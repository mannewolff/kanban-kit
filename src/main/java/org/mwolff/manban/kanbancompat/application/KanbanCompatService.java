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
 * <p>Spalten-Mapping: primär per Namensabgleich (Backlog/Ready/In Progress/In Review/Done), sonst
 * positionsbasiert (i-te Spalte → i-ter Kanban-Key). Das Dogfood-Board nutzt die
 * Standard-5-Spalten, für die das Mapping 1:1 ist.
 */
@Service
public class KanbanCompatService {

  /** Kanban-Key der Backlog-Spalte; auch Fallback bei unbekannter Spalten-Zuordnung. */
  private static final String BACKLOG = "BACKLOG";

  /** Kanban-Key der Done-Spalte; beim direct-Ingest ausgeschlossen (#569). */
  private static final String DONE = "DONE";

  /** Feste Reihenfolge der Kanban-Spalten-Keys (spiegelt das tbx.mjs-Protokoll). */
  public static final List<String> COLUMNS =
      List.of(BACKLOG, "READY", "IN_PROGRESS", "IN_REVIEW", DONE);

  private final BoardService boardService;
  private final CardService cardService;
  private final LabelService labelService;
  private final CommentService commentService;

  public KanbanCompatService(
      BoardService boardService,
      CardService cardService,
      LabelService labelService,
      CommentService commentService) {
    this.boardService = boardService;
    this.cardService = cardService;
    this.labelService = labelService;
    this.commentService = commentService;
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
                  c.externalKey()));
    }
    return grouped;
  }

  /**
   * Nimmt einen kanbancompat-Ingest entgegen und legt ihn als board-lose Pool-Idee im Projekt des
   * gebundenen Boards an; das Token-Board wird als Zielboard notiert ({@code target_board_id}).
   *
   * <p>Entscheidung B: Jeder board-token-Ingest landet bewusst im Projekt-Ideen-Pool statt direkt
   * im Board-Backlog, damit der Night-Modus (der aus <em>Ready</em> zieht) nichts autonom
   * abarbeitet, was nicht bewusst eingeplant wurde. Im Pool-Zweig sind {@code column} und {@code
   * ideaStored} gegenstandslos — sie bleiben aus Rückwärtskompatibilität im Request, werden dort
   * aber ignoriert. Mit {@code direct} bestimmt {@code column} seit #569 die Zielspalte; {@code
   * ideaStored} bleibt auch dann wirkungslos. Zurückgegeben werden {@code id} und die sofort
   * vergebene projektweite {@code number} der neuen Pool-Idee (#402), damit CLI/Adapter direkt
   * {@code #N} zeigen können.
   *
   * <p>Die zurückgegebene {@code id} taugt bewusst <em>nicht</em> als Kommentar-Ziel (#472): Eine
   * board-lose Pool-Idee liegt auf keinem Board, {@code GET/POST /items/{id}/comments} verlangt
   * aber genau das und antwortet für sie mit 404. Erst das Einplanen auf ein Board macht die Karte
   * kommentierbar. Für Aufrufer ist das folgenlos, weil die IDs dort aus {@link #items} stammen —
   * und die Liste enthält nur eingeplante Karten.
   */
  @Transactional
  public Created create(
      KanbanPrincipal principal,
      String title,
      @Nullable String body,
      @Nullable String column,
      boolean ideaStored,
      @Nullable String externalKey,
      boolean direct,
      @Nullable Integer number,
      @Nullable Integer derivedFrom) {
    long boardId = requireBound(principal);
    long projectId = boardService.requireProjectId(boardId);
    String key = normalizeExternalKey(externalKey);
    requireImportPreconditions(number, key, direct);
    CardService.IdeaCreation result;
    if (direct) {
      // Opt-in-Board-Routing (#535): für dedizierte Sammel-Boards (z. B. Sonar-Findings) landet
      // die Karte direkt auf dem Token-Board statt im Pool — seit #569 in der angeforderten
      // Spalte, ohne Angabe weiterhin in der ersten. Die Pool-Leitplanke aus Entscheidung B
      // bleibt der Default: was von außen kommt, plant sonst ein Mensch ein.
      //
      // Die Spalte wird VOR dem Duplikat-Check in createDirect aufgelöst. Damit meldet ein
      // ungültiges `column` denselben Fehler, egal ob der Schlüssel schon eine Karte trifft —
      // sonst hinge die Fehlermeldung davon ab, ob zufällig schon eine existiert.
      long columnId = directColumnId(boardId, column);
      result =
          cardService.createDirect(
              principal.userId(), boardId, columnId, title, body, key, number, derivedFrom);
    } else {
      result =
          cardService.createProjectIdea(
              principal.userId(), projectId, title, body, boardId, key, derivedFrom);
    }
    // Seit #402 vergibt createProjectIdea sofort eine Nummer; requireNonNull macht das fuer
    // NullAway explizit (CardView.number() ist @Nullable fuer Legacy-Ideen ohne Nummer).
    return new Created(
        result.view().id(), Objects.requireNonNull(result.view().number()), result.created());
  }

  /**
   * Ersetzt die Abhängigkeiten einer Karte des gebundenen Projekts (Issue #566) — der Weg, auf dem
   * ein Migrations-Script die {@code Issue #N}-Verweise eines fremden Trackers überträgt.
   *
   * <p>Der Guard prüft das <em>Projekt</em> des gebundenen Boards, nicht das Board selbst. Ein
   * Ingest ohne {@code direct} legt board-lose Pool-Ideen an (Entscheidung B); der board-bezogene
   * Guard von {@link #move} und {@link #comment} antwortet für sie mit 404, und genau diese Karten
   * will das Script gleich danach verknüpfen.
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
   * Zielspalte für den direct-Ingest (#569).
   *
   * <p>Fehlt {@code column} (oder ist es JSON-{@code null}), bleibt es bei der ersten Spalte — das
   * ist das Verhalten seit #535, und bestehende Aufrufer wie der Sonar-Sync senden keines. Ein
   * leerer oder nur aus Leerzeichen bestehender String ist dagegen ein <em>angegebener</em>,
   * ungültiger Schlüssel und wird abgelehnt; {@link #columnIdForKey} erledigt das mit.
   *
   * <p><strong>DONE ist ausgeschlossen.</strong> {@code doCreate} setzt {@code movedToDoneAt} nicht
   * — diesen Zeitstempel vergibt allein {@code CardService.move} beim Eintritt in eine Done-Spalte,
   * und die Done-Retention archiviert ausschließlich darüber ({@code findArchivableDoneCards}
   * verlangt {@code movedToDoneAt is not null}). Eine direkt in DONE angelegte Karte fiele
   * dauerhaft aus der Aufbewahrung, ohne dass der Grund sichtbar wäre; außerdem umginge sie die
   * Zykluszeit-Messung. Der reale Bedarf (Ready) ist davon nicht berührt.
   */
  private long directColumnId(long boardId, @Nullable String column) {
    if (column == null) {
      return boardService.firstColumn(boardId).id();
    }
    if (DONE.equals(column.trim().toUpperCase(Locale.ROOT))) {
      throw new InvalidKanbanColumnException(
          "Karten koennen nicht direkt in DONE angelegt werden — die Done-Aufbewahrung erfasst nur"
              + " Karten, die dorthin verschoben wurden.");
    }
    return columnIdForKey(boardId, column);
  }

  /**
   * Beide Pflichten, die an einer vorgegebenen Nummer hängen (#565).
   *
   * <p>{@code direct} ist Pflicht, weil der Ideen-Pool für ungesichtete Rohanforderungen gedacht
   * ist — eine migrierte Karte hat ihren Platz bereits.
   *
   * <p>Der {@code externalKey} ist Pflicht, weil die Import-Vorbedingung („keine Karte ohne
   * Schlüssel") sich sonst selbst aushebelt: Der erste Aufruf ohne Schlüssel legt eine
   * schlüssellose Karte an, und ab dem zweiten lehnt die Vorbedingung denselben Import ab.
   *
   * <p>Beides sind Requestfehler (400), keine Zustandskonflikte — derselbe Aufruf ist zu keinem
   * Zeitpunkt und gegen kein Projekt gültig.
   */
  private static void requireImportPreconditions(
      @Nullable Integer number, @Nullable String normalizedKey, boolean direct) {
    if (number == null) {
      return;
    }
    if (!direct) {
      throw new InvalidNumberedIngestException(
          "Eine vorgegebene Nummer verlangt direct=true — Pool-Ideen werden nicht nummeriert"
              + " uebernommen.");
    }
    if (normalizedKey == null) {
      throw new InvalidNumberedIngestException(
          "Eine vorgegebene Nummer verlangt einen externalKey — sonst blockiert der Import sich"
              + " nach dem ersten Aufruf selbst.");
    }
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

  /** Kommentiert ein Item des gebundenen Boards. */
  @Transactional
  public void comment(KanbanPrincipal principal, long cardId, String body) {
    long boardId = requireBound(principal);
    cardService.requireOnBoard(cardId, boardId);
    commentService.create(principal.userId(), cardId, body);
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
        card.externalKey());
  }

  private long requireBound(@Nullable KanbanPrincipal principal) {
    if (principal == null || !principal.isBound()) {
      throw new TokenNotBoundException();
    }
    // isBound() garantiert die Bindung; requireNonNull macht das fuer NullAway explizit.
    return Objects.requireNonNull(principal.boardId());
  }

  /** Bildet jede Board-Spalte auf einen Kanban-Key ab: Name zuerst, sonst Position. */
  private Map<Long, String> keyByColumn(long boardId) {
    List<ColumnView> ordered = boardService.listColumns(boardId);
    Map<Long, String> map = new LinkedHashMap<>();
    for (int i = 0; i < ordered.size(); i++) {
      ColumnView c = ordered.get(i);
      String fallback = COLUMNS.get(Math.min(i, COLUMNS.size() - 1));
      map.put(c.id(), canonicalKey(c.name()).orElse(fallback));
    }
    return map;
  }

  private long columnIdForKey(long boardId, @Nullable String key) {
    String wanted = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
    if (!COLUMNS.contains(wanted)) {
      throw new InvalidKanbanColumnException("Unbekannte Kanban-Spalte: " + key);
    }
    Map<Long, String> keyByColumn = keyByColumn(boardId);
    return keyByColumn.entrySet().stream()
        .filter(e -> e.getValue().equals(wanted))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(
            () ->
                new InvalidKanbanColumnException(
                    "Board " + boardId + " hat keine Spalte für " + wanted));
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
      @Nullable String externalKey) {}

  /** Kommentar eines Items; {@code author} ist der Anzeigename des Autors zur Schreibzeit. */
  public record Comment(String author, String body, Instant createdAt) {}

  /**
   * Ergebnis des Ingests: {@code created=false}, wenn ein {@code externalKey} auf eine bereits
   * existierende Karte traf und nichts angelegt wurde (#534) — {@code id}/{@code number} zeigen
   * dann die bestehende Karte.
   */
  public record Created(long id, int number, boolean created) {}

  public record Epic(int number, String title, @Nullable String shortcode, Progress progress) {}

  public record Progress(int total, int done) {}
}
