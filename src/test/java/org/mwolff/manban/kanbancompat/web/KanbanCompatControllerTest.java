package org.mwolff.manban.kanbancompat.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Created;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Epic;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Item;
import org.mwolff.manban.kanbancompat.application.TokenNotBoundException;
import org.springframework.security.core.Authentication;

/** Unit-Tests des Kanban-Compat-Controllers (Service + Authentication gemockt). */
class KanbanCompatControllerTest {

  private static final KanbanPrincipal PRINCIPAL = new KanbanPrincipal(7L, 1L, 2L, 3L);

  private KanbanCompatService service;
  private KanbanCompatController controller;

  private static Authentication boundAuthentication() {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn(PRINCIPAL);
    return authentication;
  }

  @BeforeEach
  void setUp() {
    service = mock(KanbanCompatService.class);
    controller = new KanbanCompatController(service);
  }

  @Test
  void items_withBoundPrincipal_delegates() {
    // Given
    Map<String, List<Item>> items = Map.of();
    when(service.items(PRINCIPAL)).thenReturn(items);

    // When
    Map<String, List<Item>> result = controller.items(boundAuthentication());

    // Then
    assertThat(result).isSameAs(items);
  }

  @Test
  void create_withBoundPrincipal_delegates_defaultsIdeaStoredToFalse() {
    // Given: fehlendes ideaStored (null) -> false an den Service
    Created created = new Created(42);
    var request = new KanbanCompatController.CreateItemRequest("Title", "Body", "todo", null);
    when(service.create(PRINCIPAL, "Title", "Body", "todo", false)).thenReturn(created);

    // When
    Created result = controller.create(boundAuthentication(), request);

    // Then
    assertThat(result).isSameAs(created);
  }

  @Test
  void create_withIdeaStoredTrue_delegatesFlag() {
    // Given: ideaStored=true wird an den Service durchgereicht
    Created created = new Created(43);
    var request = new KanbanCompatController.CreateItemRequest("Idee", "Body", "todo", true);
    when(service.create(PRINCIPAL, "Idee", "Body", "todo", true)).thenReturn(created);

    // When
    Created result = controller.create(boundAuthentication(), request);

    // Then
    assertThat(result).isSameAs(created);
  }

  @Test
  void move_withBoundPrincipal_delegates() {
    // Given
    var request = new KanbanCompatController.MoveRequest("done", 2);

    // When
    controller.move(boundAuthentication(), 8L, request);

    // Then
    verify(service).move(PRINCIPAL, 8L, "done", 2);
  }

  @Test
  void comment_withBoundPrincipal_delegates() {
    // Given
    var request = new KanbanCompatController.CommentRequest("hello");

    // When
    controller.comment(boundAuthentication(), 8L, request);

    // Then
    verify(service).comment(PRINCIPAL, 8L, "hello");
  }

  @Test
  void epics_withBoundPrincipal_delegates() {
    // Given
    List<Epic> epics = List.of();
    when(service.epics(PRINCIPAL)).thenReturn(epics);

    // When
    List<Epic> result = controller.epics(boundAuthentication());

    // Then
    assertThat(result).isSameAs(epics);
  }

  @Test
  void items_nullAuthentication_throwsTokenNotBound() {
    // When / Then
    assertThatThrownBy(() -> controller.items(null)).isInstanceOf(TokenNotBoundException.class);
  }

  @Test
  void items_detailsNotKanbanPrincipal_throwsTokenNotBound() {
    // Given
    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn("not-a-principal");

    // When / Then
    assertThatThrownBy(() -> controller.items(authentication))
        .isInstanceOf(TokenNotBoundException.class);
  }
}
