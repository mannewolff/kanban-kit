package org.mwolff.manban.card.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardSummary;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Karten- und Vorhaben-Use-Cases: Anlegen (projektweite Nummer, ans Spaltenende), Bearbeiten,
 * Archivieren/Wiederherstellen, Löschen, Move/Reindex und Abhängigkeiten. Vorhaben sind Karten vom
 * Typ {@link CardType#EPIC}: sie erscheinen nicht auf dem Board, halten keine Position und
 * gruppieren Karten über {@code parentId}. Rechte über den {@link PermissionChecker}.
 */
// PMD.CouplingBetweenObjects: zentraler Karten-Use-Case-Service; die Kopplung an die Ports
// (Karten, Abhängigkeiten, Boards/Spalten, Rechte, Spaltenverlauf, Aktivität) ist fachlich
// begründet und kein God-Class-Smell. Zuständige und Labels laufen seit Issue #1051 über die
// modulinterne KartenZuordnung — sie trägt deren drei Ports, Label und die beiden Ablehnungen,
// die der Service damit nicht mehr sieht (SonarCloud S6539, Plan #1042).
// PMD.CyclomaticComplexity: die Klassen-Gesamtkomplexität summiert viele kleine, je für sich
// einfache Use-Case-Methoden (höchste Einzelmethode weit unter dem Schwellwert); kein Smell.
// PMD.TooManyMethods: zentraler Karten-/Vorhaben-Use-Case-Service — viele kleine, kohäsive Methoden
// (Anlegen/Bearbeiten/Move/Archiv/Zuständige/Labels je Erfolgs- und Fehlerpfad);
// eine Aufspaltung würde denselben Use-Case-Kontext künstlich zerreißen, kein God-Class-Smell.
// PMD.ExcessivePublicCount: dieselbe Familie wie TooManyMethods, nur über die öffentliche
// Oberfläche gezählt — mit dem Ingest-Pfad für Abhängigkeiten (#566) ist die Grenze erreicht. Die
// Klasse ist bewusst der einzige Zugang zum card-Modul (ArchUnit-Fassadenregel): Ein zweiter
// öffentlicher Service würde die Regel brechen, statt den Umfang zu verringern. Wenn hier weiter
// wächst, ist eine echte Aufteilung des Moduls fällig — nicht eine höhere Schwelle.
@SuppressWarnings({
  "PMD.CouplingBetweenObjects",
  "PMD.CyclomaticComplexity",
  "PMD.TooManyMethods",
  "PMD.ExcessivePublicCount"
})
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
  private final CardDependencyRepository dependencies;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final ProjectService projects;
  private final CardColumnTransitionRepository transitions;
  private final KartenZuordnung zuordnung;
  private final CardActivityRepository activity;
  private final ActorContext actor;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public CardService(
      CardRepository cards,
      CardDependencyRepository dependencies,
      BoardService boardService,
      PermissionChecker permissions,
      ProjectService projects,
      CardColumnTransitionRepository transitions,
      KartenZuordnung zuordnung,
      CardActivityRepository activity,
      ActorContext actor,
      ApplicationEventPublisher events,
      Clock clock) {
    this.cards = cards;
    this.dependencies = dependencies;
    this.boardService = boardService;
    this.permissions = permissions;
    this.projects = projects;
    this.transitions = transitions;
    this.zuordnung = zuordnung;
    this.activity = activity;
    this.actor = actor;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Publiziert ein {@link CardBoardActivityEvent} für Live-Board-Updates. Die Composition-Root
   * übersetzt es in den SSE-Vertrag des board-Moduls, der Board-Event-Listener reicht es
   * transaktionsgebunden (nach Commit) an die SSE-Registry weiter — bei Rollback entsteht kein
   * Event. Wird am erfolgreichen Ende jeder board-relevanten Karten-Mutation aufgerufen.
   */
  private void publishChanged(long boardId, ActivityType type, @Nullable Long cardId) {
    events.publishEvent(new CardBoardActivityEvent(boardId, type, cardId));
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
    return doCreate(
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
        null);
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
    return doCreate(
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
        derivedFrom);
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
   * gemeinsamen Anlegepfad {@code doCreate}, damit Nummern-, Positions- und Transitionsvergabe
   * unverändert greifen — das Ereignis je Karte ist dessen Nebenwirkung, und der Board-Strom
   * liefert ohnehin erst nach Commit aus.
   *
   * <p>Die Karten landen in Eingabereihenfolge am Ende der Zielspalte: {@code doCreate} holt je
   * Karte eine frische aktive Position aus {@link CardRepository#allocateActivePosition(long)}.
   */
  @Transactional
  public List<CardView> createCardsBatch(
      long userId, long boardId, long columnId, List<NewCard> neueKarten) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    // Die Spalte einmal vorab gegen das Board prüfen: ein falsches Ziel soll scheitern, bevor die
    // erste Karte eine Nummer verbraucht hat. doCreate prüft sie je Karte erneut (unverändert).
    boardService.requireColumn(columnId, boardId);
    return neueKarten.stream()
        .map(
            card ->
                doCreate(
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
                    null))
        .toList();
  }

  // Kern-Logik des Anlegens ohne eigene @Transactional: wird von den öffentlichen create-
  // Überladungen (je @Transactional) aufgerufen, ohne Self-Invocation über den Proxy (java:S6809).
  private CardView doCreate(
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
      @Nullable String externalKey,
      @Nullable Integer givenNumber,
      @Nullable Integer derivedFrom) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    Long herkunft = DerivedFrom.resolve(cards, projectId, derivedFrom, null);
    ColumnView column = boardService.requireColumn(columnId, boardId);
    Long effectiveParent =
        parentId == null ? null : requireEpicInBoard(parentId, boardId).requireId();

    // Vorgegebene Nummer (#565) ersetzt die Vergabe, nicht die Sperre — die hat der Aufrufer
    // bereits genommen und die Nummer unter ihr geprüft.
    int number = givenNumber != null ? givenNumber : cards.allocateCardNumber(projectId);
    int position = cards.allocateActivePosition(columnId);
    Instant now = clock.instant();
    Instant movedToDoneAt = doneStempel(column, now);
    Card saved =
        cards.save(
            new Card(
                null,
                boardId,
                columnId,
                number,
                title.trim(),
                normalize(description),
                position,
                false,
                movedToDoneAt,
                userId,
                now,
                now,
                CardType.CARD,
                effectiveParent,
                null,
                dueDate,
                projectId,
                externalKey,
                herkunft,
                null));

    transitions.open(saved.requireId(), columnId, column.name(), now);
    activity.add(
        saved.requireId(),
        userId,
        CardActivityType.CREATED,
        "Karte angelegt",
        now,
        actor.current());
    setDependencies(saved, dependsOn);
    if (assigneeIds != null && !assigneeIds.isEmpty()) {
      zuordnung.ersetzeZustaendige(saved.requireId(), projectId, assigneeIds);
    }
    if (labelIds != null && !labelIds.isEmpty()) {
      zuordnung.ersetzeLabels(saved.requireId(), boardId, labelIds);
    }
    publishChanged(boardId, ActivityType.CREATED, saved.requireId());
    return view(saved);
  }

  /**
   * Legt ein Vorhaben an. Vorhaben halten keine Board-Position und liegen technisch in der ersten
   * Spalte.
   */
  @Transactional
  public CardView createEpic(
      long userId,
      long boardId,
      String title,
      @Nullable String description,
      @Nullable String shortcode) {
    return doCreateEpic(userId, boardId, title, description, shortcode);
  }

  // Kern-Logik der Vorhaben-Anlage ohne eigene @Transactional: wird von createEpic und
  // openEpicFromCard (je @Transactional) aufgerufen, ohne Self-Invocation über den Proxy
  // (java:S6809).
  private CardView doCreateEpic(
      long userId,
      long boardId,
      String title,
      @Nullable String description,
      @Nullable String shortcode) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.require(userId, projectId, Permission.EPIC_CREATE);

    long columnId = boardService.firstColumn(boardId).id();

    int number = cards.allocateCardNumber(projectId);
    Instant now = clock.instant();
    Card saved =
        cards.save(
            new Card(
                null,
                boardId,
                columnId,
                number,
                title.trim(),
                normalize(description),
                0,
                false,
                null,
                userId,
                now,
                now,
                CardType.EPIC,
                null,
                trimToNull(shortcode),
                null,
                projectId,
                null,
                // Herkunft: kein Schreibpfad hier — der kommt in Issue #604.
                null,
                // Anforderungskarte: kein Schreibpfad hier — der kommt in Issue #639.
                null));
    publishChanged(boardId, ActivityType.CREATED, saved.requireId());
    return view(saved);
  }

  /**
   * Karten eines Boards (ohne Vorhaben) mit ihren Zusatzdaten — vier Sammelzugriffe statt vier
   * Abfragen <em>je Karte</em> (Issue #768).
   *
   * <p>Bewusst nicht über {@link #view(Card)}: Der baut eine einzelne Karte und lädt
   * Abhängigkeiten, Zuständige, Labels und Herkunft je Aufruf einzeln nach. Auf einer ganzen
   * Board-Liste ergibt das ein N+1 mit vier Abfragen pro Karte; hier sind es vier für die gesamte
   * Liste. Für die Einzelkarten-Pfade bleibt {@code view(...)} unverändert — dort ist die
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
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    List<Card> karten =
        cards.findByBoardId(boardId).stream().filter(c -> c.type() == CardType.CARD).toList();
    Set<Long> ids = karten.stream().map(Card::requireId).collect(Collectors.toSet());
    // Karten ohne Eintrag fehlen in den Maps (Vertrag der drei findByCardIds) — die Sicht setzt
    // dort eine leere Liste, nie null.
    Map<Long, List<Integer>> abhaengigkeiten = dependencies.findByCardIds(ids);
    Map<Long, List<Long>> zustaendige = zuordnung.zustaendigeJeKarte(ids);
    Map<Long, List<Long>> labelIds = zuordnung.labelsJeKarte(ids);
    Map<Long, Integer> nummern = herkunftsnummern(karten);
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
                    abhaengigkeiten.getOrDefault(c.requireId(), List.of()),
                    c.type(),
                    c.parentId(),
                    c.shortcode(),
                    zustaendige.getOrDefault(c.requireId(), List.of()),
                    c.dueDate(),
                    labelIds.getOrDefault(c.requireId(), List.of()),
                    c.derivedFromCardId() == null ? null : nummern.get(c.derivedFromCardId())))
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
   * Sichtbare Board-Items (Karten <em>und</em> Vorhaben) als schlanke Projektion für modulfremde
   * Aufrufer: ohne archivierte Karten, nach Position in der Spalte sortiert. Erfordert
   * Projekt-Mitgliedschaft (Leserecht).
   *
   * <p>Bewusst nicht {@link CardView}: diese Projektion kommt mit einer einzigen Abfrage aus,
   * während {@code view(...)} je Karte Abhängigkeiten, Zuständige und Labels nachlädt (N+1). Der
   * {@code kanbancompat}-Ingest listet ganze Boards und braucht davon nichts.
   */
  @Transactional(readOnly = true)
  public List<BoardItemView> listBoardItems(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    List<Card> sichtbar =
        cards.findByBoardId(boardId).stream()
            .filter(c -> !c.archived())
            .sorted(Comparator.comparingInt(Card::positionInColumn))
            .toList();
    Map<Long, Integer> herkunftsnummern = herkunftsnummern(sichtbar);
    return sichtbar.stream()
        .map(
            c ->
                new BoardItemView(
                    c.requireId(),
                    c.number(),
                    c.title(),
                    c.description(),
                    c.columnId(),
                    c.positionInColumn(),
                    c.type() == CardType.EPIC,
                    c.externalKey(),
                    c.derivedFromCardId() == null
                        ? null
                        : herkunftsnummern.get(c.derivedFromCardId())))
        .toList();
  }

  /**
   * Herkunftsbaum <b>eines Vorhabens</b> als flache Liste in Präorder mit Tiefe (Issue #643).
   *
   * <p>Bis Issue #645 gab es daneben einen board-weiten Baum. Er stellte alle Ketten des Boards
   * nebeneinander und beantwortete die Frage „was gehört zu diesem Vorhaben" damit nicht; der PO
   * hat ihn abbestellt. Geblieben ist diese Sicht: dieselbe Rechnung auf den Mitgliedern
   * <em>eines</em> Vorhabens — die Zugehörigkeit aus {@link EpicMembership} (#632), der Baum aus
   * {@link DerivationTree} (#609). Eine zweite Graphenrechnung wäre ein zweiter Ort für denselben
   * Fehler.
   *
   * <p><b>Was durch die Einschränkung anders wird:</b> {@code build} weist alles als extern aus,
   * was nicht in der übergebenen Menge liegt. Ein Vorfahr, der auf dem Board liegt, aber kein
   * Mitglied ist — archiviert oder selbst ein Vorhaben —, ist damit extern <em>ohne</em> Nummer,
   * und sein Kind wird zur Wurzel. Ebenso gilt eine Abhängigkeit auf eine Board-Karte ausserhalb
   * des Vorhabens als extern und setzt {@code blocked} nicht. Beides ist gewollt: Was das Vorhaben
   * nicht enthält, kann sein Baum nicht als offen behaupten.
   *
   * @throws CardNotFoundException wenn {@code epicId} kein Vorhaben dieses Boards bezeichnet.
   *     Bewusst nicht als leere Liste: Sonst könnte der Client „existiert nicht" nicht vom
   *     legitimen Leer-Fall unterscheiden. Nicht-Existenz und Fremdzugriff bleiben ununterscheidbar
   *     wie bei {@link #getCard}.
   */
  @Transactional(readOnly = true)
  public List<DerivationNodeView> epicDerivationTree(long userId, long boardId, long epicId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));

    // Ungefiltert in die Zugehörigkeitsrechnung — genau wie in listEpics: EpicMembership filtert
    // Archiviertes selbst heraus, kappt die Kette aber nicht daran. Ein Vorfilter hier ergäbe eine
    // andere Mitgliedermenge als auf der Kachel, und beide Zahlen stammen aus derselben Ansicht.
    List<Card> alle = cards.findByBoardId(boardId);
    Set<Card> mitglieder = EpicMembership.compute(alle).get(epicId);
    if (mitglieder == null) {
      throw new CardNotFoundException();
    }

    // Bewusst UNSORTIERT weitergereicht: `DerivationTree.build` ordnet Geschwister selbst —
    // topologisch, bei Gleichstand nach Nummer. Eine Vorsortierung hier waere nicht nur doppelt,
    // sie verdeckte die eigentliche Ordnung: Mit vorsortierter Eingabe faellt ein Ausfall der
    // Sortierung in `build` nicht mehr auf (PIT-Befund zu Issue #645).
    List<Card> baumKarten = List.copyOf(mitglieder);
    Set<Long> ids = baumKarten.stream().map(Card::requireId).collect(Collectors.toSet());
    return DerivationTree.build(
        baumKarten,
        dependencies.findByCardIds(ids),
        fremdeVorfahrenNummern(alle),
        zuordnung.gezaehlteMarken(boardId, ids));
  }

  /**
   * Nummern der Vorfahren, die nicht auf diesem Board liegen — ein Sammelzugriff statt einer
   * Abfrage je Kante.
   *
   * <p>Bezugsmenge ist bewusst das <b>Board</b> und nicht die jeweils übergebene Teilmenge: Nur so
   * behält eine board-fremde Herkunft im Vorhaben-Baum ihre Nummer, während board-interne
   * Nicht-Mitglieder ohne Nummer extern bleiben.
   */
  private Map<Long, Integer> fremdeVorfahrenNummern(List<Card> boardCards) {
    Set<Long> imBoard = boardCards.stream().map(Card::requireId).collect(Collectors.toSet());
    Set<Long> fremde =
        boardCards.stream()
            .map(Card::derivedFromCardId)
            .filter(Objects::nonNull)
            .filter(id -> !imBoard.contains(id))
            .collect(Collectors.toSet());
    Map<Long, Integer> nummern = new HashMap<>();
    if (!fremde.isEmpty()) {
      for (Card vorfahr : cards.findByIds(fremde)) {
        nummern.put(vorfahr.requireId(), vorfahr.number());
      }
    }
    return nummern;
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
    return view(card);
  }

  /**
   * Sichert zu, dass die Karte auf dem angegebenen Board liegt — der Board-Guard des
   * token-gebundenen {@code kanbancompat}-Zugriffs (#44).
   *
   * @throws CardNotFoundException wenn die Karte fehlt oder auf einem anderen Board liegt
   */
  @Transactional(readOnly = true)
  public void requireOnBoard(long cardId, long boardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    // Wertvergleich der Board-IDs (Long): '!=' würde Referenzen vergleichen und bei IDs
    // jenseits des Long-Caches (> 127) falsch schlagen.
    if (!Long.valueOf(boardId).equals(card.boardId())) {
      throw new CardNotFoundException();
    }
  }

  /**
   * Vorhaben eines Boards inkl. Fortschritt.
   *
   * <p><b>Gezählt wird der Nachfahrenbaum, nicht nur die direkte Zuordnung</b> (Issue #633): Wer
   * dem Vorhaben über {@code parentId} zugeordnet ist, bringt alles mit, was über {@code
   * derivedFromCardId} aus ihm entstanden ist. Das wirkt rückwirkend auf den Bestand — die
   * Zugehörigkeit wird gerechnet und nirgends gespeichert (Plan #631, E1). Die Rechnung steht in
   * {@link EpicMembership}; sie filtert Archiviertes und Vorhaben selbst heraus, ohne die Kette an
   * ihnen zu kappen.
   */
  @Transactional(readOnly = true)
  public List<EpicView> listEpics(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));

    List<Card> all = cards.findByBoardId(boardId);
    Map<Long, String> columnNames =
        boardService.listColumns(boardId).stream()
            .collect(Collectors.toMap(ColumnView::id, ColumnView::name));
    Map<Long, Set<Card>> membership = EpicMembership.compute(all);
    Map<Long, Card> nachId =
        all.stream().collect(Collectors.toMap(Card::requireId, Function.identity()));

    return all.stream()
        .filter(c -> c.type() == CardType.EPIC)
        .map(
            epic -> {
              Set<Card> members = membership.getOrDefault(epic.requireId(), Set.of());
              int total = members.size();
              int done =
                  (int)
                      members.stream()
                          .filter(c -> isDoneColumn(columnNames.get(c.columnId())))
                          .count();
              // Wurzeln aus den Mitgliedern heraus, nicht neu aus `all`: So gelten für sie
              // dieselben Filter, und die Invariante rootNumbers ⊆ memberNumbers hält von selbst.
              // Das ist kein Zugehörigkeitsfilter mehr — die Zugehörigkeit steht schon fest —,
              // sondern nur die Kennzeichnung, wer von Hand zugeordnet wurde.
              List<Integer> rootNumbers =
                  members.stream()
                      .filter(c -> Objects.equals(c.parentId(), epic.requireId()))
                      .map(Card::number)
                      .sorted()
                      .toList();
              return new EpicView(
                  epic.requireId(),
                  epic.number(),
                  epic.title(),
                  epic.description(),
                  epic.shortcode(),
                  done,
                  total,
                  members.stream().map(Card::number).sorted().toList(),
                  rootNumbers,
                  anforderungsNummer(epic, nachId));
            })
        .toList();
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
    Card card = requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    Card updated = card.withContent(title.trim(), normalize(description));
    if (card.type() == CardType.EPIC) {
      // Vorhaben tragen ein Kürzel, aber keinen Parent.
      updated = updated.withShortcode(trimToNull(shortcode));
    } else {
      // Karten: Vorhaben-Zuordnung im selben PUT setzen/lösen (parentId == null -> lösen).
      Long effectiveParent =
          parentId == null ? null : requireEpicInBoard(parentId, card.boardId()).requireId();
      updated = updated.withParent(effectiveParent).withDueDate(dueDate);
    }
    Card saved = cards.save(updated);
    activity.add(
        cardId,
        userId,
        CardActivityType.UPDATED,
        "Karte bearbeitet",
        clock.instant(),
        actor.current());
    if (dependsOn != null) {
      setDependencies(saved, dependsOn);
    }
    publishChanged(saved.boardId(), ActivityType.UPDATED, cardId);
    return view(saved);
  }

  /**
   * Ersetzt ausschließlich Titel und Beschreibung — der schmale Schreibweg für den
   * kanbancompat-Ingest (#571).
   *
   * <p>Abgrenzung zu {@link #update}: Jene Methode ist ein Voll-Update und löscht bei {@code null}
   * die Vorhaben-Zuordnung, das Fälligkeitsdatum und (bei Vorhaben) das Kürzel. Ein Aufrufer, der
   * nur Titel und Rumpf kennt, kann sie deshalb nicht gefahrlos benutzen. Hier bleibt alles andere
   * stehen; Rechteprüfung, Aktivitätseintrag und Board-Ereignis sind identisch, damit dieser Weg
   * kein Schlupfloch am Audit und an den Rechten vorbei öffnet.
   *
   * <p>{@code description == null} heißt „nicht ändern"; ein blanker Wert löscht die Beschreibung
   * ({@link #normalize}). So vernichtet ein Aufrufer, der das Feld weglässt, keinen Inhalt.
   *
   * <p>Rückgabe ist die {@link BoardItemView} — dieselbe domain-freie Form, die {@link
   * #listBoardItems} liefert. Ein {@link CardView} wäre hier unbrauchbar: Er trägt {@code CardType}
   * aus {@code card.domain}, und das Modul ist außerhalb von {@code card} nicht sichtbar
   * (ArchUnit-Regel {@code CARD_DOMAIN_IST_MODULINTERN}).
   */
  @Transactional
  public BoardItemView updateContent(
      long userId, long cardId, String title, @Nullable String description) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    Card saved =
        cards.save(
            card.withContent(
                title.trim(), description == null ? card.description() : normalize(description)));
    activity.add(
        cardId,
        userId,
        CardActivityType.UPDATED,
        "Karte bearbeitet",
        clock.instant(),
        actor.current());
    publishChanged(saved.boardId(), ActivityType.UPDATED, cardId);
    return new BoardItemView(
        saved.requireId(),
        saved.number(),
        saved.title(),
        saved.description(),
        saved.columnId(),
        saved.positionInColumn(),
        saved.type() == CardType.EPIC,
        saved.externalKey(),
        herkunftsnummer(saved));
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
    activity.add(
        cardId,
        userId,
        CardActivityType.ASSIGNED,
        "Zuständige geändert",
        clock.instant(),
        actor.current());
    publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
    return view(card);
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
    publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
    return view(card);
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
    publishChanged(boardId, ActivityType.UPDATED, cardId);
    return view(card);
  }

  /**
   * Ordnet eine Karte einem Vorhaben zu ({@code parentId}) oder löst die Zuordnung ({@code null}).
   */
  @Transactional
  public CardView assignParent(long userId, long cardId, @Nullable Long parentId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten können einem Epic zugeordnet werden");
    }
    Long effective =
        parentId == null ? null : requireEpicInBoard(parentId, card.boardId()).requireId();
    Card saved = cards.save(card.withParent(effective));
    publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
    return view(saved);
  }

  /**
   * Setzt die Herkunft einer Karte ({@code derivedFrom} als projektweite Kartennummer) oder löscht
   * sie ({@code derivedFrom: null}).
   *
   * <p>Eigener schmaler Endpunkt statt eines Feldes in {@code updateContent}: Jene Methode ist ein
   * Voll-Update und löscht bei {@code null}, was der Aufrufer nicht mitschickt. In einem
   * Jackson-Record ist ein fehlendes JSON-Feld nicht von {@code null} zu unterscheiden — jeder
   * bestehende Client hätte die Herkunft bei jedem Karten-Edit vernichtet (Issue #607).
   *
   * <p>{@code selfCardId} ist hier <strong>nicht</strong> optional: Beim Anlegen kennt niemand die
   * Nummer der neuen Karte, beim Ändern schon. Ohne die eigene ID greift weder die Selbstbezugs-
   * noch die Zyklusabwehr in {@link DerivedFrom#resolve}.
   */
  @Transactional
  public CardView assignDerivedFrom(long userId, long cardId, @Nullable Integer derivedFrom) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    Long herkunft = DerivedFrom.resolve(cards, card.projectId(), derivedFrom, cardId);
    Card saved = cards.save(card.withDerivedFrom(herkunft));
    publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
    return view(saved);
  }

  /**
   * Eröffnet einen Vorgang: legt ein Vorhaben an, macht die übergebene Karte zu seiner Anforderung
   * und ordnet sie ihm zu — in <b>einem</b> Schritt.
   *
   * <p>Bisher entstand das Vorhaben an einer anderen Stelle als die Anforderung, zu der es gehört:
   * anlegen, dann von Hand zuordnen. Der zweite Schritt ging im Arbeitsfluss unter (Anforderung
   * #636).
   *
   * <p><b>Warum eine Transaktion:</b> Getrennte Aufrufe hinterliessen bei einem Abbruch ein
   * Vorhaben ohne Anforderung — also genau den Zustand, den diese Methode abschaffen soll (Plan
   * #637, E2).
   *
   * <p>Das Vorhaben entsteht auf dem Board der Quellkarte und <b>ohne Beschreibung</b>: Den Inhalt
   * trägt die Anforderungskarte, eine Kopie liefe sofort auseinander.
   *
   * @param shortcode optional, wie bei {@link #createEpic}
   */
  @Transactional
  public CardView openEpicFromCard(
      long userId, long cardId, @Nullable String shortcode, String title) {
    Card quelle = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.require(userId, quelle.projectId(), Permission.EPIC_CREATE);
    requireVorgangEroeffenbar(quelle);

    // Bestehenden Weg wiederverwenden statt nachbauen: Nummernvergabe, erste Spalte, Rechte und
    // das Board-Ereignis haengen alle daran.
    CardView vorhaben = doCreateEpic(userId, quelle.boardId(), title, null, shortcode);

    Card epic = cards.findById(vorhaben.id()).orElseThrow(CardNotFoundException::new);
    Long anforderung = RequirementCard.resolve(cards, epic, quelle.number());
    Card gespeichert = cards.save(epic.withRequirement(anforderung));
    cards.save(quelle.withParent(epic.requireId()));

    return view(gespeichert);
  }

  /**
   * Die Ablehnungen des Vorgangs-Eröffnens, alle mit Status 400.
   *
   * <p>Sie stehen <b>vor</b> dem Anlegen: Eine Ablehnung danach liefe zwar auch sauber zurueck,
   * verbrauchte aber eine Kartennummer — die Sequenz rollt nicht mit.
   */
  private void requireVorgangEroeffenbar(Card quelle) {
    if (quelle.type() == CardType.EPIC) {
      throw new InvalidDependencyException(
          "Ein Vorhaben eroeffnet keinen Vorgang aus sich selbst: " + quelle.number());
    }
    // Zwei Ruhezustaende, eine Regel: Eine ruhende Karte eroeffnet keinen Vorgang. Der Papierkorb
    // ist im Domain-Record nicht abgebildet — die Nummernsuche filtert ihn, findById nicht.
    if (quelle.archived() || liegtImPapierkorb(quelle)) {
      throw new InvalidDependencyException(
          "Eine ruhende Karte eroeffnet keinen Vorgang: " + quelle.number());
    }
    if (quelle.parentId() != null) {
      // Stillschweigendes Umhaengen entzoege einer bestehenden Gruppierung eine Karte, ohne dass
      // jemand es merkt. Erst loesen, dann eroeffnen.
      throw new InvalidDependencyException(
          "Die Karte ist bereits einem Vorhaben zugeordnet: " + quelle.number());
    }
  }

  /**
   * Ob die Karte im Papierkorb liegt.
   *
   * <p>{@code deletedAt} ist keine Komponente von {@link Card}; die Nummernsuche filtert den
   * Papierkorb dagegen (Port-Zusage von {@code findByProjectIdAndNumber}), {@code findById} nicht.
   * Findet die Suche unter derselben Nummer nichts, ist die Karte geloescht.
   */
  private boolean liegtImPapierkorb(Card karte) {
    return cards.findByProjectIdAndNumber(karte.projectId(), karte.number()).isEmpty();
  }

  /**
   * Setzt oder löscht ({@code null}) die Anforderungskarte eines Vorhabens.
   *
   * <p>Übergeben wird die projektweite <b>Kartennummer</b>, gespeichert die ID — die Nummer ändert
   * sich beim Projektwechsel. Die vier Ablehnungen stehen in {@link RequirementCard}.
   *
   * <p>Eigener schmaler Endpunkt statt eines Feldes im Voll-Update, aus demselben Grund wie bei der
   * Herkunft (#607): Ein Voll-Update kann ein fehlendes Feld nicht von {@code null} unterscheiden
   * und löschte die Zuordnung bei jedem Karten-Edit.
   */
  @Transactional
  public CardView assignRequirement(
      long userId, long cardId, @Nullable Integer requirementCardNumber) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    Long anforderung = RequirementCard.resolve(cards, card, requirementCardNumber);
    Card saved = cards.save(card.withRequirement(anforderung));
    publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
    return view(saved);
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
      activity.add(
          cardId,
          userId,
          CardActivityType.MOVED,
          "Verschoben nach " + target.name(),
          switchedAt,
          actor.current());
    }

    // moved_to_done_at: beim Eintritt in eine "Done"-Spalte setzen, beim Verlassen löschen.
    boolean targetIsDone = isDoneColumn(target.name());
    Instant done = card.movedToDoneAt();
    if (targetIsDone && done == null) {
      done = clock.instant();
    } else if (!targetIsDone) {
      done = null;
    }

    Card moved = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    CardView result = view(cards.save(moved.withMovedToDoneAt(done)));
    publishChanged(card.boardId(), ActivityType.MOVED, cardId);
    return result;
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
    publishChanged(boardId, ActivityType.MOVED, null);
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
    // MOVED, movedToDoneAt beim Eintritt in eine Done-Spalte, Vorhaben-Zuordnung bleibt. Der
    // Umzugspfad unten ist auf den Board-Wechsel gebaut: Er leert parentId (das Ziel-Board hat
    // eigene Vorhaben) und movedToDoneAt und schreibt den Spaltenverlauf auch dann fort, wenn die
    // Spalte dieselbe bleibt. Auf dem eigenen Board wäre jede dieser drei Wirkungen falsch.
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
      dependencies.deleteByCardId(cardId);
      zuordnung.entferneZustaendige(cardId);
    }

    // Spaltenverlauf: der board-/spaltenübergreifende Umzug zählt als Spaltenwechsel.
    Instant switchedAt = clock.instant();
    transitions.closeOpen(cardId, switchedAt);
    transitions.open(cardId, targetColumnId, targetColumn.name(), switchedAt);

    Card moved = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    Card cleaned = moved.withParent(null).withMovedToDoneAt(null);
    if (!sameProject) {
      // Die Herkunft ist projekt-lokal: Der Vorfahr bleibt zurueck, und ein Verweis ueber die
      // Projektgrenze zeigte auf eine Nummer, die dort einer anderen Karte gehoeren kann.
      // Anders als withParent(null) gilt das NUR beim Projektwechsel — innerhalb des Projekts
      // ueberlebt die Kette den Board-Wechsel.
      cleaned = cleaned.withDerivedFrom(null);
      // Dieselbe Begruendung fuer die Anforderung: Sie ist board- und damit projekt-lokal.
      cleaned = cleaned.withRequirement(null);
    }
    CardView result = view(cards.save(cleaned));
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
    publishChanged(card.boardId(), ActivityType.MOVED, cardId);
    publishChanged(targetBoardId, ActivityType.MOVED, cardId);
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

  @Transactional
  public CardView archive(long userId, long cardId) {
    return doArchive(userId, cardId);
  }

  private CardView doArchive(long userId, long cardId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    activity.add(
        card.requireId(),
        userId,
        CardActivityType.ARCHIVED,
        "Archiviert",
        clock.instant(),
        actor.current());
    CardView result = view(cards.save(card.asArchived()));
    publishChanged(card.boardId(), ActivityType.ARCHIVED, card.requireId());
    return result;
  }

  /**
   * Archiviert mehrere Karten in einer Transaktion (alles-oder-nichts). Nutzt je Karte die
   * Einzel-Logik von {@link #archive(long, long)} inklusive Rechteprüfung; fehlt an einer Karte das
   * Recht oder existiert sie nicht, rollt der gesamte Batch zurück. Kein Positions-Reindex nötig,
   * da archivierte Karten über {@code active_position = NULL} aus dem Namespace fallen.
   */
  @Transactional
  public List<CardView> bulkArchive(long userId, List<Long> cardIds) {
    return cardIds.stream().map(cardId -> doArchive(userId, cardId)).toList();
  }

  @Transactional
  public CardView restore(long userId, long cardId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    int position = cards.allocateActivePosition(card.columnId());
    activity.add(
        card.requireId(),
        userId,
        CardActivityType.RESTORED,
        "Wiederhergestellt",
        clock.instant(),
        actor.current());
    CardView result = view(cards.save(card.asRestored(position)));
    publishChanged(card.boardId(), ActivityType.RESTORED, card.requireId());
    return result;
  }

  /**
   * Legt eine Karte direkt in einer Spalte des Boards an — idempotent über den optionalen {@code
   * externalKey} (#535, Ingest auf ein Board). Existiert im Projekt bereits eine Karte mit diesem
   * Schlüssel — gleich ob auf einem Board, archiviert oder im Papierkorb —, wird nichts angelegt
   * und die bestehende Karte mit {@code created=false} zurückgegeben. Anlage,
   * Nummern-/Positionsvergabe, Spalten-Transition und Rechteprüfung laufen über den normalen
   * Anlege-Pfad; beim Duplikat entsteht nichts (kein Aktivitätseintrag, kein Event).
   *
   * <p>Nebenläufigkeit: bewusst SELECT-first statt Insert-and-catch — nach einer
   * Constraint-Verletzung wäre die Transaktion rollback-only, ein Nachlesen in ihr unmöglich. Den
   * seltenen Wettlauf zweier gleichzeitiger Erst-Ingests fängt der partielle Unique-Index (V24) als
   * Backstop; er endet als 409 über die bestehende {@code DataIntegrityViolationException}-
   * Behandlung (#496), und der Wiederholungs-Request trifft dann den SELECT.
   */
  @Transactional
  public CardCreation createDirect(long userId, long boardId, long columnId, DirectCard card) {
    long projectId = boardService.requireProjectId(boardId);
    // Rechte VOR dem Duplikat-Check: der Rückgabepfad darf Unberechtigten keine Existenz leaken.
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    // Die beiden Identitaetsfelder einmal in lokale Variablen: die Null-Pruefungen unten gelten
    // sonst nur fuer den jeweiligen Aufruf, nicht fuer den danach (SpotBugs
    // NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE).
    String externalKey = card.externalKey();
    Integer givenNumber = card.givenNumber();
    if (externalKey != null) {
      Optional<Card> existing = cards.findByProjectIdAndExternalKey(projectId, externalKey);
      if (existing.isPresent()) {
        requireMatchingNumber(existing.get(), givenNumber);
        // Idempotenz-Treffer: derivedFrom wird ignoriert wie Titel und Rumpf auch. Anders als
        // number, das requireMatchingNumber als Identitaetsfeld verifiziert (#565).
        return new CardCreation(view(existing.get()), false);
      }
    }
    if (givenNumber != null) {
      requireNumberAvailable(projectId, givenNumber);
    }
    CardView created =
        doCreate(
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
            externalKey,
            givenNumber,
            card.derivedFrom());
    return new CardCreation(created, true);
  }

  /**
   * Der Idempotenz-Treffer muss dieselbe Identität liefern, die angefordert wurde (#565). Eine
   * abweichende Nummer stillschweigend zurückzugeben wäre das Gegenteil des Zwecks: Der Aufrufer
   * bekäme eine fremde Identität und merkte es erst, wenn migrierte Abhängigkeiten ins Leere
   * zeigen.
   */
  private static void requireMatchingNumber(Card existing, @Nullable Integer givenNumber) {
    if (givenNumber != null && !givenNumber.equals(existing.number())) {
      throw new CardNumberConflictException(
          "Der Schluessel gehoert bereits zu Karte #"
              + existing.number()
              + ", angefordert war #"
              + givenNumber
              + ".");
    }
  }

  /**
   * Prüft unter der Nummern-Sperre, ob eine vorgegebene Nummer vergeben werden darf (#565).
   *
   * <p>Reihenfolge ist wesentlich: erst sperren, dann prüfen. Läuft die Prüfung vor der Sperre,
   * rutscht eine gleichzeitige Anlage zwischen Prüfung und Schreiben — und genau diese Vorbedingung
   * ist der einzige Schutz davor, in ein gewachsenes Projekt hineinzuimportieren.
   */
  private void requireNumberAvailable(long projectId, int number) {
    cards.lockCardNumbers(projectId);
    if (cards.hasCardWithoutExternalKey(projectId)) {
      throw new CardNumberConflictException(
          "Das Projekt enthaelt Karten ausserhalb eines Imports — eine vorgegebene Nummer wird nur"
              + " in ein Projekt ohne importfremde Karten uebernommen.");
    }
    if (cards.isNumberTaken(projectId, number)) {
      throw new CardNumberConflictException("Nummer #" + number + " ist bereits vergeben.");
    }
  }

  /**
   * Eine anzulegende Board-Karte im Stapel (Issue #1200): Titel und optionale Beschreibung. Board
   * und Spalte stehen bewusst nicht hier, sondern gelten für den ganzen Stapel — er füllt genau
   * eine Spalte.
   */
  public record NewCard(String title, @Nullable String description) {}

  /** Ergebnis eines idempotenten Ingests (#534): die Karte plus ob sie neu angelegt wurde. */
  public record CardCreation(CardView view, boolean created) {}

  /**
   * Die Nutzdaten einer direkt in einer Board-Spalte anzulegenden Karte (#535). Das Routing —
   * Benutzer, Board, Spalte — steht bewusst nicht hier, sondern bleibt skalar an {@link
   * #createDirect}, damit die Rechteprüfung vor dem Duplikat-Check lesbar bleibt.
   *
   * <p>{@code externalKey} und {@code givenNumber} sind Identitätsfelder (#565): Der Schlüssel
   * entscheidet über den Idempotenz-Treffer, und eine vorgegebene Nummer wird gegen die bestehende
   * Karte verifiziert. {@code derivedFrom} dagegen wird beim Idempotenz-Treffer ignoriert — wie
   * Titel und Rumpf auch.
   */
  public record DirectCard(
      String title,
      @Nullable String description,
      @Nullable String externalKey,
      @Nullable Integer givenNumber,
      @Nullable Integer derivedFrom) {}

  /**
   * Löst eine projektweite Kartennummer zu ihrer {@link CardView} auf. Erfordert
   * Projekt-Mitgliedschaft (Leserecht); Nichtmitglied wie unbekannte oder gelöschte Nummer → 404
   * (kein Existenz-Leak). Basis für klickbare {@code #N}-Verweise (#403).
   */
  @Transactional(readOnly = true)
  public CardView getByNumber(long userId, long projectId, int number) {
    permissions.requireMembership(userId, projectId);
    Card card =
        cards.findByProjectIdAndNumber(projectId, number).orElseThrow(CardNotFoundException::new);
    return view(card);
  }

  /**
   * Sucht eine projektweite Kartennummer über <strong>alle Projekte, in denen der Benutzer lesen
   * darf</strong>, und liefert je Treffer die Karte samt Ortsangabe (Projekt, Board, Spalte).
   *
   * <p><strong>Warum eine Liste:</strong> Kartennummern sind projektweit eindeutig, nicht global
   * ({@code uq_card_number (project_id, number)}). Dass die Projekte hier faktisch disjunkte
   * Nummernkreise haben (Startnummer-Floor aus V20), ist Konvention und keine Invariante — dieselbe
   * Nummer kann in mehreren Projekten existieren, und dann sind alle Treffer gemeint.
   *
   * <p><strong>Sichtbarkeit:</strong> Gesucht wird ausschließlich in den Projekten des Benutzers.
   * Ein fremdes Projekt macht sich in keiner Weise bemerkbar — kein 403, kein Zähler, kein
   * Unterschied im Antwortverhalten. Die leere Liste ist die Antwort sowohl für „Nummer existiert
   * nirgends" als auch für „Nummer existiert nur in fremden Projekten" (Prinzip aus {@link
   * PermissionChecker}). Ein <b>Plattform-Admin</b> findet dagegen per Definition alles: Die
   * Projektauswahl kommt von {@link ProjectService#listAccessible(long)}, das ihm wie überall sonst
   * (Projektliste, {@code requireMembership}) alle Projekte zeigt. Das ist bewusst das
   * Bestandsverhalten und keine Sonderregel dieser Suche.
   *
   * <p><strong>Was nicht gefunden wird:</strong> Karten im Papierkorb — ihre Nummer bleibt belegt
   * (sie kann wiederhergestellt werden), per Suche sind sie unsichtbar. <b>Archivierte</b> Karten
   * bleiben dagegen auffindbar, ebenso Karten auf einem <b>archivierten Board</b>: Deren Boardname
   * wird über {@link BoardService#requireBoardSummary(long)} aufgelöst, das den Archiv-Filter
   * bewusst nicht anwendet und den Zustand stattdessen mitliefert.
   */
  @Transactional(readOnly = true)
  public List<CardSearchHit> searchByNumber(long userId, int number) {
    Map<Long, String> projectNames =
        projects.listAccessible(userId).stream()
            .collect(
                Collectors.toMap(
                    ProjectService.AccessibleProject::id, ProjectService.AccessibleProject::name));
    if (projectNames.isEmpty()) {
      // Ohne Projekte gibt es nichts zu durchsuchen — und eine leere IN-Menge wäre keine sinnvolle
      // Anfrage an die Datenbank (siehe Zusicherung an CardRepository.findByNumberInProjects).
      return List.of();
    }
    return cards.findByNumberInProjects(number, List.copyOf(projectNames.keySet())).stream()
        .map(c -> hit(c, Objects.requireNonNull(projectNames.get(c.projectId()))))
        .toList();
  }

  /**
   * Baut den Suchtreffer samt Ortsangabe. Board und Spalte werden über die board-Fassade aufgelöst
   * — der Boardname auch dann, wenn das Board archiviert ist.
   */
  private CardSearchHit hit(Card c, String projectName) {
    Long boardId = c.boardId();
    BoardSummary board = boardService.requireBoardSummary(boardId);
    ColumnView column = boardService.requireColumn(c.columnId(), boardId);
    return new CardSearchHit(
        view(c),
        c.projectId(),
        projectName,
        boardId,
        board.name(),
        board.archived(),
        column.id(),
        column.name());
  }

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
   * dasselbe Muster wie {@code doArchive} und {@code doCreateEpic}.
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
   * Verschiebt eine Karte in den Papierkorb (Soft-Delete, reversibel). Recht: TICKET/EPIC_DELETE.
   */
  @Transactional
  public void delete(long userId, long cardId) {
    doDelete(userId, cardId);
  }

  private void doDelete(long userId, long cardId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    // Beim Löschen eines Vorhabens die Kinder lösen — die DB-„ON DELETE SET NULL"-Kaskade auf
    // parent_id feuert nur beim Hard-Delete, nicht beim Soft-Delete.
    if (card.type() == CardType.EPIC) {
      cards.findByBoardId(card.boardId()).stream()
          .filter(c -> Objects.equals(c.parentId(), card.requireId()))
          .forEach(child -> cards.save(child.withParent(null)));
    }
    cards.softDelete(card.requireId(), clock.instant());
    publishChanged(card.boardId(), ActivityType.DELETED, card.requireId());
  }

  /**
   * Verschiebt mehrere Karten in einer Transaktion in den Papierkorb (alles-oder-nichts). Nutzt je
   * Karte die Einzel-Logik von {@link #delete(long, long)} inklusive Rechteprüfung und Lösen der
   * Vorhaben-Kinder; fehlt an einer Karte das Recht oder existiert sie nicht, rollt der gesamte
   * Batch zurück.
   */
  @Transactional
  public void bulkDelete(long userId, List<Long> cardIds) {
    cardIds.forEach(cardId -> doDelete(userId, cardId));
  }

  /**
   * Holt eine Karte aus dem Papierkorb zurück (ans Spaltenende). Recht wie Löschen (Member und
   * aufwärts) — so kann ein Member eine versehentlich gelöschte Karte selbst wiederherstellen.
   */
  @Transactional
  public CardView restoreFromTrash(long userId, long cardId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    int position = cards.allocateActivePosition(card.columnId());
    cards.restoreFromTrash(card.requireId(), position);
    activity.add(
        card.requireId(),
        userId,
        CardActivityType.RESTORED,
        "Aus Papierkorb wiederhergestellt",
        clock.instant(),
        actor.current());
    publishChanged(card.boardId(), ActivityType.RESTORED, card.requireId());
    // View aus der bereits geladenen Karte mit neuer Position — der JDBC-Restore hat die DB-Zeile
    // geändert; ein erneutes findById käme aus dem JPA-Cache noch mit dem alten Stand.
    return view(card.asRestored(position));
  }

  /**
   * Entfernt eine Karte endgültig (Hard-Delete). Nur für Board-Verwalter (Projekt-Admin/Owner,
   * Recht {@link Permission#BOARD_DELETE}) — bewusst restriktiver als das reversible Löschen.
   */
  @Transactional
  public void purge(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.require(
        userId, boardService.requireProjectId(card.boardId()), Permission.BOARD_DELETE);
    // Vor dem Delete publizieren (Issue #503): Nachgelagerte Module (Anhänge) planen ihre
    // Aufräum-Aufträge ein, solange die Metadaten existieren — die Cascade nimmt sie gleich mit.
    events.publishEvent(new CardsPurgedEvent(List.of(card.requireId())));
    dependencies.deleteByCardId(card.requireId());
    cards.deleteById(card.requireId());
    publishChanged(card.boardId(), ActivityType.DELETED, card.requireId());
  }

  /** Karten im Papierkorb eines Boards. Erfordert Board-Mitgliedschaft (Leserecht). */
  @Transactional(readOnly = true)
  public List<CardView> listTrash(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    return cards.findTrashByBoardId(boardId).stream()
        .filter(c -> c.type() == CardType.CARD)
        .map(this::view)
        .toList();
  }

  /**
   * Lädt die Karte und verlangt das je nach Kartentyp (Ticket/Vorhaben) passende Recht. Die Rechte
   * sind projekt-basiert und werden über {@code card.projectId()} (immer gesetzt, V18) geprüft —
   * nicht über das Board; für board-gebundene Karten ist die Prüfung identisch (die Projekt-ID
   * stimmt mit dem Board-Projekt überein).
   */
  private Card requireCardOp(
      long userId, long cardId, Permission ticketPermission, Permission epicPermission) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.require(
        userId, card.projectId(), card.type() == CardType.EPIC ? epicPermission : ticketPermission);
    return card;
  }

  /**
   * Nummer der Anforderungskarte eines Vorhabens, oder {@code null}.
   *
   * <p>Aufgelöst wird ausschliesslich innerhalb der Board-Karten. Das ist keine Einschränkung,
   * sondern die Grenze, die {@link RequirementCard} beim Setzen zieht: Die Anforderung liegt immer
   * auf dem Board des Vorhabens. Fehlt sie hier trotzdem, liegt sie im Papierkorb — dann ist {@code
   * null} die ehrliche Antwort und keine erfundene Nummer.
   */
  private static @Nullable Integer anforderungsNummer(Card epic, Map<Long, Card> nachId) {
    // Optional-Kette statt zweier Null-Vergleiche: Ein vorgeschaltetes `id == null` waere hier
    // redundant — die Map liefert fuer eine unbekannte ID ohnehin nichts —, und PIT kann eine
    // redundante Bedingung nicht toeten, weil beide Zweige dasselbe Ergebnis liefern.
    return Optional.ofNullable(epic.requirementCardId())
        .map(nachId::get)
        .map(Card::number)
        .orElse(null);
  }

  private Card requireEpicInBoard(long epicId, long boardId) {
    Card epic = cards.findById(epicId).orElseThrow(CardNotFoundException::new);
    if (epic.type() != CardType.EPIC || epic.boardId() != boardId) {
      throw new InvalidDependencyException("Kein Epic dieses Boards: " + epicId);
    }
    return epic;
  }

  /**
   * Ersetzt die Abhängigkeiten einer Karte, <strong>ohne</strong> die Zielnummern auf Existenz zu
   * prüfen (Issue #566) — für den Import aus einem anderen Tracker.
   *
   * <p>Der Unterschied zum UI-Pfad ist Absicht und der Kern dieser Aufgabe: {@link
   * #setDependencies(Card, List)} lehnt unbekannte Nummern hart ab, weil dort ein Tippfehler
   * wahrscheinlicher ist als eine noch nicht angelegte Karte. Beim Import ist es umgekehrt — die
   * Zielkarte kommt oft erst später an, und eine Reihenfolge zu erzwingen hieße, den Import über
   * die Abhängigkeitsgraphen zu sortieren. Die Datenbank trägt das: {@code
   * card_dependency.depends_on_card_number} ist eine Zahl ohne Fremdschlüssel, der Verweis heilt,
   * sobald die Zielkarte existiert.
   *
   * <p>{@code expectedProjectId} bindet die Karte an das Projekt des Aufrufers und ist bewusst Teil
   * dieser Methode statt eines eigenen Guards: Ein separater Aufruf ließe sich vergessen. Die
   * Prüfung ist projekt- und nicht boardbezogen — der Ingest kennt sein Projekt, nicht zwingend das
   * Board der Zielkarte.
   *
   * <p>Geteilt bleibt die Selbstverweis-Prüfung — sie hängt nicht am Wissen über andere Karten.
   */
  @Transactional
  public void replaceDependenciesFromIngest(
      long userId, long cardId, long expectedProjectId, @Nullable List<Integer> dependsOn) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    if (card.projectId() != expectedProjectId) {
      throw new CardNotFoundException();
    }
    // Kein Sonderfall für „leer": Die Schleife läuft dann einfach nicht, und replaceDependencies
    // bekommt eine leere Liste — dasselbe Ergebnis, ein Zweig weniger.
    List<Integer> distinct = dependsOn == null ? List.of() : dependsOn.stream().distinct().toList();
    int eigeneNummer = card.number();
    for (Integer dep : distinct) {
      if (dep == eigeneNummer) {
        throw new InvalidDependencyException("Karte kann nicht von sich selbst abhängen");
      }
    }
    dependencies.replaceDependencies(card.requireId(), distinct);
  }

  private void setDependencies(Card card, @Nullable List<Integer> dependsOn) {
    if (dependsOn == null || dependsOn.isEmpty()) {
      dependencies.replaceDependencies(card.requireId(), List.of());
      return;
    }
    List<Integer> distinct = dependsOn.stream().distinct().toList();
    // Querverweise werden projektweit aufgelöst: eine #N-Abhängigkeit darf auf jede Karte desselben
    // Projekts zeigen (board-übergreifend), nicht nur auf dasselbe Board.
    List<Integer> projectNumbers =
        cards.findByProjectId(card.projectId()).stream().map(Card::number).toList();
    int eigeneNummer = card.number();
    for (Integer dep : distinct) {
      if (dep == eigeneNummer) {
        throw new InvalidDependencyException("Karte kann nicht von sich selbst abhängen");
      }
      if (!projectNumbers.contains(dep)) {
        throw new InvalidDependencyException("Unbekannte Kartennummer: " + dep);
      }
    }
    dependencies.replaceDependencies(card.requireId(), distinct);
  }

  /**
   * Done-Zeitpunkt einer frisch angelegten Karte (Issue #1200, E4): Wird sie direkt in einer
   * Done-Spalte angelegt, zählt sie ab sofort als erledigt. Ohne diesen Zeitstempel fiele sie
   * dauerhaft aus der Done-Aufbewahrung, die ausschließlich über ihn greift ({@code
   * findArchivableDoneCards} verlangt {@code movedToDoneAt is not null}).
   *
   * <p>Eigene Methode statt eines Ausdrucks in {@code doCreate}: Dort trieb die Verzweigung die
   * NPath-Komplexität des ohnehin verzweigungsreichen Anlegepfads über die PMD-Schwelle.
   */
  private static @Nullable Instant doneStempel(ColumnView column, Instant now) {
    return isDoneColumn(column.name()) ? now : null;
  }

  private static boolean isDoneColumn(@Nullable String name) {
    return name != null && name.toLowerCase(Locale.ROOT).contains("done");
  }

  private static @Nullable String normalize(@Nullable String description) {
    return description == null || description.isBlank() ? null : description;
  }

  private static @Nullable String trimToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /**
   * Herkunfts-Nummern zu einer Kartenliste — <strong>ein</strong> Sammelzugriff, unabhaengig von
   * der Zahl verschiedener Vorfahren. Je Karte einzeln nachzuschlagen ergaebe ein N+1 auf einer
   * Liste, die ein ganzes Board umfasst.
   */
  private Map<Long, Integer> herkunftsnummern(List<Card> karten) {
    Set<Long> ids =
        karten.stream()
            .map(Card::derivedFromCardId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<Long, Integer> nummern = new HashMap<>();
    for (Card vorfahr : cards.findByIds(ids)) {
      nummern.put(vorfahr.requireId(), vorfahr.number());
    }
    return nummern;
  }

  /**
   * Herkunfts-Nummer einer einzelnen Karte. Liefert {@code null}, wenn keine Herkunft gesetzt ist
   * oder der Vorfahr nicht mehr existiert — die Sicht haelt den Zustand aus, statt zu scheitern.
   */
  private @Nullable Integer herkunftsnummer(Card c) {
    Long id = c.derivedFromCardId();
    return id == null ? null : cards.findById(id).map(Card::number).orElse(null);
  }

  private CardView view(Card c) {
    return new CardView(
        c.requireId(),
        c.boardId(),
        c.columnId(),
        c.number(),
        c.title(),
        c.description(),
        // Einzelkarte: der Volltext steht schon in description, ein Auszug daneben wäre redundant.
        null,
        c.positionInColumn(),
        c.archived(),
        c.movedToDoneAt(),
        dependencies.findByCardId(c.requireId()),
        c.type(),
        c.parentId(),
        c.shortcode(),
        zuordnung.zustaendigeVon(c.requireId()),
        c.dueDate(),
        zuordnung.labelsVon(c.requireId()),
        herkunftsnummer(c));
  }

  /**
   * Kartendarstellung inkl. Abhängigkeits-Nummern, Typ und Vorhaben-Zuordnung.
   *
   * <p>{@code description} und {@code excerpt} schließen einander aus (Issue #771): Die
   * Einzelkarten-Pfade liefern den Volltext in {@code description} und lassen {@code excerpt} leer,
   * die Board-Liste genau umgekehrt. Zwei Felder mit demselben Text nebeneinander wären zwei
   * Wahrheiten über dieselbe Beschreibung.
   *
   * @param description volle Markdown-Beschreibung; {@code null} in der Antwort von {@link
   *     #listByBoard(long, long)}
   * @param excerpt erste 200 Codepoints der rohen Beschreibung, nur in der Board-Liste gesetzt;
   *     sonst {@code null}
   */
  public record CardView(
      Long id,
      Long boardId,
      Long columnId,
      Integer number,
      String title,
      @Nullable String description,
      @Nullable String excerpt,
      int positionInColumn,
      boolean archived,
      @Nullable Instant movedToDoneAt,
      List<Integer> dependencies,
      CardType type,
      @Nullable Long parentId,
      @Nullable String shortcode,
      List<Long> assignees,
      @Nullable Instant dueDate,
      List<Long> labels,
      @Nullable Integer derivedFrom) {}

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
   * Treffer der projektübergreifenden Nummernsuche: die Karte plus die Angabe, wo sie liegt.
   *
   * @param card die gefundene Karte
   * @param projectId Projekt der Karte (immer gesetzt)
   * @param projectName Name des Projekts — das unterscheidende Merkmal, wenn dieselbe Nummer in
   *     mehreren Projekten existiert
   * @param boardId Board der Karte (immer gesetzt)
   * @param boardName Name des Boards (immer gesetzt)
   * @param boardArchived ob das Board archiviert ist (die Karte bleibt auffindbar, das Board ist
   *     über die normale Board-API aber nicht mehr ladbar)
   * @param columnId Spalte der Karte (immer gesetzt)
   * @param columnName Name der Spalte (immer gesetzt)
   */
  public record CardSearchHit(
      CardView card,
      Long projectId,
      String projectName,
      Long boardId,
      String boardName,
      boolean boardArchived,
      Long columnId,
      String columnName) {}

  /**
   * Schlanke Board-Projektion einer Karte oder eines Vorhabens — ohne Abhängigkeiten, Zuständige
   * und Labels. {@code epic} unterscheidet die beiden Ausprägungen, ohne den Kartentyp aus {@code
   * card.domain} nach außen zu geben.
   *
   * <p>{@code externalKey} ist der Idempotenz-Schlüssel eines Automatik-Ingests (#534). Er wird
   * seit #573 mitgeliefert, damit ein Importwerkzeug seine eigenen Karten wiedererkennt, ohne dafür
   * schreiben zu müssen; {@code null} für alles, was nicht aus einem Ingest stammt.
   */
  public record BoardItemView(
      long id,
      int number,
      String title,
      @Nullable String description,
      @Nullable Long columnId,
      int positionInColumn,
      boolean epic,
      @Nullable String externalKey,
      @Nullable Integer derivedFrom) {}

  /**
   * Eine Zeile des Herkunftsbaums (Issue #609). Die Liste kommt in Präorder — jede Wurzel
   * unmittelbar gefolgt von ihrem vollständigen Teilbaum —, damit sich der Baum allein aus {@code
   * depth} rekonstruieren lässt.
   *
   * @param derivedFrom Nummer des Vorfahren; {@code null} ohne Herkunft. Bei {@code broken} bleibt
   *     die Nummer gesetzt: Sie beschreibt den gespeicherten Zustand, nicht die Baumposition
   * @param depth 0-basiert; Wurzeln tragen 0
   * @param blocked eine board-interne Abhängigkeit liegt noch nicht in Done. Abgeleitet, nie
   *     gepflegt — externe Abhängigkeiten gehen nicht ein
   * @param dependencies board-interne Abhängigkeits-Nummern
   * @param externalDependencies Abhängigkeits-Nummern, die keine Karte dieses Boards trägt. Jede
   *     gespeicherte Nummer erscheint in genau einer der beiden Listen
   * @param externalOrigin die Herkunft zeigt auf eine Karte außerhalb dieses Boards; die Zeile
   *     erscheint dann als Wurzel, auch ohne Nachfahren
   * @param broken die Zeile hängt an einem Herkunftsring, der nur an der API vorbei entstehen kann
   * @param labels die Labels dieser Karte, die auf der Vorhaben-Kachel gezählt werden ({@code
   *     Label.countOnEpicTile}, Issue #659) — in der Reihenfolge aufsteigender Label-ID. Nur
   *     gezählte reisen mit: Was nicht gezählt wird, muss auch nicht übertragen werden
   */
  public record DerivationNodeView(
      int number,
      String title,
      CardType type,
      @Nullable Integer derivedFrom,
      int depth,
      boolean done,
      boolean blocked,
      List<Integer> dependencies,
      List<Integer> externalDependencies,
      boolean externalOrigin,
      boolean broken,
      List<LabelMarkView> labels) {}

  /**
   * Ein Label als Marke im Herkunftsbaum: Name als Text, Farbe als Chip-Fläche.
   *
   * <p>Beides und nicht nur der Name (Entscheidung Manne, 2026-08-31): Nur Namen zu übertragen wäre
   * schmaler, ließe aber Baum und Vorhaben-Kachel unterschiedlich aussehen.
   */
  public record LabelMarkView(String name, String color) {}

  /**
   * Vorhaben-Darstellung inkl. Fortschritt (zugehörige Karten gesamt / in Done).
   *
   * <p>Die beiden Nummern-Listen machen die gezählte Menge nachprüfbar: Ohne sie wäre eine
   * gestiegene Zahl für den Nutzer nicht nachvollziehbar, weil er die geerbten Karten nirgends
   * sieht (Issue #634 baut die Anzeige darauf).
   *
   * @param done Anzahl zugehöriger Karten in einer Done-Spalte
   * @param total Anzahl zugehöriger Karten; stets {@code memberNumbers.size()}
   * @param memberNumbers Nummern aller zugehörigen Karten, aufsteigend — direkt zugeordnete und
   *     über die Herkunft geerbte gemeinsam
   * @param rootNumbers Nummern der direkt über {@code parentId} zugeordneten Karten, aufsteigend.
   *     Stets eine Teilmenge von {@code memberNumbers}: Sie werden aus derselben Menge gefiltert
   *     und unterliegen damit denselben Regeln
   * @param requirementCardNumber Nummer der Anforderungskarte, oder {@code null}. Nullable, weil
   *     ein Vorhaben auch ohne Herkunftskette zum Gruppieren dienen darf (PO-Entscheidung in #636)
   *     — und weil eine zugeordnete Anforderung im Papierkorb liegen kann, dann ist sie hier nicht
   *     auflösbar. Ausdrücklich <b>nicht</b> 0 oder ein Platzhalter: 0 wäre eine gültige Nummer
   */
  public record EpicView(
      Long id,
      int number,
      String title,
      @Nullable String description,
      @Nullable String shortcode,
      int done,
      int total,
      List<Integer> memberNumbers,
      List<Integer> rootNumbers,
      @Nullable Integer requirementCardNumber) {}
}
