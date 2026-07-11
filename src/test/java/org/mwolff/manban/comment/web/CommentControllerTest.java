package org.mwolff.manban.comment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.CommentView;

/** Unit-Tests des Kommentar-Controllers (Service gemockt). */
class CommentControllerTest {

  private CommentService service;
  private CommentController controller;

  private static CommentView comment() {
    return new CommentView(1L, 5L, 3L, "Alice", "hello", Instant.EPOCH, Instant.EPOCH);
  }

  @BeforeEach
  void setUp() {
    service = mock(CommentService.class);
    controller = new CommentController(service);
  }

  @Test
  void create_delegatesToService() {
    // Given
    CommentView view = comment();
    when(service.create(3L, 5L, "hello")).thenReturn(view);

    // When
    CommentView result = controller.create(3L, 5L, new CommentController.CommentRequest("hello"));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void list_delegatesToService() {
    // Given
    List<CommentView> views = List.of(comment());
    when(service.list(3L, 5L)).thenReturn(views);

    // When
    List<CommentView> result = controller.list(3L, 5L);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void update_delegatesToService() {
    // Given
    CommentView view = comment();
    when(service.update(3L, 8L, "edited")).thenReturn(view);

    // When
    CommentView result = controller.update(3L, 8L, new CommentController.CommentRequest("edited"));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void delete_delegatesToService() {
    // When
    controller.delete(3L, 8L);

    // Then
    verify(service).delete(3L, 8L);
  }
}
