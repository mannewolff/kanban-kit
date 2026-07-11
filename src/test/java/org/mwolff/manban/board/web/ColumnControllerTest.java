package org.mwolff.manban.board.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;

/** Unit-Tests des Spalten-Controllers (Service gemockt). */
class ColumnControllerTest {

  private BoardService service;
  private ColumnController controller;

  private static ColumnView column() {
    return new ColumnView(1L, "Todo", 0, 5);
  }

  @BeforeEach
  void setUp() {
    service = mock(BoardService.class);
    controller = new ColumnController(service);
  }

  @Test
  void add_delegatesToService() {
    // Given
    ColumnView view = column();
    when(service.addColumn(3L, 2L, "Todo", 5)).thenReturn(view);

    // When
    ColumnView result = controller.add(3L, 2L, new ColumnController.ColumnRequest("Todo", 5));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void update_delegatesToService() {
    // Given
    ColumnView view = column();
    when(service.updateColumn(3L, 8L, "Todo", 5)).thenReturn(view);

    // When
    ColumnView result = controller.update(3L, 8L, new ColumnController.ColumnRequest("Todo", 5));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void delete_delegatesToService() {
    // When
    controller.delete(3L, 8L);

    // Then
    verify(service).deleteColumn(3L, 8L);
  }

  @Test
  void reorder_delegatesToService() {
    // Given
    List<ColumnView> views = List.of(column());
    List<Long> ids = List.of(3L, 1L, 2L);
    when(service.reorderColumns(3L, 2L, ids)).thenReturn(views);

    // When
    List<ColumnView> result = controller.reorder(3L, 2L, new ColumnController.ReorderRequest(ids));

    // Then
    assertThat(result).isSameAs(views);
  }
}
