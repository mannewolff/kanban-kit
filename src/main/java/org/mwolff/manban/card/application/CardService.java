package org.mwolff.manban.card.application;

import static org.mwolff.manban.card.application.KartenGrundlage.doneStempel;
import static org.mwolff.manban.card.application.KartenGrundlage.inSpalte;
import static org.mwolff.manban.card.application.KartenGrundlage.normalize;
import static org.mwolff.manban.card.application.KartenGrundlage.statusIn;
import static org.mwolff.manban.card.application.KartenGrundlage.trimToNull;
import static org.mwolff.manban.card.application.KartenSicht.statusName;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
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
 * Karten- und Vorhaben-Use-Cases: Anlegen (projektweite Nummer, ans Spaltenende), Bearbeiten,
 * Move/Reindex und Abhängigkeiten; Archiv und Papierkorb liegen seit Issue #1394 in {@link
 * CardArchiveService}. Vorhaben sind Karten vom Typ {@link CardType#EPIC}: sie erscheinen nicht auf
 * dem Board, halten keine Position und gruppieren Karten über {@code parentId}. Rechte über den
 * {@link PermissionChecker}.
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

  /**
   * Zielposition „ans Ende der Spalte" für {@link #doMove(long, long, long, int)}. {@link
   * CardRepository#move(long, long, int)} begrenzt die Zielposition auf das aktive Band der
   * Zielspalte — ein Wert oberhalb jeder erreichbaren Spaltenlänge landet damit hinter der letzten
   * Karte, ohne die Spalte vorher zählen zu müssen. Ein zusätzlicher Zählzugriff wäre nicht nur
   * überflüssig: Er nähme eine zweite Spaltensperre und verstieße damit gegen die Regel „eine
   * Transaktion nimmt ihre Spaltensperren in einem Aufruf" ({@link
   * CardRepository#lockColumnPositions(List)}).
   */
  private static final int POSITION_AM_ENDE = Integer.MAX_VALUE;

  private final CardRepository cards;
  private final KartenAbhaengigkeiten abhaengigkeiten;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final CardColumnTransitionRepository transitions;
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
      CardColumnTransitionRepository transitions,
      KartenZuordnung zuordnung,
      CardActivityRepository activity,
      KartenGrundlage grundlage,
      KartenSicht sicht,
      Clock clock) {
    this.cards = cards;
    this.abhaengigkeiten = abhaengigkeiten;
    this.boardService = boardService;
    this.permissions = permissions;
    this.transitions = transitions;
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

  @Transactional
  public CardView move(long userId, long cardId, long targetColumnId, int targetPosition) {
    return doMove(userId, cardId, targetColumnId, targetPosition);
  }

  private CardView doMove(long userId, long cardId, long targetColumnId, int targetPosition) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() == CardType.EPIC) {
      throw new InvalidDependencyException("Epics werden nicht auf dem Board positioniert");
    }
    permissions.require(
        userId, boardService.requireProjectId(card.boardId()), Permission.CARD_MOVE);

    ColumnView target = boardService.requireColumn(targetColumnId, card.boardId());

    cards.move(cardId, targetColumnId, targetPosition);

    // Spaltenverlauf: nur bei echtem Spaltenwechsel (kein Eintrag bei reinem Reindex). Ein einziger
    // Zeitstempel schließt die verlassene und eröffnet die Ziel-Spalte lückenlos.
    long fromColumn = card.columnId();
    if (fromColumn != targetColumnId) {
      Instant switchedAt = clock.instant();
      transitions.closeOpen(cardId, switchedAt);
      transitions.open(cardId, targetColumnId, target.name(), switchedAt);
      grundlage.aktivitaet(
          cardId, userId, CardActivityType.MOVED, "Verschoben nach " + target.name(), switchedAt);
    }

    // Status nur bei echtem Spaltenwechsel (Plan #1294): Umsortieren ändert ihn nicht. Der
    // Done-Zeitstempel folgt dem effektiven Maßstab — bei Arbeitspaketen dem Status (E7).
    Card moved = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (fromColumn != targetColumnId) {
      moved = moved.withStatus(statusIn(moved, target.name()));
    }
    // CARD_MOVE ist oben geprüft, und die Karte bleibt im Projekt — die Sicht braucht keine zweite
    // Prüfung.
    CardView result =
        sicht.view(
            cards.save(moved.withMovedToDoneAt(doneStempel(moved, target.name(), clock.instant()))),
            true);
    grundlage.publishChanged(card.boardId(), ActivityType.MOVED, cardId);
    return result;
  }

  /**
   * Setzt den eigenen Status eines Arbeitspakets (Plan #1294, E9) und legt es in die Prozessspalte
   * dieses Status (Korrektur von #787 am 2026-10-01, Issue #1326). Recht wie beim Verschieben:
   * {@link Permission#CARD_MOVE}, geprüft vor jeder Auswertung der Eingabe.
   *
   * <p>Hat das Board eine Spalte, deren Name nach {@link Arbeitspaket#statusVonSpalte} den neuen
   * Status ergibt, und liegt die Karte nicht schon darin, wandert sie über {@code doMove} ans Ende
   * der ersten solchen Spalte in Board-Reihenfolge; Status, Done-Zeitstempel, Aufenthalt und
   * Verlauf folgen dort aus der Zielspalte. Sonst — keine passende Spalte, oder die Karte liegt
   * schon darin — wird nur der Status gesetzt, Spalte und Position bleiben.
   *
   * <p>Nebenwirkungen eines reinen Statuswechsels, alle mit demselben Zeitstempel:
   *
   * <ul>
   *   <li>der Done-Zeitstempel wird aus dem neuen Status abgeleitet (E7);
   *   <li>der Aufenthaltsverlauf schließt den offenen Aufenthalt und öffnet einen neuen in der
   *       tatsächlichen Spalte, benannt mit dem kanonischen Prozessnamen (E25) — sonst hätte ein
   *       Paket, das in einer eigenen Spalte per Status durch In progress läuft, keine
   *       Implementierungszeit;
   *   <li>ein Verlaufseintrag {@code STATUS_CHANGED} „Status auf &lt;Prozessname&gt;" (E14);
   *   <li>ein {@code UPDATED}-Ereignis für offene Boards.
   * </ul>
   *
   * <p>Der bisherige Status noch einmal gesetzt ist kein Wechsel und hinterlässt keine Spur.
   *
   * @param status Konstantenname von {@code CardStatus} (E24) — ein Anzeigename gilt nicht
   * @throws CardNotFoundException wenn die Karte nicht existiert
   * @throws InvalidStatusException bei unbekanntem Wert oder einer Karte ohne eigenen Status
   *     (Vorhaben, Dokumentart)
   */
  @Transactional
  public void setStatus(long userId, long cardId, String status) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.require(userId, card.projectId(), Permission.CARD_MOVE);
    CardStatus neu = statusAus(status);
    if (!Arbeitspaket.istArbeitspaket(card.type(), card.title())) {
      throw new InvalidStatusException("Diese Karte trägt keinen eigenen Status");
    }
    if (neu == card.status()) {
      return;
    }
    Optional<ColumnView> prozessspalte =
        boardService.listColumns(card.boardId()).stream()
            .filter(
                spalte ->
                    Arbeitspaket.statusVonSpalte(spalte.name()).filter(neu::equals).isPresent())
            .findFirst();
    if (prozessspalte.isPresent() && prozessspalte.get().id().longValue() != card.columnId()) {
      doMove(userId, cardId, prozessspalte.get().id().longValue(), POSITION_AM_ENDE);
      return;
    }
    Instant jetzt = clock.instant();
    Card gesetzt = card.withStatus(neu);
    // Bei einem Arbeitspaket entscheidet allein der Status über Done — die Spalte geht nicht ein.
    cards.save(gesetzt.withMovedToDoneAt(doneStempel(gesetzt, null, jetzt)));
    transitions.closeOpen(cardId, jetzt);
    transitions.open(cardId, card.columnId(), neu.anzeigename(), jetzt);
    grundlage.aktivitaet(
        cardId, userId, CardActivityType.STATUS_CHANGED, "Status auf " + neu.anzeigename(), jetzt);
    grundlage.publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
  }

  /**
   * Der Status zu seinem Konstantennamen. Die Meldung wiederholt die Eingabe bewusst nicht — sie
   * stammt vom Client.
   */
  private static CardStatus statusAus(String status) {
    try {
      return CardStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
      throw new InvalidStatusException("Unbekannter Status", e);
    }
  }

  /**
   * Ordnet die aktiven Karten einer Spalte nach ihrer Kartennummer — {@link SortDirection#ASC}
   * kleinste zuerst, {@link SortDirection#DESC} größte zuerst. Gedacht für Spalten, in die mehrere
   * Karten am Stück gezogen wurden und die deshalb ungeordnet dastehen.
   *
   * <p>Fachlich ist das ein <em>Massen-Verschieben innerhalb</em> der Spalte und keine
   * Strukturänderung am Board, deshalb genügt {@link Permission#CARD_MOVE} — dasselbe Recht wie für
   * das Verschieben einer einzelnen Karte. Karten außerhalb des aktiven Positions-Namespace
   * (archiviert, Papierkorb) und Vorhaben bleiben unberührt; Details am Port {@link
   * CardRepository#sortActiveByNumber(long, SortDirection)}.
   *
   * <p>Bewusst ohne {@link CardActivity}-Eintrag: Die Umsortierung ändert nur die Anordnung
   * innerhalb der Spalte, keine Karte wechselt Spalte oder Zustand — ein Audit-Eintrag pro
   * betroffener Karte würde den Aktivitätsverlauf fluten, ohne eine fachliche Änderung zu
   * dokumentieren. Offene Boards erfahren von der neuen Anordnung über das SSE-Event.
   */
  @Transactional
  public void sortColumnByNumber(long userId, long columnId, SortDirection direction) {
    long boardId = boardService.boardIdOfColumn(columnId);
    permissions.require(userId, boardService.requireProjectId(boardId), Permission.CARD_MOVE);

    cards.sortActiveByNumber(columnId, direction);

    // Ohne Karten-Bezug: betroffen ist die ganze Spalte, offene Boards laden über SSE neu.
    grundlage.publishChanged(boardId, ActivityType.MOVED, null);
  }

  /**
   * Verschiebt eine Karte in eine Spalte eines Boards; die Karte landet am Ende der Zielspalte.
   *
   * <p><b>Dasselbe Board</b> (Issue #1043): kein Umzug, sondern ein Spaltenwechsel — die Karte
   * behält Nummer, Vorhaben-Zuordnung, Abhängigkeiten und Zuständige, bekommt einen Verlaufseintrag
   * {@code MOVED} und ihren Done-Zeitpunkt nach denselben Regeln wie {@link #move(long, long, long,
   * int)}, an das dieser Fall delegiert. Liegt die Karte bereits in der Zielspalte, bleibt sie an
   * ihrem Platz, ohne Eintrag und ohne Fehler.
   *
   * <p><b>Anderes Board</b> — Rechte und Nebenwirkungen sind richtungsabhängig:
   *
   * <ul>
   *   <li><b>Selbes Projekt:</b> es genügt {@link Permission#CARD_MOVE} — dasselbe Recht wie für
   *       das Verschieben innerhalb eines Boards. Die Nummer bleibt erhalten (projektweit ohnehin
   *       eindeutig), Abhängigkeiten und Zuständige wandern mit — so brechen Querverweise beim
   *       Board-Wechsel nicht.
   *   <li><b>Anderes Projekt:</b> der Benutzer muss im Quell- <em>und</em> im Zielprojekt OWNER
   *       (oder Plattform-Admin) sein. Die Karte erhält eine neue projekt-scoped Nummer;
   *       Abhängigkeiten und Zuständige (projekt-lokal) werden entfernt.
   * </ul>
   *
   * <p>Die board-lokale Vorhaben-Zuordnung wird beim Board-Wechsel in beiden Fällen entfernt (das
   * Ziel-Board hat eigene Vorhaben). Kommentare und Anhänge wandern immer mit (an der Karten-ID).
   */
  @Transactional
  public CardView transfer(long userId, long cardId, long targetBoardId, long targetColumnId) {
    return doTransfer(userId, cardId, targetBoardId, targetColumnId);
  }

  private CardView doTransfer(long userId, long cardId, long targetBoardId, long targetColumnId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() == CardType.EPIC) {
      throw new InvalidDependencyException("Epics können nicht verschoben werden");
    }
    // Zielboard == Board der Karte: Das ist kein Umzug, sondern ein Spaltenwechsel, und dessen
    // Regeln stehen vollständig in doMove — Spaltenverlauf nur bei echtem Wechsel, Verlaufseintrag
    // MOVED, Status nur bei echtem Wechsel, Vorhaben-Zuordnung bleibt. Der Umzugspfad unten ist
    // auf den Board-Wechsel gebaut: Er leert parentId (das Ziel-Board hat eigene Vorhaben) und
    // schreibt Spaltenverlauf und Status auch dann fort, wenn die Spalte dieselbe bleibt. Auf dem
    // eigenen Board wäre jede dieser Wirkungen falsch.
    if (targetBoardId == card.boardId()) {
      return doMove(userId, cardId, targetColumnId, POSITION_AM_ENDE);
    }
    long sourceProjectId = boardService.requireProjectId(card.boardId());
    long targetProjectId = boardService.requireProjectId(targetBoardId);
    ColumnView targetColumn = boardService.requireColumn(targetColumnId, targetBoardId);

    boolean sameProject = sourceProjectId == targetProjectId;
    // Projektintern ist der Board-Wechsel nur ein Verschieben und verlangt daher CARD_MOVE. Über
    // Projektgrenzen bleibt es bei der strengen Eigentümer-Prüfung in beiden Projekten.
    if (sameProject) {
      permissions.require(userId, sourceProjectId, Permission.CARD_MOVE);
    } else {
      permissions.requireOwner(userId, sourceProjectId);
      permissions.requireOwner(userId, targetProjectId);
    }

    // Innerhalb desselben Projekts bleibt die Nummer erhalten (projektweit ohnehin eindeutig) und
    // Abhängigkeiten/Zuständige wandern mit — nur so bleiben Querverweise beim Board-Wechsel
    // stabil.
    // Nur über Projektgrenzen wird neu nummeriert und werden die projekt-lokalen Verknüpfungen
    // (Abhängigkeiten, Zuständige) entfernt.
    int newNumber = sameProject ? card.number() : cards.allocateCardNumber(targetProjectId);
    cards.transfer(cardId, targetBoardId, targetColumnId, newNumber);
    if (!sameProject) {
      abhaengigkeiten.entferne(cardId);
      zuordnung.entferneZustaendige(cardId);
    }

    // Spaltenverlauf: der board-/spaltenübergreifende Umzug zählt als Spaltenwechsel.
    Instant switchedAt = clock.instant();
    transitions.closeOpen(cardId, switchedAt);
    transitions.open(cardId, targetColumnId, targetColumn.name(), switchedAt);

    Card moved = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    // Status wie bei doMove aus der Zielspalte; der Done-Zeitstempel wird daraus neu abgeleitet
    // statt gelöscht — sonst fiele ein Paket mit Status DONE aus jeder Auswertung (Plan E18).
    Card cleaned = inSpalte(moved.withParent(null), targetColumn.name(), switchedAt);
    if (!sameProject) {
      // Die Herkunft ist projekt-lokal: Der Vorfahr bleibt zurueck, und ein Verweis ueber die
      // Projektgrenze zeigte auf eine Nummer, die dort einer anderen Karte gehoeren kann.
      // Anders als withParent(null) gilt das NUR beim Projektwechsel — innerhalb des Projekts
      // ueberlebt die Kette den Board-Wechsel.
      cleaned = cleaned.withDerivedFrom(null);
      // Dieselbe Begruendung fuer die Anforderung: Sie ist board- und damit projekt-lokal.
      cleaned = cleaned.withRequirement(null);
    }
    CardView result = sicht.view(userId, cards.save(cleaned));
    if (!sameProject) {
      // Gegenrichtung: Auch die Kinder verlieren ihren Verweis. Sonst zeigten sie auf die NEUE
      // Nummer der abgewanderten Karte — im eigenen Projekt womoeglich eine fremde. Bewusst ohne
      // Ausnahme fuer bulkTransfer: Wandern Vorfahr und Kind im selben Batch, haenge das Ergebnis
      // sonst von der Reihenfolge innerhalb des Batches ab.
      for (Card kind : cards.findByDerivedFrom(cardId)) {
        cards.save(kind.withDerivedFrom(null));
      }
      // Gegenrichtung der Anforderung: Wandert die Anforderungskarte ab, verliert das
      // zurueckbleibende Vorhaben seinen Verweis — sonst zeigte er ueber die Projektgrenze.
      for (Card vorhaben : cards.findByRequirementCard(cardId)) {
        cards.save(vorhaben.withRequirement(null));
      }
    }
    // Board-übergreifend: Quell- und Ziel-Board müssen beide live nachziehen.
    grundlage.publishChanged(card.boardId(), ActivityType.MOVED, cardId);
    grundlage.publishChanged(targetBoardId, ActivityType.MOVED, cardId);
    return result;
  }

  /**
   * Verschiebt mehrere Karten in einer Transaktion auf dasselbe Zielboard und dieselbe Zielspalte
   * (alles-oder-nichts). Nutzt je Karte die Einzel-Logik von {@link #transfer(long, long, long,
   * long)} inklusive der richtungsabhängigen Rechteprüfung ({@link Permission#CARD_MOVE} innerhalb
   * des Projekts, OWNER in Quell- und Zielprojekt darüber hinaus) sowie Vorhaben-Ausschluss;
   * scheitert eine Karte, rollt der gesamte Batch zurück. Die Karten landen in Eingabereihenfolge
   * am Ende der Zielspalte, jede Quellspalte wird dabei lückenlos nachgezogen. Das gilt auch, wenn
   * das Zielboard das Board der Karten ist — dann ist der Sammel-Umzug ein Sammel-Spaltenwechsel
   * (Issue #1043), und Karten, die schon in der Zielspalte liegen, bleiben unberührt.
   *
   * <p>Die Spaltensperren nimmt der Batch <strong>vorab in einem Zug</strong> (Issue #499): Nähme
   * jeder Einzel-Umzug seine beiden Sperren für sich, könnten zwei gleichzeitige Sammel-Umzüge mit
   * überlappenden Quellspalten dieselben Spalten in unterschiedlicher Reihenfolge greifen und
   * verklemmen. Ein sortierter Aufruf über die Vereinigung schließt das aus; die Sperren der
   * Einzel-Umzüge sind danach wirkungslose Wiederholungen.
   *
   * <p>Enthält der Batch eine Karte aus einem <em>anderen</em> Projekt, wird zuvor der
   * Nummern-Namespace des Zielprojekts gesperrt: Nur so bleibt die Ordnung „Projekt vor Spalte"
   * gewahrt, die jeder Einzel-Umzug einhält. Innerhalb eines Projekts entfällt diese Sperre — dort
   * wird keine Nummer neu vergeben, und ein Sammel-Umzug soll die Karten-Anlage im selben Projekt
   * nicht für die Dauer des Batches ausbremsen.
   */
  @Transactional
  public List<CardView> bulkTransfer(
      long userId, List<Long> cardIds, long targetBoardId, long targetColumnId) {
    long targetProjectId = boardService.requireProjectId(targetBoardId);
    List<Card> batch = cardIds.stream().map(cards::findById).flatMap(Optional::stream).toList();
    if (batch.stream().anyMatch(card -> !Objects.equals(card.projectId(), targetProjectId))) {
      cards.lockCardNumbers(targetProjectId);
    }
    List<Long> affectedColumns = new ArrayList<>();
    affectedColumns.add(targetColumnId);
    batch.forEach(card -> Optional.ofNullable(card.columnId()).ifPresent(affectedColumns::add));
    cards.lockColumnPositions(affectedColumns);
    return cardIds.stream()
        .map(cardId -> doTransfer(userId, cardId, targetBoardId, targetColumnId))
        .toList();
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
