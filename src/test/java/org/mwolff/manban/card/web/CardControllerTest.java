package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.CardView;
import org.mwolff.manban.card.application.CardService.EpicView;
import org.mwolff.manban.card.domain.CardType;

/** Unit-Tests des Karten-/Epic-Controllers (Service gemockt). */
class CardControllerTest {

  private static final java.time.Instant INSTANT = java.time.Instant.parse("2026-01-01T00:00:00Z");

  private CardService service;
  private CardController controller;

  private static CardView card() {
    return new CardView(
        1L,
        2L,
        3L,
        4,
        "Title",
        "Desc",
        0,
        false,
        null,
        List.of(),
        CardType.CARD,
        null,
        null,
        List.of(),
        null,
        List.of());
  }

  @BeforeEach
  void setUp() {
    service = mock(CardService.class);
    controller = new CardController(service);
  }

  @Test
  void create_epicType_delegatesToCreateEpic() {
    // Given
    CardView view = card();
    var request =
        new CardController.CreateCardRequest(
            null, "Epic", "Desc", null, CardType.EPIC, null, "EP-1");
    when(service.createEpic(3L, 2L, "Epic", "Desc", "EP-1")).thenReturn(view);

    // When
    CardView result = controller.create(3L, 2L, request);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void create_nullTypeDefaultsToCard_delegatesToCreate() {
    // Given
    CardView view = card();
    var deps = List.of(1, 2);
    var request = new CardController.CreateCardRequest(7L, "Title", "Desc", deps, null, 9L, null);
    when(service.create(3L, 2L, 7L, "Title", "Desc", deps, 9L)).thenReturn(view);

    // When
    CardView result = controller.create(3L, 2L, request);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void create_cardTypeWithoutColumn_throwsColumnNotFound() {
    // Given
    var request =
        new CardController.CreateCardRequest(
            null, "Title", "Desc", null, CardType.CARD, null, null);

    // When / Then
    assertThatThrownBy(() -> controller.create(3L, 2L, request))
        .isInstanceOf(ColumnNotFoundException.class);
  }

  @Test
  void list_delegatesToService() {
    // Given
    List<CardView> views = List.of(card());
    when(service.listByBoard(3L, 2L)).thenReturn(views);

    // When
    List<CardView> result = controller.list(3L, 2L);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void epics_delegatesToService() {
    // Given
    List<EpicView> views = List.of(new EpicView(1L, 4, "Epic", "Desc", "EP-1", 1, 3));
    when(service.listEpics(3L, 2L)).thenReturn(views);

    // When
    List<EpicView> result = controller.epics(3L, 2L);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void update_delegatesToService() {
    // Given
    CardView view = card();
    var deps = List.of(3, 4);
    var request = new CardController.UpdateCardRequest("Title", "Desc", deps, "SC-1", 9L, null);
    when(service.update(3L, 8L, "Title", "Desc", deps, "SC-1", 9L, null)).thenReturn(view);

    // When
    CardView result = controller.update(3L, 8L, request);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void assignParent_delegatesToService() {
    // Given
    CardView view = card();
    when(service.assignParent(3L, 8L, 9L)).thenReturn(view);

    // When
    CardView result = controller.assignParent(3L, 8L, new CardController.AssignParentRequest(9L));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void move_delegatesToService() {
    // Given
    CardView view = card();
    when(service.move(3L, 8L, 5L, 2)).thenReturn(view);

    // When
    CardView result = controller.move(3L, 8L, new CardController.MoveCardRequest(5L, 2));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void archive_delegatesToService() {
    // Given
    CardView view = card();
    when(service.archive(3L, 8L)).thenReturn(view);

    // When
    CardView result = controller.archive(3L, 8L);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void restore_delegatesToService() {
    // Given
    CardView view = card();
    when(service.restore(3L, 8L)).thenReturn(view);

    // When
    CardView result = controller.restore(3L, 8L);

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

  @Test
  void transfer_delegatesToService() {
    // Given
    CardView view = card();
    when(service.transfer(3L, 8L, 20L, 60L)).thenReturn(view);

    // When
    CardView result = controller.transfer(3L, 8L, new CardController.TransferCardRequest(20L, 60L));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void setAssignees_delegatesToService() {
    // Given
    CardView view = card();
    when(service.setAssignees(3L, 8L, List.of(5L, 6L))).thenReturn(view);

    // When
    CardView result =
        controller.setAssignees(3L, 8L, new CardController.AssigneesRequest(List.of(5L, 6L)));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void setAssignees_coalescesNullListToEmpty() {
    // Given
    CardView view = card();
    when(service.setAssignees(3L, 8L, List.of())).thenReturn(view);

    // When
    CardView result = controller.setAssignees(3L, 8L, new CardController.AssigneesRequest(null));

    // Then
    assertThat(result).isSameAs(view);
    verify(service).setAssignees(3L, 8L, List.of());
  }

  @Test
  void setLabels_delegatesToService() {
    CardView view = card();
    when(service.setLabels(3L, 8L, List.of(5L, 6L))).thenReturn(view);

    CardView result =
        controller.setLabels(3L, 8L, new CardController.LabelsRequest(List.of(5L, 6L)));

    assertThat(result).isSameAs(view);
  }

  @Test
  void setLabels_coalescesNullListToEmpty() {
    CardView view = card();
    when(service.setLabels(3L, 8L, List.of())).thenReturn(view);

    CardView result = controller.setLabels(3L, 8L, new CardController.LabelsRequest(null));

    assertThat(result).isSameAs(view);
    verify(service).setLabels(3L, 8L, List.of());
  }

  @Test
  void trash_delegatesToService() {
    List<CardView> views = List.of(card());
    when(service.listTrash(3L, 2L)).thenReturn(views);

    assertThat(controller.trash(3L, 2L)).isSameAs(views);
  }

  @Test
  void restoreDeleted_delegatesToService() {
    CardView view = card();
    when(service.restoreFromTrash(3L, 8L)).thenReturn(view);

    assertThat(controller.restoreDeleted(3L, 8L)).isSameAs(view);
  }

  @Test
  void purge_delegatesToService() {
    controller.purge(3L, 8L);

    verify(service).purge(3L, 8L);
  }

  @Test
  void activity_mapsDomainToViews() {
    var entry =
        new org.mwolff.manban.card.domain.CardActivity(
            5L,
            8L,
            9L,
            org.mwolff.manban.card.domain.CardActivityType.MOVED,
            "Verschoben",
            INSTANT);
    when(service.listActivity(3L, 8L)).thenReturn(List.of(entry));

    List<CardController.ActivityView> result = controller.activity(3L, 8L);

    assertThat(result)
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.id()).isEqualTo(5L);
              assertThat(v.actorUserId()).isEqualTo(9L);
              assertThat(v.type()).isEqualTo("MOVED");
              assertThat(v.detail()).isEqualTo("Verschoben");
              assertThat(v.createdAt()).isEqualTo(INSTANT);
            });
  }
}
