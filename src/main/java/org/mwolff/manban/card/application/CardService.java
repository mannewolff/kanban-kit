package org.mwolff.manban.card.application;

import static org.mwolff.manban.card.application.KartenGrundlage.normalize;
import static org.mwolff.manban.card.application.KartenGrundlage.trimToNull;
import static org.mwolff.manban.card.application.KartenSicht.statusName;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Arbeitspaket;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Karten- und Vorhaben-Use-Cases: Anlegen (projektweite Nummer, ans Spaltenende), Bearbeiten und
 * Abhängigkeiten; Archiv und Papierkorb liegen seit Issue #1394 in {@link CardArchiveService},
 * Verschieben, Status und Umzug seit Issue #1395 in {@link CardMoveService}. Vorhaben sind Karten
 * vom Typ {@link CardType#EPIC}: sie erscheinen nicht auf dem Board, halten keine Position und
 * gruppieren Karten über {@code parentId}. Rechte über den {@link PermissionChecker}.
 */
// PMD.CouplingBetweenObjects: zentraler Karten-Use-Case-Service; die Kopplung an die Ports
// (Karten, Abhängigkeiten, Boards/Spalten, Rechte, Spaltenverlauf, Aktivität) ist fachlich
// begründet und kein God-Class-Smell. Zuständige und Labels laufen seit Issue #1051 über die
// modulinterne KartenZuordnung — sie trägt deren drei Ports, Label und die beiden Ablehnungen,
// die der Service damit nicht mehr sieht (SonarCloud S6539, Plan #1042).
// PMD.ExcessivePublicCount entfiel mit dem Kanban-kompatiblen Einliefern, das seit Issue #1392 in
// CardIngestService liegt: Die öffentliche Oberfläche liegt wieder unter der Schwelle.
// PMD.CyclomaticComplexity entfiel mit Vorhaben und Herkunft, die seit Issue #1393 in EpicService
// liegen: Die Gesamtkomplexität liegt wieder unter der Schwelle.
// PMD.TooManyMethods entfiel mit Archiv und Papierkorb, die seit Issue #1394 in CardArchiveService
// liegen: Die Zahl der Methoden liegt wieder unter der Schwelle.
@SuppressWarnings("PMD.CouplingBetweenObjects")
@Service
public class CardService {

  /**
   * Länge des Listen-Auszugs in Codepoints (Issue #771). Reicht für die einzeilige, ohnehin
   * abgeschnittene Vorschau der Listenansicht — mehr Text käme nie auf den Bildschirm.
   */
  private static final int AUSZUG_CODEPOINTS = 200;

  private final CardRepository cards;
  private final KartenAbhaengigkeiten abhaengigkeiten;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final KartenZuordnung zuordnung;
  private final CardActivityRepository activity;
  private final KartenGrundlage grundlage;
  private final KartenSicht sicht;
  private final Clock clock;

  public CardService(
      CardRepository cards,
      KartenAbhaengigkeiten abhaengigkeiten,
      BoardService boardService,
      PermissionChecker permissions,
      KartenZuordnung zuordnung,
      CardActivityRepository activity,
      KartenGrundlage grundlage,
      KartenSicht sicht,
      Clock clock) {
    this.cards = cards;
    this.abhaengigkeiten = abhaengigkeiten;
    this.boardService = boardService;
    this.permissions = permissions;
    this.zuordnung = zuordnung;
    this.activity = activity;
    this.grundlage = grundlage;
    this.sicht = sicht;
    this.clock = clock;
  }

  /**
   * Legt eine Karte an (ohne Fälligkeit/Zuständige/Labels). Delegiert an die Voll-Signatur mit
   * {@code null} für die inhaltlichen Zusatzfelder — die schlanke Überladung für alle Aufrufer, die
   * diese Felder nicht setzen.
   */
  @Transactional
  public CardView create(
      long userId,
      long boardId,
      long columnId,
      String title,
      @Nullable String description,
      @Nullable List<Integer> dependsOn,
      @Nullable Long parentId) {
    return sicht.view(
        userId,
        grundlage.anlegen(
            userId,
            boardId,
            columnId,
            title,
            description,
            dependsOn,
            parentId,
            null,
            null,
            null,
            null,
            null,
            null));
  }

  /**
   * Legt eine Karte an. {@code dueDate}, {@code assigneeIds} und {@code labelIds} werden — sofern
   * gesetzt — atomar mit der Anlage übernommen (ein einziger {@code CREATED}-Aktivitätseintrag,
   * kein Teil-Zustand); {@code null}/leer bedeutet „nicht gesetzt". Assignees/Labels durchlaufen
   * dieselbe Prüfung wie {@link #setAssignees} / {@link #setLabels} (Mitglied im Projekt, Label des
   * Boards).
   */
  @Transactional
  public CardView create(
      long userId,
      long boardId,
      long columnId,
      String title,
      @Nullable String description,
      @Nullable List<Integer> dependsOn,
      @Nullable Long parentId,
      @Nullable Instant dueDate,
      @Nullable List<Long> assigneeIds,
      @Nullable List<Long> labelIds,
      @Nullable Integer derivedFrom) {
    return sicht.view(
        userId,
        grundlage.anlegen(
            userId,
            boardId,
            columnId,
            title,
            description,
            dependsOn,
            parentId,
            dueDate,
            assigneeIds,
            labelIds,
            null,
            null,
            derivedFrom));
  }

  /**
   * Legt mehrere Karten in einem Zug am Ende einer Board-Spalte an (Issue #1200) — der
   * board-gebundene Weg des Spezifikations-Imports. Recht: {@link Permission#TICKET_CREATE}, einmal
   * für den ganzen Stapel geprüft; importieren ist fachlich dasselbe wie Karten anlegen, nur in
   * Menge.
   *
   * <p><b>Alles-oder-nichts.</b> Die Methode läuft in einer Transaktion: schlägt eine Karte fehl,
   * entsteht keine. Ein halb importierter Spec wäre schwerer aufzuräumen (welche Abschnitte
   * fehlen?) als ein wiederholter Import. Feld- und Mengengrenzen prüft bereits die Bean-Validation
   * am Endpoint, sodass ein ungültiges Element gar nicht bis hierher gelangt.
   *
   * <p><b>Ein Ereignis je Karte</b> statt eines gebündelten: Der Stapel läuft bewusst über den
   * gemeinsamen Anlegepfad {@link KartenGrundlage#anlegen}, damit Nummern-, Positions- und
   * Transitionsvergabe unverändert greifen — das Ereignis je Karte ist dessen Nebenwirkung, und der
   * Board-Strom liefert ohnehin erst nach Commit aus.
   *
   * <p>Die Karten landen in Eingabereihenfolge am Ende der Zielspalte: {@code anlegen} holt je
   * Karte eine frische aktive Position aus {@link CardRepository#allocateActivePosition(long)}.
   */
  @Transactional
  public List<CardView> createCardsBatch(
      long userId, long boardId, long columnId, List<NewCard> neueKarten) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    // Die Spalte einmal vorab gegen das Board prüfen: ein falsches Ziel soll scheitern, bevor die
    // erste Karte eine Nummer verbraucht hat. anlegen prüft sie je Karte erneut (unverändert).
    boardService.requireColumn(columnId, boardId);
    return neueKarten.stream()
        .map(
            card ->
                sicht.view(
                    userId,
                    grundlage.anlegen(
                        userId,
                        boardId,
                        columnId,
                        card.title(),
                        card.description(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .toList();
  }

  /**
   * Karten eines Boards (ohne Vorhaben) mit ihren Zusatzdaten — vier Sammelzugriffe statt vier
   * Abfragen <em>je Karte</em> (Issue #768).
   *
   * <p>Bewusst nicht über {@link KartenSicht#view(Card, boolean)}: Der baut eine einzelne Karte und
   * lädt Abhängigkeiten, Zuständige, Labels und Herkunft je Aufruf einzeln nach. Auf einer ganzen
   * Board-Liste ergibt das ein N+1 mit vier Abfragen pro Karte; hier sind es vier für die gesamte
   * Liste. Für die Einzelkarten-Pfade bleibt {@code sicht.view(...)} unverändert — dort ist die
   * Kartenzahl 1, und ein Sammelzugriff brächte nichts.
   *
   * <p>Gefiltert wird wie bisher <b>nur</b> nach {@link CardType#CARD}: Archivierte Karten bleiben
   * enthalten, die Reihenfolge ist die von {@link CardRepository#findByBoardId}.
   *
   * <p>Die Beschreibung kommt hier <b>nicht</b> mit (Issue #771): {@code description} ist immer
   * {@code null}, gesetzt ist stattdessen {@code excerpt} — die ersten {@value #AUSZUG_CODEPOINTS}
   * Codepoints. Den Volltext holt der Einzelabruf.
   */
  @Transactional(readOnly = true)
  public List<CardView> listByBoard(long userId, long boardId) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.requireMembership(userId, projectId);
    List<Card> karten =
        cards.findByBoardId(boardId).stream().filter(c -> c.type() == CardType.CARD).toList();
    Set<Long> ids = karten.stream().map(Card::requireId).collect(Collectors.toSet());
    // Karten ohne Eintrag fehlen in den Maps (Vertrag der drei findByCardIds) — die Sicht setzt
    // dort eine leere Liste, nie null.
    Map<Long, List<Integer>> abhaengigkeitenJeKarte = abhaengigkeiten.abhaengigkeitenJeKarte(ids);
    Map<Long, List<Long>> zustaendige = zuordnung.zustaendigeJeKarte(ids);
    Map<Long, List<Long>> labelIds = zuordnung.labelsJeKarte(ids);
    Map<Long, Integer> nummern = sicht.herkunftsnummern(karten);
    // Alle Karten liegen auf diesem Board: eine CARD_MOVE-Prüfung für die ganze Liste (E10).
    boolean darfStatusSetzen = sicht.darfStatusSetzen(userId, projectId);
    return karten.stream()
        .map(
            c ->
                new CardView(
                    c.requireId(),
                    c.boardId(),
                    c.columnId(),
                    c.number(),
                    c.title(),
                    // Die Board-Liste zeigt die Beschreibung nirgends ganz: Kacheln gar nicht, die
                    // Listenansicht nur einzeilig abgeschnitten. Der Volltext kommt über den
                    // Einzelabruf (Issue #771).
                    null,
                    auszug(c.description()),
                    c.positionInColumn(),
                    c.archived(),
                    c.movedToDoneAt(),
                    abhaengigkeitenJeKarte.getOrDefault(c.requireId(), List.of()),
                    c.type(),
                    c.parentId(),
                    c.shortcode(),
                    zustaendige.getOrDefault(c.requireId(), List.of()),
                    c.dueDate(),
                    labelIds.getOrDefault(c.requireId(), List.of()),
                    c.derivedFromCardId() == null ? null : nummern.get(c.derivedFromCardId()),
                    statusName(c),
                    c.status() != null && darfStatusSetzen))
        .toList();
  }

  /**
   * Vorschautext einer Karte: die ersten {@value #AUSZUG_CODEPOINTS} Codepoints der <b>rohen</b>,
   * ungestrippten Beschreibung. Roh, weil das Strippen der Markdown-Syntax im Frontend sitzt und
   * dort auch für die Sortierung gebraucht wird.
   *
   * <p>Geschnitten wird über {@link String#offsetByCodePoints(int, int)} und nicht über den
   * char-Index: Ein Emoji belegt zwei {@code char}, und ein Schnitt mitten hinein hinterließe ein
   * halbes Surrogatpaar — im Browser ein Ersatzzeichen.
   *
   * @return {@code null}, wenn keine Beschreibung gesetzt ist
   */
  private static @Nullable String auszug(@Nullable String beschreibung) {
    if (beschreibung == null) {
      return null;
    }
    int codepoints =
        Math.min(beschreibung.codePointCount(0, beschreibung.length()), AUSZUG_CODEPOINTS);
    return beschreibung.substring(0, beschreibung.offsetByCodePoints(0, codepoints));
  }

  /**
   * Projekt-ID der Karte — die Auflösung, die modulfremde Rechteprüfungen (Anhänge, Kommentare)
   * brauchen, ohne das Kartenaggregat oder dessen Port zu kennen. Projekt-basiert über {@code
   * card.projectId()} (immer gesetzt, V18) statt über das Board.
   *
   * @throws CardNotFoundException wenn die Karte nicht existiert
   */
  @Transactional(readOnly = true)
  public long requireProjectId(long cardId) {
    return cards.findById(cardId).orElseThrow(CardNotFoundException::new).projectId();
  }

  /**
   * Einzelne Karte für Stellen, die nur eine Karten-ID kennen (Dashboard-Ausreißer, #515) — ohne
   * den Umweg über die komplette Board-Kartenliste. Leserecht wie bei den übrigen Lesepfaden:
   * Projekt-Mitgliedschaft über die Projekt-ID der Karte; Nichtmitglied und unbekannte Karte sind
   * nicht unterscheidbar (beide 404, kein Existenz-Leak).
   *
   * @throws CardNotFoundException wenn die Karte nicht existiert
   */
  @Transactional(readOnly = true)
  public CardView getCard(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.requireMembership(userId, card.projectId());
    return sicht.view(userId, card);
  }

  /**
   * Zu jeder genannten Kartennummer des Projekts die Vorhaben, zu denen ihre Karte gehört (Issue
   * #936, Plan #933 E10).
   *
   * <p>Die Zugehörigkeit ist die aus {@link EpicMembership} — {@code parentId} plus Herkunftskette
   * — und kein eigener Begriff. Gerechnet wird je Board, wie überall, wo {@code EpicMembership}
   * gilt, und über alle Boards des Projekts vereinigt: Kartennummern sind projektweit eindeutig,
   * und der Aufrufer kennt nur Nummern.
   *
   * <p><b>Eine Karte kann zu mehreren Vorhaben gehören</b> (Plan #631, E10) und steht dann mit
   * mehreren {@link EpicRef} im Ergebnis. Wer über Vorhaben summiert, zählt ihre Werte deshalb
   * mehrfach — die Summen der Vorhaben dürfen sich überschneiden und ergeben zusammen nicht die
   * Gesamtsumme.
   *
   * <p>Eine Nummer ohne Karte oder ohne Vorhaben fehlt im Ergebnis; die Methode wirft dafür nicht.
   * Eine leere Nummernmenge fragt die Datenbank nicht.
   *
   * <p>Ohne Rechteprüfung wie {@link #requireProjectId}: Die Methode ist Vertrag für fremde Module,
   * die ihre eigene Prüfung bereits vorgenommen haben — sie liefert keine Karteninhalte, nur die
   * Zuordnung zu den Nummern, die der Aufrufer schon kennt.
   *
   * @return je Kartennummer die Menge ihrer Vorhaben; Nummern ohne Vorhaben fehlen
   */
  @Transactional(readOnly = true)
  public Map<Integer, Set<EpicRef>> epicsByCardNumber(
      long projectId, Collection<Integer> cardNumbers) {
    if (cardNumbers.isEmpty()) {
      return Map.of();
    }
    Set<Integer> gesucht = Set.copyOf(cardNumbers);
    Map<Long, List<Card>> jeBoard =
        cards.findByProjectId(projectId).stream().collect(Collectors.groupingBy(Card::boardId));

    Map<Integer, Set<EpicRef>> ergebnis = new HashMap<>();
    for (List<Card> boardKarten : jeBoard.values()) {
      Map<Long, Card> nachId =
          boardKarten.stream().collect(Collectors.toMap(Card::requireId, Function.identity()));
      EpicMembership.compute(boardKarten)
          .forEach(
              (epicId, mitglieder) -> {
                // Die Schluessel von compute sind die Vorhaben genau dieser Kartenmenge.
                Card epic = Objects.requireNonNull(nachId.get(epicId));
                EpicRef ref = new EpicRef(epicId, epic.shortcode(), epic.title());
                mitglieder.stream()
                    .map(Card::number)
                    .filter(gesucht::contains)
                    .forEach(n -> ergebnis.computeIfAbsent(n, k -> new HashSet<>()).add(ref));
              });
    }
    return ergebnis.entrySet().stream()
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
  }

  /**
   * Die Teilmenge der genannten Kartennummern, zu denen es im Projekt eine Karte gibt (Issue
   * #1169).
   *
   * <p>Eine schmale Abfrage: Sie beantwortet nur ja/nein zu Nummern, die der Aufrufer schon kennt,
   * und liefert <b>keine Karteninhalte</b>. Gedacht für Aufrufer, die zu vielen Nummern zugleich
   * wissen müssen, ob der Zugriff darauf eine Karte fände — ein Abruf je Nummer wäre dort eine
   * Anfragelawine.
   *
   * <p>Sichtbarkeit wie bei {@code findByProjectIdAndNumber}: archivierte Karten zählen mit,
   * Papierkorb-Karten nicht. Unbekannte Nummern fehlen im Ergebnis; die Methode wirft dafür nicht.
   * Eine leere Nummernmenge fragt die Datenbank nicht.
   *
   * <p>Ohne Rechteprüfung wie {@link #epicsByCardNumber}: Die Methode ist Vertrag für fremde
   * Module, die ihre eigene Prüfung bereits vorgenommen haben — sie liefert keine Karteninhalte,
   * nur die Existenz zu den Nummern, die der Aufrufer schon kennt.
   *
   * @return die vorhandenen unter den gefragten Nummern
   */
  @Transactional(readOnly = true)
  public Set<Integer> existingCardNumbers(long projectId, Collection<Integer> cardNumbers) {
    if (cardNumbers.isEmpty()) {
      return Set.of();
    }
    return cards.findExistingNumbers(projectId, Set.copyOf(cardNumbers));
  }

  /**
   * Die Kartenaktivitäten eines Nachtlaufs im Zeitfenster (Issue #1373, Plan #1372 E2): Herkunft
   * {@code TOKEN} mit diesem Token-Namen, gesetztes {@code agent}, Zeitpunkt in {@code [von, bis]}.
   * Chronologisch; je Eintrag nur Karte, Art und Zeitpunkt.
   *
   * <p>Ohne Rechteprüfung wie {@link #existingCardNumbers}: Vertrag für das Modul {@code nightrun},
   * das die Projekt-Rolle vor dem Aufruf selbst prüft.
   */
  @Transactional(readOnly = true)
  public List<TokenActivityView> tokenActivitiesInWindow(
      long projectId, String tokenName, Instant von, Instant bis) {
    return activity.findTokenActivitiesInWindow(projectId, tokenName, von, bis).stream()
        .map(a -> new TokenActivityView(a.cardId(), a.type().name(), a.createdAt()))
        .toList();
  }

  /**
   * Die Karten zu den genannten IDs mit allem, was die Ermittlung des Lauf-Fortschritts braucht
   * (Issue #1373): Nummer, Titel, Board, Status, Labelnamen, Herkunft, Typ und Beschreibung — dazu,
   * ob die Karte ein Arbeitspaket ist, damit der Aufrufer {@code card.domain} nicht kennen muss.
   *
   * <p>Unbekannte IDs fehlen im Ergebnis; die Reihenfolge folgt der Eingabe. Gelesen wird wie bei
   * {@link CardRepository#findByIds}, also auch archivierte Karten und Karten im Papierkorb. Ohne
   * Rechteprüfung wie {@link #tokenActivitiesInWindow}; eine leere Eingabe fragt keinen Port.
   */
  @Transactional(readOnly = true)
  public List<LaufKarteView> cardsByIds(Collection<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<Long, Card> jeId =
        cards.findByIds(Set.copyOf(ids)).stream()
            .collect(Collectors.toMap(Card::requireId, Function.identity()));
    List<Card> gefunden = ids.stream().map(jeId::get).filter(Objects::nonNull).toList();
    Map<Long, Long> boardJeKarte = new LinkedHashMap<>();
    gefunden.forEach(c -> boardJeKarte.put(c.requireId(), c.boardId()));
    Map<Long, List<String>> labelNamen = zuordnung.labelNamenJeKarte(boardJeKarte);
    return gefunden.stream()
        .map(
            c -> {
              CardStatus status = c.status();
              return new LaufKarteView(
                  c.requireId(),
                  c.number(),
                  c.title(),
                  c.boardId(),
                  status == null ? null : status.name(),
                  labelNamen.getOrDefault(c.requireId(), List.of()),
                  c.derivedFromCardId(),
                  c.type().name(),
                  Arbeitspaket.istArbeitspaket(c.type(), c.title()),
                  c.description());
            })
        .toList();
  }

  @Transactional
  public CardView update(
      long userId,
      long cardId,
      String title,
      @Nullable String description,
      @Nullable List<Integer> dependsOn,
      @Nullable String shortcode,
      @Nullable Long parentId,
      @Nullable Instant dueDate) {
    Card card =
        grundlage.requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    Card updated = card.withContent(title.trim(), normalize(description));
    if (card.type() == CardType.EPIC) {
      // Vorhaben tragen ein Kürzel, aber keinen Parent.
      updated = updated.withShortcode(trimToNull(shortcode));
    } else {
      // Karten: Vorhaben-Zuordnung im selben PUT setzen/lösen (parentId == null -> lösen).
      Long effectiveParent =
          parentId == null
              ? null
              : grundlage.requireEpicInBoard(parentId, card.boardId()).requireId();
      updated = updated.withParent(effectiveParent).withDueDate(dueDate);
    }
    Card saved = cards.save(grundlage.folgeArtwechsel(card, updated));
    grundlage.aktivitaet(
        cardId, userId, CardActivityType.UPDATED, "Karte bearbeitet", clock.instant());
    if (dependsOn != null) {
      abhaengigkeiten.ersetze(saved, dependsOn);
    }
    grundlage.publishChanged(saved.boardId(), ActivityType.UPDATED, cardId);
    return sicht.view(userId, saved);
  }

  /**
   * Ersetzt die Zuständigen einer Karte. Nur Karten (keine Vorhaben); zugewiesen werden dürfen
   * ausschließlich Mitglieder des Projekts. Recht: {@link Permission#TICKET_UPDATE} (Member und
   * aufwärts).
   */
  @Transactional
  public CardView setAssignees(long userId, long cardId, List<Long> assigneeIds) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten haben Zuständige");
    }
    // Projekt-basierte Rechte (#405), nicht über das Board.
    permissions.require(userId, card.projectId(), Permission.TICKET_UPDATE);

    zuordnung.ersetzeZustaendige(cardId, card.projectId(), assigneeIds);
    grundlage.aktivitaet(
        cardId, userId, CardActivityType.ASSIGNED, "Zuständige geändert", clock.instant());
    grundlage.publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
    return sicht.view(userId, card);
  }

  /**
   * Ersetzt die Labels einer Karte. Nur Karten (keine Vorhaben); zugeordnet werden dürfen nur
   * Labels desselben Boards. Recht: {@link Permission#TICKET_UPDATE} (Member und aufwärts).
   */
  @Transactional
  public CardView setLabels(long userId, long cardId, List<Long> labelIds) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten haben Labels");
    }
    // Projekt-basierte Rechte (#405); die Labels selbst bleiben board-scoped.
    permissions.require(userId, card.projectId(), Permission.TICKET_UPDATE);

    zuordnung.ersetzeLabels(cardId, card.boardId(), labelIds);
    grundlage.publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
    return sicht.view(userId, card);
  }

  /**
   * Setzt <b>ein</b> Label an mehreren Karten oder nimmt es ihnen ab — in einer Transaktion
   * (alles-oder-nichts). Je Karte gelten dieselben Prüfungen wie bei {@link #setLabels}; scheitert
   * eine, rollt der ganze Batch zurück.
   *
   * <p><b>Warum hinzufügen/abnehmen statt ersetzen:</b> Die Auswahl trägt in aller Regel
   * unterschiedliche Labels. Eine ersetzende Massenaktion löschte die übrigen still mit — der
   * Nutzer sähe nur das gesetzte Label und nicht, was dafür verschwunden ist (Issue #994).
   *
   * <p>Eine Karte, die das Label schon trägt (bzw. schon nicht trägt), bleibt unverändert und ist
   * kein Fehler: Die Massenaktion beschreibt einen Zielzustand, keinen Umschalter je Karte.
   */
  @Transactional
  public List<CardView> bulkLabels(
      long userId, List<Long> cardIds, long labelId, LabelAction action) {
    return cardIds.stream().map(cardId -> doLabel(userId, cardId, labelId, action)).toList();
  }

  private CardView doLabel(long userId, long cardId, long labelId, LabelAction action) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten haben Labels");
    }
    permissions.require(userId, card.projectId(), Permission.TICKET_UPDATE);

    long boardId = card.boardId();
    zuordnung.aendereLabel(cardId, boardId, labelId, action);
    grundlage.publishChanged(boardId, ActivityType.UPDATED, cardId);
    return sicht.view(userId, card);
  }

  /**
   * Eine anzulegende Board-Karte im Stapel (Issue #1200): Titel und optionale Beschreibung. Board
   * und Spalte stehen bewusst nicht hier, sondern gelten für den ganzen Stapel — er füllt genau
   * eine Spalte.
   */
  public record NewCard(String title, @Nullable String description) {}

  /**
   * Aktivitätsverlauf einer Karte (chronologisch). Erfordert Projekt-Mitgliedschaft (Leserecht),
   * geprüft über {@code card.projectId()} (#405) und nicht über das Board.
   */
  @Transactional(readOnly = true)
  public List<CardActivity> listActivity(long userId, long cardId) {
    return doListActivity(userId, cardId);
  }

  /**
   * Der Kern ohne Annotation, damit ihn {@link #listActivityViews(long, long)} rufen kann, ohne
   * über {@code this} an einer {@code @Transactional}-Methode vorbeizugehen (Sonar java:S6809) —
   * dasselbe Muster wie {@code CardArchiveService.doArchive} und {@code EpicService.doCreateEpic}.
   */
  private List<CardActivity> doListActivity(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.requireMembership(userId, card.projectId());
    return activity.findByCardId(cardId);
  }

  /**
   * Derselbe Verlauf wie {@link #listActivity(long, long)}, als Fassaden-Sicht ohne Typen aus
   * {@code card.domain} — der Weg, auf dem fremde Module (heute {@code kanbancompat}, #876) den
   * Verlauf lesen. Rechte und Reihenfolge sind unverändert die von {@code listActivity}.
   */
  @Transactional(readOnly = true)
  public List<ActivityView> listActivityViews(long userId, long cardId) {
    return doListActivity(userId, cardId).stream().map(CardService::activityView).toList();
  }

  private static ActivityView activityView(CardActivity a) {
    return new ActivityView(
        a.id(),
        a.actorUserId(),
        a.type().name(),
        a.detail(),
        a.createdAt(),
        a.origin() == null ? null : a.origin().name(),
        a.tokenName(),
        a.agent());
  }

  /**
   * Ein Eintrag des Aktivitätsverlaufs als Fassaden-Sicht: {@code type} und {@code origin} sind die
   * Namen der zugehörigen Aufzählungen, damit die Sicht keinen Typ aus {@code card.domain} nach
   * außen gibt.
   *
   * <p>{@code origin} und {@code tokenName} sind serverseitig verifiziert, {@code agent} ist eine
   * Selbstauskunft des Clients — siehe Issue #517. Bei Alt-Einträgen aus der Zeit vor Migration V23
   * sind alle drei Felder {@code null}.
   */
  public record ActivityView(
      @Nullable Long id,
      @Nullable Long actorUserId,
      String type,
      String detail,
      Instant createdAt,
      @Nullable String origin,
      @Nullable String tokenName,
      @Nullable String agent) {}

  /**
   * Eine Kartenaktivität eines Nachtlaufs als Fassaden-Sicht (Issue #1373).
   *
   * @param cardId Karte, an der die Aktivität stattfand
   * @param type Konstantenname von {@code CardActivityType}, etwa {@code CREATED} oder {@code
   *     MOVED}
   * @param createdAt Zeitpunkt der Aktivität
   */
  public record TokenActivityView(long cardId, String type, Instant createdAt) {}

  /**
   * Eine Karte, wie die Ermittlung des Lauf-Fortschritts sie braucht (Issue #1373).
   *
   * @param status Konstantenname von {@code CardStatus}; {@code null} bei Vorhaben und
   *     Dokumentarten
   * @param labels Labelnamen der Karte, alphabetisch
   * @param derivedFromCardId ID der Karte, aus der diese entstanden ist; {@code null} ohne Herkunft
   * @param type Konstantenname von {@code CardType}
   * @param arbeitspaket ob die Karte ein Arbeitspaket ist ({@code Arbeitspaket.istArbeitspaket})
   * @param description Markdown-Beschreibung (trägt etwa die Zeile {@code Plan-Review:})
   */
  public record LaufKarteView(
      long id,
      int number,
      String title,
      long boardId,
      @Nullable String status,
      List<String> labels,
      @Nullable Long derivedFromCardId,
      String type,
      boolean arbeitspaket,
      @Nullable String description) {}
}
