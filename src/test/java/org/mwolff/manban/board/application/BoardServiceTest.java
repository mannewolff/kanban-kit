package org.mwolff.manban.board.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;

/** Verhaltenstests der Board- und Spalten-Use-Cases (Mockito an den Ports). */
class BoardServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    private BoardRepository boards;
    private BoardColumnRepository columns;
    private ColumnCardCounter cardCounter;
    private PermissionChecker permissions;
    private BoardService service;

    private static Board board() {
        return new Board(10L, 1L, "Board", FIXED);
    }

    private static BoardColumn column(long id, String name, int position) {
        return new BoardColumn(id, 10L, name, position, null);
    }

    @BeforeEach
    void setUp() {
        boards = mock(BoardRepository.class);
        columns = mock(BoardColumnRepository.class);
        cardCounter = mock(ColumnCardCounter.class);
        permissions = mock(PermissionChecker.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        service = new BoardService(boards, columns, cardCounter, permissions, clock);
        when(boards.save(any(Board.class))).thenAnswer(inv -> {
            Board b = inv.getArgument(0);
            return b.id() == null ? new Board(10L, b.projectId(), b.name(), b.createdAt()) : b;
        });
        when(columns.save(any(BoardColumn.class))).thenAnswer(inv -> inv.getArgument(0));
        when(columns.findByBoardId(10L)).thenReturn(List.of());
    }

    @Test
    void createBoard_setsCreatedAtFromInjectedClock() {
        // When
        ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
        service.createBoard(1L, 2L, "Board");

        // Then
        verify(boards).save(captor.capture());
        assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
    }

    @Test
    void createBoard_seedsFiveDefaultColumns() {
        // When
        service.createBoard(1L, 2L, "Board");

        // Then
        verify(columns, times(5)).save(any(BoardColumn.class));
    }

    @Test
    void createBoard_trimsName() {
        // When
        ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
        service.createBoard(1L, 2L, "  Board  ");

        // Then
        verify(boards).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Board");
    }

    @Test
    void createBoard_returnsViewOfPersistedBoard() {
        // When
        BoardService.BoardView view = service.createBoard(1L, 2L, "Board");

        // Then
        assertThat(view.name()).isEqualTo("Board");
    }

    @Test
    void listBoards_mapsBoardsToViews() {
        // Given
        when(boards.findByProjectId(1L)).thenReturn(List.of(board()));

        // When
        List<BoardService.BoardView> views = service.listBoards(1L, 1L);

        // Then
        assertThat(views).singleElement().extracting(BoardService.BoardView::id).isEqualTo(10L);
    }

    @Test
    void getBoard_returnsBoardView() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));

        // When
        BoardService.BoardView view = service.getBoard(1L, 10L);

        // Then
        assertThat(view.name()).isEqualTo("Board");
    }

    @Test
    void getBoard_mapsColumnsIntoView() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));
        when(columns.findByBoardId(10L)).thenReturn(List.of(column(1L, "Todo", 0)));

        // When
        BoardService.BoardView view = service.getBoard(1L, 10L);

        // Then
        assertThat(view.columns()).singleElement().extracting(BoardService.ColumnView::name).isEqualTo("Todo");
    }

    @Test
    void getBoard_throwsBoardNotFound_whenUnknown() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.getBoard(1L, 10L)).isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    void renameBoard_trimsAndPersistsName() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));

        // When
        ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
        service.renameBoard(1L, 10L, "  Renamed  ");

        // Then
        verify(boards).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Renamed");
    }

    @Test
    void renameBoard_returnsViewWithNewName() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));

        // When
        BoardService.BoardView view = service.renameBoard(1L, 10L, "Renamed");

        // Then
        assertThat(view.name()).isEqualTo("Renamed");
    }

    @Test
    void renameBoard_requiresBoardUpdatePermission() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));

        // When
        service.renameBoard(1L, 10L, "Renamed");

        // Then
        verify(permissions).require(1L, 1L, Permission.BOARD_UPDATE);
    }

    @Test
    void deleteBoard_deletesById() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));

        // When
        service.deleteBoard(1L, 10L);

        // Then
        verify(boards).deleteById(10L);
    }

    @Test
    void addColumn_appendsAtNextPosition() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));
        when(columns.findByBoardId(10L)).thenReturn(List.of(column(1L, "A", 0), column(2L, "B", 1)));

        // When
        ArgumentCaptor<BoardColumn> captor = ArgumentCaptor.forClass(BoardColumn.class);
        service.addColumn(1L, 10L, "  C  ", 3);

        // Then
        verify(columns).save(captor.capture());
        assertThat(captor.getValue().position()).isEqualTo(2);
    }

    @Test
    void addColumn_returnsViewOfPersistedColumn() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));

        // When
        BoardService.ColumnView view = service.addColumn(1L, 10L, "Todo", 4);

        // Then
        assertThat(view.name()).isEqualTo("Todo");
    }

    @Test
    void addColumn_startsAtPositionZero_whenNoColumnsExist() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));

        // When
        ArgumentCaptor<BoardColumn> captor = ArgumentCaptor.forClass(BoardColumn.class);
        service.addColumn(1L, 10L, "First", null);

        // Then
        verify(columns).save(captor.capture());
        assertThat(captor.getValue().position()).isZero();
    }

    @Test
    void updateColumn_trimsNameAndPersists() {
        // Given
        when(columns.findById(2L)).thenReturn(Optional.of(column(2L, "Old", 1)));
        when(boards.findById(10L)).thenReturn(Optional.of(board()));

        // When
        ArgumentCaptor<BoardColumn> captor = ArgumentCaptor.forClass(BoardColumn.class);
        service.updateColumn(1L, 2L, "  New  ", 5);

        // Then
        verify(columns).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("New");
    }

    @Test
    void updateColumn_returnsViewWithNewName() {
        // Given
        when(columns.findById(2L)).thenReturn(Optional.of(column(2L, "Old", 1)));
        when(boards.findById(10L)).thenReturn(Optional.of(board()));

        // When
        BoardService.ColumnView view = service.updateColumn(1L, 2L, "New", 5);

        // Then
        assertThat(view.name()).isEqualTo("New");
    }

    @Test
    void updateColumn_throwsColumnNotFound_whenUnknown() {
        // Given
        when(columns.findById(2L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.updateColumn(1L, 2L, "New", null))
                .isInstanceOf(ColumnNotFoundException.class);
    }

    @Test
    void deleteColumn_deletesWhenEmpty() {
        // Given
        when(columns.findById(2L)).thenReturn(Optional.of(column(2L, "A", 1)));
        when(boards.findById(10L)).thenReturn(Optional.of(board()));
        when(cardCounter.countByColumnId(2L)).thenReturn(0L);

        // When
        service.deleteColumn(1L, 2L);

        // Then
        verify(columns).deleteById(2L);
    }

    @Test
    void deleteColumn_throwsColumnNotEmpty_whenColumnHoldsCards() {
        // Given
        when(columns.findById(2L)).thenReturn(Optional.of(column(2L, "A", 1)));
        when(boards.findById(10L)).thenReturn(Optional.of(board()));
        when(cardCounter.countByColumnId(2L)).thenReturn(3L);

        // When / Then
        assertThatThrownBy(() -> service.deleteColumn(1L, 2L)).isInstanceOf(ColumnNotEmptyException.class);
    }

    @Test
    void reorderColumns_returnsColumnsInRequestedOrder() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));
        when(columns.findByBoardId(10L)).thenReturn(List.of(column(1L, "A", 0), column(2L, "B", 1)));

        // When
        List<BoardService.ColumnView> result = service.reorderColumns(1L, 10L, List.of(2L, 1L));

        // Then
        assertThat(result).extracting(BoardService.ColumnView::id).containsExactly(2L, 1L);
    }

    @Test
    void reorderColumns_reindexesPositionsSequentially() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));
        when(columns.findByBoardId(10L)).thenReturn(List.of(column(1L, "A", 0), column(2L, "B", 1)));

        // When
        List<BoardService.ColumnView> result = service.reorderColumns(1L, 10L, List.of(2L, 1L));

        // Then
        assertThat(result).extracting(BoardService.ColumnView::position).containsExactly(0, 1);
    }

    @Test
    void reorderColumns_persistsNewOrderViaRepository() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));
        when(columns.findByBoardId(10L)).thenReturn(List.of(column(1L, "A", 0), column(2L, "B", 1)));

        // When
        service.reorderColumns(1L, 10L, List.of(2L, 1L));

        // Then
        verify(columns).reorder(10L, List.of(2L, 1L));
    }

    @Test
    void reorderColumns_throwsColumnNotFound_whenIdSetsDiffer() {
        // Given
        when(boards.findById(10L)).thenReturn(Optional.of(board()));
        when(columns.findByBoardId(10L)).thenReturn(List.of(column(1L, "A", 0), column(2L, "B", 1)));

        // When / Then
        assertThatThrownBy(() -> service.reorderColumns(1L, 10L, List.of(1L, 99L)))
                .isInstanceOf(ColumnNotFoundException.class);
    }
}
