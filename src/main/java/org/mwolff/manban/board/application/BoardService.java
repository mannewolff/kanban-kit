package org.mwolff.manban.board.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Board- und Spalten-Use-Cases. Rechte laufen über den {@link PermissionChecker}:
 * BOARD_CREATE (anlegen), BOARD_UPDATE (umbenennen/Spalten), BOARD_DELETE (löschen).
 * Lesezugriffe verlangen nur Projekt-Mitgliedschaft.
 */
@Service
public class BoardService {

    private static final List<String> DEFAULT_COLUMNS =
            List.of("Backlog", "Ready", "In Progress", "In Review", "Done");

    private final BoardRepository boards;
    private final BoardColumnRepository columns;
    private final ColumnCardCounter cardCounter;
    private final PermissionChecker permissions;
    private final Clock clock;

    public BoardService(BoardRepository boards, BoardColumnRepository columns,
                        ColumnCardCounter cardCounter, PermissionChecker permissions, Clock clock) {
        this.boards = boards;
        this.columns = columns;
        this.cardCounter = cardCounter;
        this.permissions = permissions;
        this.clock = clock;
    }

    @Transactional
    public BoardView createBoard(long userId, long projectId, String name) {
        permissions.require(userId, projectId, Permission.BOARD_CREATE);
        Board board = boards.save(new Board(null, projectId, name.trim(), clock.instant()));
        for (int i = 0; i < DEFAULT_COLUMNS.size(); i++) {
            columns.save(new BoardColumn(null, board.id(), DEFAULT_COLUMNS.get(i), i, null));
        }
        return view(board);
    }

    @Transactional(readOnly = true)
    public List<BoardView> listBoards(long userId, long projectId) {
        permissions.requireMembership(userId, projectId);
        return boards.findByProjectId(projectId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public BoardView getBoard(long userId, long boardId) {
        Board board = requireBoard(boardId);
        permissions.requireMembership(userId, board.projectId());
        return view(board);
    }

    @Transactional
    public BoardView renameBoard(long userId, long boardId, String name) {
        Board board = requireBoard(boardId);
        permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);
        return view(boards.save(board.withName(name.trim())));
    }

    @Transactional
    public void deleteBoard(long userId, long boardId) {
        Board board = requireBoard(boardId);
        permissions.require(userId, board.projectId(), Permission.BOARD_DELETE);
        boards.deleteById(boardId);
    }

    @Transactional
    public ColumnView addColumn(long userId, long boardId, String name, Integer wipLimit) {
        Board board = requireBoard(boardId);
        permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);
        int nextPosition = columns.findByBoardId(boardId).stream()
                .mapToInt(BoardColumn::position).max().orElse(-1) + 1;
        return toColumnView(columns.save(new BoardColumn(null, boardId, name.trim(), nextPosition, wipLimit)));
    }

    @Transactional
    public ColumnView updateColumn(long userId, long columnId, String name, Integer wipLimit) {
        BoardColumn column = requireColumn(columnId);
        Board board = requireBoard(column.boardId());
        permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);
        return toColumnView(columns.save(column.with(name.trim(), wipLimit)));
    }

    @Transactional
    public void deleteColumn(long userId, long columnId) {
        BoardColumn column = requireColumn(columnId);
        Board board = requireBoard(column.boardId());
        permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);
        if (cardCounter.countByColumnId(columnId) > 0) {
            throw new ColumnNotEmptyException();
        }
        columns.deleteById(columnId);
    }

    @Transactional
    public List<ColumnView> reorderColumns(long userId, long boardId, List<Long> orderedColumnIds) {
        Board board = requireBoard(boardId);
        permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);

        List<BoardColumn> current = columns.findByBoardId(boardId);
        List<Long> existing = current.stream().map(BoardColumn::id).sorted().toList();
        List<Long> requested = orderedColumnIds.stream().sorted().toList();
        if (!existing.equals(requested)) {
            throw new ColumnNotFoundException();
        }

        columns.reorder(boardId, orderedColumnIds);

        // Antwort in-memory aufbauen: der Direkt-SQL-Reindex umgeht den JPA-L1-Cache,
        // ein erneutes JPA-Read würde veraltete Positionen liefern.
        Map<Long, BoardColumn> byId = current.stream().collect(Collectors.toMap(BoardColumn::id, Function.identity()));
        List<ColumnView> result = new ArrayList<>();
        for (int i = 0; i < orderedColumnIds.size(); i++) {
            BoardColumn c = byId.get(orderedColumnIds.get(i));
            result.add(new ColumnView(c.id(), c.name(), i, c.wipLimit()));
        }
        return result;
    }

    private Board requireBoard(long boardId) {
        return boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
    }

    private BoardColumn requireColumn(long columnId) {
        return columns.findById(columnId).orElseThrow(ColumnNotFoundException::new);
    }

    private BoardView view(Board board) {
        List<ColumnView> columnViews = columns.findByBoardId(board.id()).stream()
                .map(BoardService::toColumnView).toList();
        return new BoardView(board.id(), board.projectId(), board.name(), board.createdAt(), columnViews);
    }

    private static ColumnView toColumnView(BoardColumn c) {
        return new ColumnView(c.id(), c.name(), c.position(), c.wipLimit());
    }

    /** Board inkl. seiner Spalten. */
    public record BoardView(Long id, Long projectId, String name, Instant createdAt, List<ColumnView> columns) {
    }

    /** Spaltendarstellung. */
    public record ColumnView(Long id, String name, int position, Integer wipLimit) {
    }
}
