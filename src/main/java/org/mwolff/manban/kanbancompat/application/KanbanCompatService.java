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
import org.mwolff.manban.card.application.CardService.CardView;
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

  /** Feste Reihenfolge der Kanban-Spalten-Keys (spiegelt das tbx.mjs-Protokoll). */
  public static final List<String> COLUMNS =
      List.of(BACKLOG, "READY", "IN_PROGRESS", "IN_REVIEW", "DONE");

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
   * Nach Kanban-Spalte gruppierte, nicht-archivierte Items des gebundenen Boards (inkl. Epics).
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
                  c.epic() ? "epic" : "card",
                  labelsByCard.getOrDefault(c.id(), List.of())));
    }
    return grouped;
  }

  /**
   * Nimmt einen kanbancompat-Ingest entgegen und legt ihn als board-lose Pool-Idee im Projekt des
   * gebundenen Boards an; das Token-Board wird als Zielboard notiert ({@code target_board_id}).
   *
   * <p>Entscheidung B: Jeder board-token-Ingest landet bewusst im Projekt-Ideen-Pool statt direkt
   * im Board-Backlog, damit der Night-Modus (der aus <em>Ready</em> zieht) nichts autonom
   * abarbeitet, was nicht bewusst eingeplant wurde. Die Parameter {@code column} und {@code
   * ideaStored} sind dadurch gegenstandslos — sie bleiben aus Rückwärtskompatibilität im Request,
   * werden hier aber ignoriert. Zurückgegeben werden {@code id} und die sofort vergebene
   * projektweite {@code number} der neuen Pool-Idee (#402), damit CLI/Adapter direkt {@code #N}
   * zeigen können.
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
      boolean ideaStored) {
    long boardId = requireBound(principal);
    long projectId = boardService.requireProjectId(boardId);
    CardView v = cardService.createProjectIdea(principal.userId(), projectId, title, body, boardId);
    // Seit #402 vergibt createProjectIdea sofort eine Nummer; requireNonNull macht das fuer
    // NullAway explizit (CardView.number() ist @Nullable fuer Legacy-Ideen ohne Nummer).
    return new Created(v.id(), Objects.requireNonNull(v.number()));
  }

  /** Verschiebt ein Item des gebundenen Boards in die Ziel-Spalte an die Ziel-Position. */
  @Transactional
  public void move(KanbanPrincipal principal, long cardId, String column, int position) {
    long boardId = requireBound(principal);
    cardService.requireOnBoard(cardId, boardId);
    long columnId = columnIdForKey(boardId, column);
    cardService.move(principal.userId(), cardId, columnId, position);
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

  /** Epics des gebundenen Boards inkl. Fortschritt. */
  @Transactional(readOnly = true)
  public List<Epic> epics(KanbanPrincipal principal) {
    long boardId = requireBound(principal);
    return cardService.listEpics(principal.userId(), boardId).stream()
        .map(e -> new Epic(e.number(), e.title(), e.shortcode(), new Progress(e.total(), e.done())))
        .toList();
  }

  // --- interne Helfer -------------------------------------------------------

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
   * Board-Item; {@code column} ist der Kanban-Key, {@code type} ist "card" oder "epic". {@code
   * labels} enthält die zugeordneten Label-Namen in Board-Definitionsreihenfolge (leer, wenn
   * keine).
   */
  public record Item(
      Long id,
      int number,
      String title,
      @Nullable String body,
      String column,
      int position,
      String type,
      List<String> labels) {}

  /** Kommentar eines Items; {@code author} ist der Anzeigename des Autors zur Schreibzeit. */
  public record Comment(String author, String body, Instant createdAt) {}

  public record Created(long id, int number) {}

  public record Epic(int number, String title, @Nullable String shortcode, Progress progress) {}

  public record Progress(int total, int done) {}
}
