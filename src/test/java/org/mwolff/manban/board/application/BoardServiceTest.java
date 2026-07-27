package org.mwolff.manban.board.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
// PMD.TooManyMethods: umfassende Unit-Suite (Boards, Spalten und die modulfremde Fassade, je
// Erfolgs- und Fehlerpfad). Viele kleine @Test-Methoden sind hier gewollt, kein God-Class-Smell.
@SuppressWarnings("PMD.TooManyMethods")
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
    when(boards.save(any(Board.class)))
        .thenAnswer(
            inv -> {
              Board b = inv.getArgument(0);
              return b.id() == null ? new Board(10L, b.projectId(), b.name(), b.createdAt()) : b;
            });
    // Simuliert die DB: beim ersten Speichern wird eine ID vergeben (Issue #0080).
    when(columns.save(any(BoardColumn.class)))
        .thenAnswer(
            inv -> {
              BoardColumn c = inv.getArgument(0);
              return c.id() == null
                  ? new BoardColumn(99L, c.boardId(), c.name(), c.position(), c.wipLimit())
                  : c;
            });
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
    assertThat(view.columns())
        .singleElement()
        .extracting(BoardService.ColumnView::name)
        .isEqualTo("Todo");
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
  void deleteBoard_archivesInsteadOfDeleting() {
    // Given
    when(boards.findById(10L)).thenReturn(Optional.of(board()));

    // When
    service.deleteBoard(1L, 10L);

    // Then (Archiv-Lebenszyklus im Detail: siehe BoardArchiveServiceTest)
    verify(boards).save(any(Board.class));
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
    assertThatThrownBy(() -> service.deleteColumn(1L, 2L))
        .isInstanceOf(ColumnNotEmptyException.class);
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
    List<Long> mismatchedIds = List.of(1L, 99L);
    assertThatThrownBy(() -> service.reorderColumns(1L, 10L, mismatchedIds))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  // --- Fassade fuer modulfremde Use-Cases (Issue #459) ---------------------

  @Test
  void requireProjectId_returnsProjectOfBoard() {
    // Given
    when(boards.findById(10L)).thenReturn(Optional.of(board()));

    // When / Then
    assertThat(service.requireProjectId(10L)).isEqualTo(1L);
  }

  @Test
  void requireProjectId_throwsBoardNotFound_whenBoardUnknown() {
    // Given: archivierte Boards liefert findById ebenfalls nicht
    when(boards.findById(10L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.requireProjectId(10L))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void requireProjectId_doesNotCheckPermissions() {
    // Given: die Fassade loest nur auf — die Rechtepruefung bleibt beim aufrufenden Modul.
    when(boards.findById(10L)).thenReturn(Optional.of(board()));

    // When
    service.requireProjectId(10L);

    // Then
    verifyNoInteractions(permissions);
  }

  @Test
  void findProjectId_returnsProjectOfBoard() {
    // Given
    when(boards.findById(10L)).thenReturn(Optional.of(board()));

    // When / Then
    assertThat(service.findProjectId(10L)).contains(1L);
  }

  @Test
  void findProjectId_returnsEmpty_whenBoardUnknown() {
    // Given: kein 404 — der Aufrufer entscheidet, wie ein unbekanntes Board zu werten ist
    when(boards.findById(10L)).thenReturn(Optional.empty());

    // When / Then
    assertThat(service.findProjectId(10L)).isEmpty();
  }

  @Test
  void requireColumn_returnsViewOfColumn() {
    // Given
    when(columns.findById(1L)).thenReturn(Optional.of(new BoardColumn(1L, 10L, "Ready", 2, 5)));

    // When / Then
    assertThat(service.requireColumn(1L, 10L))
        .isEqualTo(new BoardService.ColumnView(1L, "Ready", 2, 5));
  }

  @Test
  void requireColumn_throwsColumnNotFound_whenColumnUnknown() {
    // Given
    when(columns.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.requireColumn(1L, 10L))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void requireColumn_throwsColumnNotFound_whenColumnBelongsToOtherBoard() {
    // Given: die Spalte existiert, gehoert aber zu einem fremden Board (kein Existenz-Leak)
    when(columns.findById(1L)).thenReturn(Optional.of(new BoardColumn(1L, 99L, "Ready", 2, null)));

    // When / Then
    assertThatThrownBy(() -> service.requireColumn(1L, 10L))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void listColumns_returnsColumnsSortedByPosition() {
    // Given: die Rohdaten kommen bewusst unsortiert
    when(columns.findByBoardId(10L))
        .thenReturn(List.of(column(2L, "Ready", 1), column(1L, "Backlog", 0)));

    // When / Then
    assertThat(service.listColumns(10L))
        .containsExactly(
            new BoardService.ColumnView(1L, "Backlog", 0, null),
            new BoardService.ColumnView(2L, "Ready", 1, null));
  }

  @Test
  void listColumns_returnsEmptyList_whenBoardHasNoColumns() {
    // Given (Default-Stub aus setUp: keine Spalten)

    // When / Then
    assertThat(service.listColumns(10L)).isEmpty();
  }

  @Test
  void firstColumn_returnsColumnWithSmallestPosition() {
    // Given: die Rohdaten kommen bewusst unsortiert
    when(columns.findByBoardId(10L))
        .thenReturn(List.of(column(2L, "Ready", 1), column(1L, "Backlog", 0)));

    // When / Then
    assertThat(service.firstColumn(10L))
        .isEqualTo(new BoardService.ColumnView(1L, "Backlog", 0, null));
  }

  @Test
  void firstColumn_throwsColumnNotFound_whenBoardHasNoColumns() {
    // Given (Default-Stub aus setUp: keine Spalten)

    // When / Then
    assertThatThrownBy(() -> service.firstColumn(10L)).isInstanceOf(ColumnNotFoundException.class);
  }
}
