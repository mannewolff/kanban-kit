package org.mwolff.manban.board.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;

/** Unit-Tests des Board-Controllers (Service gemockt). */
class BoardControllerTest {

  private BoardService service;
  private BoardController controller;

  private static BoardView board() {
    return new BoardView(1L, 2L, "Board", Instant.EPOCH, List.of());
  }

  @BeforeEach
  void setUp() {
    service = mock(BoardService.class);
    controller = new BoardController(service);
  }

  @Test
  void create_delegatesToService() {
    // Given
    BoardView view = board();
    when(service.createBoard(3L, 2L, "Board")).thenReturn(view);

    // When
    BoardView result = controller.create(3L, 2L, new BoardController.BoardRequest("Board"));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void list_delegatesToService() {
    // Given
    List<BoardView> views = List.of(board());
    when(service.listBoards(3L, 2L)).thenReturn(views);

    // When
    List<BoardView> result = controller.list(3L, 2L);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void get_delegatesToService() {
    // Given
    BoardView view = board();
    when(service.getBoard(3L, 5L)).thenReturn(view);

    // When
    BoardView result = controller.get(3L, 5L);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void rename_delegatesToService() {
    // Given
    BoardView view = board();
    when(service.renameBoard(3L, 5L, "New")).thenReturn(view);

    // When
    BoardView result = controller.rename(3L, 5L, new BoardController.BoardRequest("New"));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void delete_delegatesToService() {
    // When
    controller.delete(3L, 5L);

    // Then
    verify(service).deleteBoard(3L, 5L);
  }

  @Test
  void listArchived_delegatesToService() {
    // Given
    List<BoardView> views = List.of(board());
    when(service.listArchivedBoards(3L, 2L)).thenReturn(views);

    // When
    List<BoardView> result = controller.listArchived(3L, 2L);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void restore_delegatesToService() {
    // Given
    BoardView view = board();
    when(service.restoreBoard(3L, 5L)).thenReturn(view);

    // When
    BoardView result = controller.restore(3L, 5L);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void purge_delegatesToService() {
    // When
    controller.purge(3L, 5L);

    // Then
    verify(service).purgeBoard(3L, 5L);
  }
}
