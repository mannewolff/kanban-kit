package org.mwolff.manban.kanbancompat.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.BoardItemView;
import org.mwolff.manban.card.application.LabelService;
import org.mwolff.manban.comment.application.CommentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compat-Schicht für die Toolbox-Kanban-API (tbx.mjs / board.mjs). Bildet das feste
 * 5-Spalten-Protokoll (BACKLOG/READY/IN_PROGRESS/IN_REVIEW/DONE) auf ein manban-Board ab und
 * operiert ausschließlich auf dem an das Token gebundenen Board (#44). Rechte laufen über die
 * bestehenden Services (CardService/CommentService → PermissionChecker).
 *
 * <p>Spalten-Mapping ausschließlich per Namensabgleich (Backlog/Ready/In Progress/In Review/Done).
 * Eine Spalte ohne kanonischen Namen trägt <em>keinen</em> Kanban-Key: Ihre Karten gelten als
 * „nicht bereit, nicht fertig" und werden unter BACKLOG gemeldet. Sonst würde eine eigene Spalte
 * allein durch ihre Position einen Zustand behaupten, den ihr niemand gegeben hat — eine Karte in
 * „Anstehend" wäre für die Automatisierung freigegeben, eine in „Zurückgestellt" erledigt (#697).
 */
// PMD.CouplingBetweenObjects: Übersetzungsschicht einer fremden API auf vier Fach-Fassaden (Board,
// Karte, Label, Kommentar) samt deren View-Typen und den Response-Records des Protokolls. Seit
// Issue #1001 kommt der Idempotenz-Guard dazu. Eine Aufteilung zerrisse ein festes Protokoll über
// mehrere Services, ohne dass eine einzige Übersetzung einfacher würde.
@SuppressWarnings("PMD.CouplingBetweenObjects")
@Service
public class KanbanCompatService {

  /** Kanban-Key der Backlog-Spalte; auch Fallback bei unbekannter Spalten-Zuordnung. */
  private static final String BACKLOG = "BACKLOG";

  /** Kanban-Key der Done-Spalte; beim Ingest ausgeschlossen (#569, E8b). */
  private static final String DONE = "DONE";

  /** Feste Reihenfolge der Kanban-Spalten-Keys (spiegelt das tbx.mjs-Protokoll). */
  public static final List<String> COLUMNS =
      List.of(BACKLOG, "READY", "IN_PROGRESS", "IN_REVIEW", DONE);

  private final BoardService boardService;
  private final CardService cardService;
  private final LabelService labelService;
  private final CommentService commentService;
  private final IdempotencyGuard idempotency;

  public KanbanCompatService(
      BoardService boardService,
      CardService cardService,
      LabelService labelService,
      CommentService commentService,
      IdempotencyGuard idempotency) {
    this.boardService = boardService;
    this.cardService = cardService;
    this.labelService = labelService;
    this.commentService = commentService;
    this.idempotency = idempotency;
  }

  /**
   * Nach Kanban-Spalte gruppierte, nicht-archivierte Items des gebundenen Boards (inkl. Vorhaben).
   *
   * <p>Karten im Ideen-Speicher bleiben ausgeschlossen (#434): Sie tragen weiterhin Board und
   * Spalte, sind in der Oberfläche aber ausgeblendet. Ohne diesen Filter meldete die Schnittstelle
   * sie als reguläre Karten ihrer Spalte — Kit und Nacht-Runner sahen dann Aufgaben, die für
   * Menschen auf dem Board nicht existieren.
   */
  @Transactional(readOnly = true)
  public Map<String, List<Item>> items(KanbanPrincipal principal) {
    long boardId = requireBound(principal);
    // listBoardItems prueft Board-Existenz und Projekt-Mitgliedschaft und filtert archivierte
    // sowie im Ideen-Speicher liegende Karten bereits im card-Modul heraus.
    List<BoardItemView> visible = cardService.listBoardItems(principal.userId(), boardId);

    Map<Long, String> keyByColumn = keyByColumn(boardId);
    Map<String, List<Item>> grouped = new LinkedHashMap<>();
    for (String key : COLUMNS) {
      grouped.put(key, new ArrayList<>());
    }

    Map<Long, List<String>> labelsByCard =
        labelService.namesByCard(boardId, visible.stream().map(BoardItemView::id).toList());

    for (BoardItemView c : visible) {
      String key = keyByColumn.getOrDefault(c.columnId(), BACKLOG);
      // grouped ist mit allen COLUMNS-Keys vorbelegt und key stammt aus COLUMNS;
      // requireNonNull macht das fuer NullAway explizit (Map.get liefert @Nullable).
      Objects.requireNonNull(grouped.get(key))
          .add(
              new Item(
                  c.id(),
                  c.number(),
                  c.title(),
                  c.description(),
                  key,
                  c.positionInColumn(),
                  // Protokoll, nicht Vokabular: Der Typ-Wert unten ist Teil der
                  // kanbancompat-Schnittstelle. Der Board-Adapter board.mjs des
                  // claude-workflow-kit filtert darauf und liest /api/kanban/epics; die
                  // Umbenennung auf "Vorhaben" betrifft nur die Oberflaeche, nie den Draht.
                  c.epic() ? "epic" : "card",
                  labelsByCard.getOrDefault(c.id(), List.of()),
                  c.externalKey(),
                  c.derivedFrom()));
    }
    return grouped;
  }

  /**
   * Nimmt einen kanbancompat-Ingest entgegen und legt ihn als Karte auf dem Board an, an das der
   * Zugang gebunden ist (Issue #1203, E8). Zurückgegeben werden {@code id} und die projektweite
   * {@code number} der Karte (#402), damit CLI/Adapter direkt {@code #N} zeigen können.
   *
   * <p><strong>Die Leitplanke gegen autonomes Abarbeiten bleibt, nur an anderer Stelle:</strong>
   * Ein Ingest ohne {@code direct} landet in der ersten Spalte des gebundenen Boards, nicht in
   * <em>Ready</em>. Der Nachtlauf zieht aus Ready, arbeitet also weiterhin nur ab, was ein Mensch
   * dorthin gestellt hat. Vorher lag eine solche Karte board-los im Ideen-Pool; mit dem Pool
   * entfällt dieser Weg (Plan #1199).
   *
   * <p>{@code direct} entscheidet nur noch, wie streng ein angegebenes {@code column} aufgelöst
   * wird — siehe {@link #zielSpalteId}. {@code ideaStored} bleibt im Request zulässig, hat aber
   * keine Wirkung mehr; die Begründung steht am Anfrage-DTO des Controllers.
   *
   * <p><strong>Idempotenz (Issue #1001, E7/E8):</strong> Mit einem {@code idempotencyKey} tritt die
   * Anlage je Projekt und Schlüssel genau einmal ein; jede Wiederholung bekommt dieselbe Antwort.
   * Ist ein {@code externalKey} gesetzt, greift der Idempotenz-Schlüssel gar nicht erst — der
   * fachliche Schlüssel mit unbegrenzter Lebensdauer hat Vorrang vor dem technischen mit kurzer.
   */
  @Transactional
  public Created create(
      KanbanPrincipal principal,
      String title,
      @Nullable String body,
      @Nullable String column,
      @Nullable String externalKey,
      boolean direct,
      @Nullable Integer number,
      @Nullable Integer derivedFrom,
      @Nullable String idempotencyKey) {
    long boardId = requireBound(principal);
    long projectId = boardService.requireProjectId(boardId);
    String key = normalizeExternalKey(externalKey);
    String idempotent = key == null ? normalizeIdempotencyKey(idempotencyKey) : null;
    if (idempotent == null) {
      return createNow(principal, boardId, title, body, column, key, direct, number, derivedFrom);
    }
    return idempotency.execute(
        projectId,
        idempotent,
        "POST /items",
        Created.class,
        () -> createNow(principal, boardId, title, body, column, key, direct, number, derivedFrom));
  }

  /** Die eigentliche Anlage, mit oder ohne Idempotenz-Schlüssel davor. */
  private Created createNow(
      KanbanPrincipal principal,
      long boardId,
      String title,
      @Nullable String body,
      @Nullable String column,
      @Nullable String key,
      boolean direct,
      @Nullable Integer number,
      @Nullable Integer derivedFrom) {
    requireImportPreconditions(number, key);
    // Die Spalte wird VOR dem Duplikat-Check in createDirect aufgelöst. Damit meldet ein
    // ungültiges `column` denselben Fehler, egal ob der Schlüssel schon eine Karte trifft —
    // sonst hinge die Fehlermeldung davon ab, ob zufällig schon eine existiert.
    long columnId = zielSpalteId(boardId, column, direct);
    CardService.IdeaCreation result =
        cardService.createDirect(
            principal.userId(),
            boardId,
            columnId,
            new CardService.DirectCard(title, body, key, number, derivedFrom));
    // Jede board-gebundene Karte trägt eine Nummer; requireNonNull macht das fuer NullAway
    // explizit (CardView.number() ist bis zum Pool-Rückbau noch @Nullable).
    return new Created(
        result.view().id(), Objects.requireNonNull(result.view().number()), result.created());
  }

  /**
   * Ersetzt die Abhängigkeiten einer Karte des gebundenen Projekts (Issue #566) — der Weg, auf dem
   * ein Migrations-Script die {@code Issue #N}-Verweise eines fremden Trackers überträgt.
   *
   * <p>Der Guard prüft das <em>Projekt</em> des gebundenen Boards, nicht das Board selbst: Ein
   * Migrations-Script verknüpft Karten, die es über mehrere Boards eines Projekts verteilt hat, und
   * der board-bezogene Guard von {@link #move} und {@link #comment} antwortete für die Karten der
   * übrigen Boards mit 404. Vor Issue #1203 war der Anlass derselbe, nur mit board-losen Pool-Ideen
   * als Fall.
   *
   * <p>Ersetzen-Semantik: Die übergebene Liste tritt an die Stelle der vorhandenen Verweise. Damit
   * ist ein wiederholter Aufruf mit derselben Liste folgenlos, ohne dass es eine Sonderbehandlung
   * für Dubletten bräuchte.
   */
  @Transactional
  public void replaceDependencies(
      KanbanPrincipal principal, long cardId, @Nullable List<Integer> dependsOn) {
    long boardId = requireBound(principal);
    long projectId = boardService.requireProjectId(boardId);
    cardService.replaceDependenciesFromIngest(principal.userId(), cardId, projectId, dependsOn);
  }

  /**
   * Zielspalte des Ingests (#569, seit #1203 für jeden Ingest).
   *
   * <p>Fehlt {@code column} (oder ist es JSON-{@code null}), bleibt es bei der ersten Spalte — das
   * ist das Verhalten seit #535, und bestehende Aufrufer wie der Sonar-Sync senden keines.
   *
   * <p><strong>{@code direct} entscheidet nur noch die Strenge (E8a).</strong> Mit {@code
   * direct=true} bleibt ein nicht auflösbarer Schlüssel — unbekannter Key, leerer String, oder ein
   * Key ohne passende Spalte auf diesem Board — ein Requestfehler, wie seit #569. Ohne {@code
   * direct} greift stattdessen die erste Spalte: {@code .claude/kit/board.mjs} sendet {@code
   * column: "BACKLOG"} bedingungslos und lässt für eine Idee nur {@code direct} weg. Ein 400 träfe
   * damit genau die ältere Kit-Version, der die Zusage gilt, weiter anlegen zu können — auf einem
   * Board ohne Backlog-Spalte, an dem der Aufrufer nichts ändern kann.
   *
   * <p><strong>DONE ist ausgeschlossen</strong>, auf beiden Wegen: Ein Werkzeug von außen legt
   * nichts an, was bereits erledigt ist — dass eine Karte fertig ist, stellt ein Mensch auf dem
   * Board fest. Das ist eine fachliche Regel und kein Auflösungsfehler, deshalb führt sie auch ohne
   * {@code direct} zur Ablehnung statt zum Rückfall auf die erste Spalte (E8b).
   */
  private long zielSpalteId(long boardId, @Nullable String column, boolean direct) {
    if (column == null) {
      return boardService.firstColumn(boardId).id();
    }
    if (DONE.equals(normalizeColumnKey(column))) {
      throw new InvalidKanbanColumnException(
          "Karten koennen nicht in DONE angelegt werden — dass eine Karte fertig ist, stellt ein"
              + " Mensch auf dem Board fest.");
    }
    if (direct) {
      return columnIdForKey(boardId, column);
    }
    return findColumnIdForKey(boardId, column)
        .orElseGet(() -> boardService.firstColumn(boardId).id());
  }

  /**
   * Die Pflicht, die an einer vorgegebenen Nummer hängt (#565).
   *
   * <p>Der {@code externalKey} ist Pflicht, weil die Import-Vorbedingung („keine Karte ohne
   * Schlüssel") sich sonst selbst aushebelt: Der erste Aufruf ohne Schlüssel legt eine
   * schlüssellose Karte an, und ab dem zweiten lehnt die Vorbedingung denselben Import ab.
   *
   * <p>Die frühere zweite Pflicht ({@code direct=true}) ist mit dem Ideen-Pool entfallen (#1203,
   * E8): Es gibt keinen Anlegeweg mehr, der eine Nummer nicht vergeben könnte.
   *
   * <p>Es ist ein Requestfehler (400), kein Zustandskonflikt — derselbe Aufruf ist zu keinem
   * Zeitpunkt und gegen kein Projekt gültig.
   */
  private static void requireImportPreconditions(
      @Nullable Integer number, @Nullable String normalizedKey) {
    if (number == null) {
      return;
    }
    if (normalizedKey == null) {
      throw new InvalidNumberedIngestException(
          "Eine vorgegebene Nummer verlangt einen externalKey — sonst blockiert der Import sich"
              + " nach dem ersten Aufruf selbst.");
    }
  }

  /** Leer oder nur Leerzeichen gilt als „kein Schlüssel", sonst getrimmt — wie beim externalKey. */
  private static @Nullable String normalizeIdempotencyKey(@Nullable String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return null;
    }
    return idempotencyKey.trim();
  }

  /**
   * Normalisiert den Idempotenz-Schlüssel (#534): getrimmt, auf die Spaltenlänge (100) gekappt,
   * leer wird zu {@code null} (kein Schlüssel).
   */
  private static @Nullable String normalizeExternalKey(@Nullable String externalKey) {
    if (externalKey == null || externalKey.isBlank()) {
      return null;
    }
    String trimmed = externalKey.trim();
    // Ohne Grenz-Verzweigung (substring(0, length) liefert this): eine <=-Bedingung wäre an der
    // exakten Grenze ein äquivalenter, untötbarer PIT-Mutant.
    return trimmed.substring(0, Math.min(trimmed.length(), 100));
  }

  /** Verschiebt ein Item des gebundenen Boards in die Ziel-Spalte an die Ziel-Position. */
  @Transactional
  public void move(KanbanPrincipal principal, long cardId, String column, int position) {
    long boardId = requireBound(principal);
    cardService.requireOnBoard(cardId, boardId);
    long columnId = columnIdForKey(boardId, column);
    cardService.move(principal.userId(), cardId, columnId, position);
  }

  /**
   * Ersetzt Titel und Rumpf eines Items des gebundenen Boards (#571) — der Schreibweg, über den das
   * claude-workflow-kit den geschärften Issue-Text zurückschreibt.
   *
   * <p>Geht bewusst über {@link CardService#updateContent} statt über das Voll-Update: Sonst
   * verlören Karten bei jedem Body-Update ihre Vorhaben-Zuordnung und ihr Fälligkeitsdatum und
   * Vorhaben ihr Kürzel, weil dieser Aufrufer diese Felder gar nicht kennt.
   *
   * <p>Reichweite wie bei {@link #move} und {@link #comment}: nur Karten und Vorhaben des
   * gebundenen Boards. Board-lose Pool-Ideen und Karten im Ideen-Speicher sind über {@code
   * requireOnBoard} ausgeschlossen und antworten mit 404.
   */
  @Transactional
  public Item update(KanbanPrincipal principal, long cardId, String title, @Nullable String body) {
    long boardId = requireBound(principal);
    cardService.requireOnBoard(cardId, boardId);
    return item(boardId, cardService.updateContent(principal.userId(), cardId, title, body));
  }

  /**
   * Ergänzt an einem Item des gebundenen Boards genau ein Label (#574) — der Weg, auf dem das
   * claude-workflow-kit sein Routing-Label {@code kit:nightrun} setzt.
   *
   * <p>Reichweite wie bei {@link #move} und {@link #comment}: Der Board-Guard der card-Fassade
   * schließt Karten anderer Boards, board-lose Pool-Ideen und den Ideen-Speicher mit 404 aus. Das
   * gilt auch innerhalb desselben Projekts, wo die Projektberechtigung allein nicht schützt.
   *
   * <p>Alle übrigen Labels der Karte bleiben unverändert; ein bereits gesetztes Label erneut zu
   * setzen ist Erfolg (Einzelheiten in {@code LabelService.addToCard}).
   */
  @Transactional
  public void addLabel(KanbanPrincipal principal, long cardId, String name) {
    long boardId = requireBound(principal);
    cardService.requireOnBoard(cardId, boardId);
    labelService.addToCard(principal.userId(), cardId, name);
  }

  /** Gegenstück zu {@link #addLabel}: entfernt genau ein Label, alle übrigen bleiben stehen. */
  @Transactional
  public void removeLabel(KanbanPrincipal principal, long cardId, String name) {
    long boardId = requireBound(principal);
    cardService.requireOnBoard(cardId, boardId);
    labelService.removeFromCard(principal.userId(), cardId, name);
  }

  /**
   * Kommentiert ein Item des gebundenen Boards. Mit einem {@code idempotencyKey} entsteht der
   * Kommentar je Projekt und Schlüssel genau einmal (Issue #1001); zwei verschiedene Schlüssel mit
   * gleichem Text ergeben bewusst zwei Kommentare.
   */
  @Transactional
  public void comment(
      KanbanPrincipal principal, long cardId, String body, @Nullable String idempotencyKey) {
    long boardId = requireBound(principal);
    cardService.requireOnBoard(cardId, boardId);
    String idempotent = normalizeIdempotencyKey(idempotencyKey);
    if (idempotent == null) {
      commentService.create(principal.userId(), cardId, body);
      return;
    }
    idempotency.execute(
        boardService.requireProjectId(boardId),
        idempotent,
        "POST /items/" + cardId + "/comments",
        () -> commentService.create(principal.userId(), cardId, body));
  }

  /**
   * Kommentare eines Items des gebundenen Boards in chronologischer Reihenfolge (#448).
   *
   * <p>Gegenstück zu {@link #comment}: Ohne diesen Lesepfad waren über die Schnittstelle
   * geschriebene Kommentare (Abschlussberichte, Review-Befunde) für jedes Werkzeug unsichtbar, das
   * ausschließlich über kanbancompat liest. Die Zugriffskontrolle läuft wie bei den übrigen
   * Endpoints über den Board-Guard der card-Fassade und die Mitgliedschaftsprüfung der
   * Kommentar-Fassade — ein Nichtmitglied bekommt dadurch 404 statt 403.
   */
  @Transactional(readOnly = true)
  public List<Comment> listComments(KanbanPrincipal principal, long cardId) {
    long boardId = requireBound(principal);
    cardService.requireOnBoard(cardId, boardId);
    return commentService.list(principal.userId(), cardId).stream()
        .map(c -> new Comment(c.authorName(), c.body(), c.createdAt()))
        .toList();
  }

  /**
   * Aktivitätsverlauf eines Items des gebundenen Boards in chronologischer Reihenfolge (#876).
   *
   * <p>Dieselbe Auskunft wie {@code GET /api/cards/{id}/activity}, nur innerhalb der Board-Grenze:
   * Werkzeuge, die ausschließlich über ein board-gebundenes Token arbeiten (Abdeckungs-Gate des
   * Nacht-Runners), brauchen den Verlauf als Quelle des Anlagedatums.
   *
   * <p>Der Weg über die Fassade {@link CardService#listActivityViews} ist die einzige zulässige
   * Kante: Die Domänenschicht des card-Moduls ist modulintern (ArchUnit-Regel), dieses Modul darf
   * ihre Typen nicht importieren. Die Zugriffskontrolle läuft wie bei {@link #listComments} über
   * den Board-Guard der card-Fassade und deren Mitgliedschaftsprüfung — ein Nichtmitglied bekommt
   * dadurch 404 statt 403.
   */
  @Transactional(readOnly = true)
  public List<Activity> listActivity(KanbanPrincipal principal, long cardId) {
    long boardId = requireBound(principal);
    cardService.requireOnBoard(cardId, boardId);
    return cardService.listActivityViews(principal.userId(), cardId).stream()
        .map(
            a ->
                new Activity(
                    a.id(),
                    a.actorUserId(),
                    a.type(),
                    a.detail(),
                    a.createdAt(),
                    a.origin(),
                    a.tokenName(),
                    a.agent()))
        .toList();
  }

  /** Vorhaben des gebundenen Boards inkl. Fortschritt. */
  @Transactional(readOnly = true)
  public List<Epic> epics(KanbanPrincipal principal) {
    long boardId = requireBound(principal);
    return cardService.listEpics(principal.userId(), boardId).stream()
        .map(e -> new Epic(e.number(), e.title(), e.shortcode(), new Progress(e.total(), e.done())))
        .toList();
  }

  // --- interne Helfer -------------------------------------------------------

  /**
   * Baut die Item-Form aus einer aktualisierten Karte — dieselben Felder wie in {@link #items},
   * damit Schreib- und Leseantwort nicht auseinanderlaufen.
   */
  private Item item(long boardId, BoardItemView card) {
    return new Item(
        card.id(),
        card.number(),
        card.title(),
        card.description(),
        keyByColumn(boardId).getOrDefault(card.columnId(), BACKLOG),
        card.positionInColumn(),
        // Protokoll, nicht Vokabular — siehe die Erlaeuterung an der Board-Liste oben.
        card.epic() ? "epic" : "card",
        labelService.namesByCard(boardId, List.of(card.id())).getOrDefault(card.id(), List.of()),
        card.externalKey(),
        card.derivedFrom());
  }

  private long requireBound(@Nullable KanbanPrincipal principal) {
    if (principal == null || !principal.isBound()) {
      throw new TokenNotBoundException();
    }
    // isBound() garantiert die Bindung; requireNonNull macht das fuer NullAway explizit.
    return Objects.requireNonNull(principal.boardId());
  }

  /** Bildet Board-Spalten mit kanonischem Namen auf ihren Kanban-Key ab. */
  private Map<Long, String> keyByColumn(long boardId) {
    Map<Long, String> map = new LinkedHashMap<>();
    for (ColumnView c : boardService.listColumns(boardId)) {
      canonicalKey(c.name()).ifPresent(key -> map.put(c.id(), key));
    }
    return map;
  }

  /** Strenge Auflösung: jeder nicht auflösbare Schlüssel ist ein Requestfehler. */
  private long columnIdForKey(long boardId, @Nullable String key) {
    String wanted = normalizeColumnKey(key);
    if (!COLUMNS.contains(wanted)) {
      throw new InvalidKanbanColumnException("Unbekannte Kanban-Spalte: " + key);
    }
    return columnWithKey(boardId, wanted)
        .orElseThrow(
            () ->
                new InvalidKanbanColumnException(
                    "Board " + boardId + " hat keine Spalte für " + wanted));
  }

  /**
   * Nachsichtige Auflösung für den Ingest ohne {@code direct} (E8a): leer, wenn der Schlüssel
   * unbekannt ist <em>oder</em> das Board keine Spalte dafür hat. Der Aufrufer entscheidet, was
   * dann gilt — hier ist es die erste Spalte, siehe {@link #zielSpalteId}.
   */
  private Optional<Long> findColumnIdForKey(long boardId, String key) {
    String wanted = normalizeColumnKey(key);
    if (!COLUMNS.contains(wanted)) {
      return Optional.empty();
    }
    return columnWithKey(boardId, wanted);
  }

  /** Die erste Spalte des Boards, die diesen Kanban-Key trägt. */
  private Optional<Long> columnWithKey(long boardId, String wanted) {
    return keyByColumn(boardId).entrySet().stream()
        .filter(e -> e.getValue().equals(wanted))
        .map(Map.Entry::getKey)
        .findFirst();
  }

  /** Normalisiert einen eingehenden Spalten-Schlüssel; {@code null} und leer werden zu "". */
  private static String normalizeColumnKey(@Nullable String key) {
    return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
  }

  /** Normalisierter Namensabgleich auf einen Kanban-Key; leer, wenn kein Treffer. */
  static Optional<String> canonicalKey(@Nullable String columnName) {
    if (columnName == null) {
      return Optional.empty();
    }
    String n = columnName.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    return switch (n) {
      case "backlog" -> Optional.of(BACKLOG);
      case "ready" -> Optional.of("READY");
      case "inprogress" -> Optional.of("IN_PROGRESS");
      case "inreview" -> Optional.of("IN_REVIEW");
      case "done" -> Optional.of("DONE");
      default -> Optional.empty();
    };
  }

  // --- Response-Formen (spiegeln das tbx.mjs-Protokoll) ---------------------

  /**
   * Board-Item; {@code column} ist der Kanban-Key, {@code type} ist "card" oder "epic" — das
   * Protokoll-Literal bleibt auch nach der Umbenennung auf „Vorhaben" unverändert. {@code labels}
   * enthält die zugeordneten Label-Namen in Board-Definitionsreihenfolge (leer, wenn keine).
   */
  public record Item(
      Long id,
      int number,
      String title,
      @Nullable String body,
      String column,
      int position,
      String type,
      List<String> labels,
      @Nullable String externalKey,
      @Nullable Integer derivedFrom) {}

  /** Kommentar eines Items; {@code author} ist der Anzeigename des Autors zur Schreibzeit. */
  public record Comment(String author, String body, Instant createdAt) {}

  /**
   * Ein Eintrag des Aktivitätsverlaufs eines Items (#876). Feldgleich mit der Antwort von {@code
   * GET /api/cards/{id}/activity} — der Endpunkt ist deren Ersatz innerhalb der Board-Grenze, und
   * eine abweichende Form wäre für jeden Aufrufer eine zweite Wahrheit über dieselbe Auskunft.
   */
  public record Activity(
      @Nullable Long id,
      @Nullable Long actorUserId,
      String type,
      String detail,
      Instant createdAt,
      @Nullable String origin,
      @Nullable String tokenName,
      @Nullable String agent) {}

  /**
   * Ergebnis des Ingests: {@code created=false}, wenn ein {@code externalKey} auf eine bereits
   * existierende Karte traf und nichts angelegt wurde (#534) — {@code id}/{@code number} zeigen
   * dann die bestehende Karte.
   */
  public record Created(long id, int number, boolean created) {}

  public record Epic(int number, String title, @Nullable String shortcode, Progress progress) {}

  public record Progress(int total, int done) {}
}
