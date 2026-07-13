package org.mwolff.manban.kanbancompat.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.CardView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.project.application.PermissionChecker;
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

  private final CardRepository cards;
  private final BoardColumnRepository boardColumns;
  private final BoardRepository boards;
  private final CardService cardService;
  private final CommentService commentService;
  private final PermissionChecker permissions;

  public KanbanCompatService(
      CardRepository cards,
      BoardColumnRepository boardColumns,
      BoardRepository boards,
      CardService cardService,
      CommentService commentService,
      PermissionChecker permissions) {
    this.cards = cards;
    this.boardColumns = boardColumns;
    this.boards = boards;
    this.cardService = cardService;
    this.commentService = commentService;
    this.permissions = permissions;
  }

  /** Nach Kanban-Spalte gruppierte, nicht-archivierte Items des gebundenen Boards (inkl. Epics). */
  @Transactional(readOnly = true)
  public Map<String, List<Item>> items(KanbanPrincipal principal) {
    long boardId = requireBound(principal);
    Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
    permissions.requireMembership(principal.userId(), board.projectId());

    Map<Long, String> keyByColumn = keyByColumn(boardId);
    Map<String, List<Item>> grouped = new LinkedHashMap<>();
    for (String key : COLUMNS) {
      grouped.put(key, new ArrayList<>());
    }
    cards.findByBoardId(boardId).stream()
        .filter(c -> !c.archived())
        .sorted(Comparator.comparingInt(Card::positionInColumn))
        .forEach(
            c -> {
              String key = keyByColumn.getOrDefault(c.columnId(), BACKLOG);
              // grouped ist mit allen COLUMNS-Keys vorbelegt und key stammt aus COLUMNS;
              // requireNonNull macht das fuer NullAway explizit (Map.get liefert @Nullable).
              Objects.requireNonNull(grouped.get(key))
                  .add(
                      new Item(
                          c.requireId(),
                          c.number(),
                          c.title(),
                          c.description(),
                          key,
                          c.positionInColumn(),
                          c.type() == CardType.EPIC ? "epic" : "card"));
            });
    return grouped;
  }

  /** Legt ein Item in der angegebenen (Default: BACKLOG) Spalte des gebundenen Boards an. */
  @Transactional
  public Created create(
      KanbanPrincipal principal, String title, @Nullable String body, @Nullable String column) {
    long boardId = requireBound(principal);
    long columnId = columnIdForKey(boardId, column == null || column.isBlank() ? BACKLOG : column);
    CardView v = cardService.create(principal.userId(), boardId, columnId, title, body, null, null);
    return new Created(v.number());
  }

  /** Verschiebt ein Item des gebundenen Boards in die Ziel-Spalte an die Ziel-Position. */
  @Transactional
  public void move(KanbanPrincipal principal, long cardId, String column, int position) {
    long boardId = requireBound(principal);
    requireCardOnBoard(cardId, boardId);
    long columnId = columnIdForKey(boardId, column);
    cardService.move(principal.userId(), cardId, columnId, position);
  }

  /** Kommentiert ein Item des gebundenen Boards. */
  @Transactional
  public void comment(KanbanPrincipal principal, long cardId, String body) {
    long boardId = requireBound(principal);
    requireCardOnBoard(cardId, boardId);
    commentService.create(principal.userId(), cardId, body);
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

  /** Sichert die Token-Bindung ab: die Karte muss auf dem gebundenen Board liegen (sonst 404). */
  private void requireCardOnBoard(long cardId, long boardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (!Long.valueOf(boardId).equals(card.boardId())) {
      throw new CardNotFoundException();
    }
  }

  /** Bildet jede Board-Spalte auf einen Kanban-Key ab: Name zuerst, sonst Position. */
  private Map<Long, String> keyByColumn(long boardId) {
    List<BoardColumn> ordered =
        boardColumns.findByBoardId(boardId).stream()
            .sorted(Comparator.comparingInt(BoardColumn::position))
            .toList();
    Map<Long, String> map = new LinkedHashMap<>();
    for (int i = 0; i < ordered.size(); i++) {
      BoardColumn c = ordered.get(i);
      String fallback = COLUMNS.get(Math.min(i, COLUMNS.size() - 1));
      map.put(c.requireId(), canonicalKey(c.name()).orElse(fallback));
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

  /** Board-Item; {@code column} ist der Kanban-Key, {@code type} ist "card" oder "epic". */
  public record Item(
      Long id,
      int number,
      String title,
      @Nullable String body,
      String column,
      int position,
      String type) {}

  public record Created(int number) {}

  public record Epic(int number, String title, @Nullable String shortcode, Progress progress) {}

  public record Progress(int total, int done) {}
}
