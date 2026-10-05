package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardArchiveService;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.card.domain.CardType;

/**
 * Unit-Tests des Archiv- und Papierkorb-Controllers (Service gemockt). Die Testmethoden stammen
 * unverändert aus {@link CardControllerTest} (Issue #1394, Plan #1387 E6/E12).
 */
class CardArchiveControllerTest {

  private CardArchiveService service;
  private CardArchiveController controller;

  private static CardView card() {
    return new CardView(
        1L,
        2L,
        3L,
        4,
        "Title",
        "Desc",
        null,
        0,
        false,
        null,
        List.of(),
        CardType.CARD,
        null,
        null,
        List.of(),
        null,
        List.of(),
        null,
        "BACKLOG",
        true);
  }

  @BeforeEach
  void setUp() {
    service = mock(CardArchiveService.class);
    controller = new CardArchiveController(service);
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
  void bulkArchive_delegatesToService() {
    // Given
    List<CardView> views = List.of(card());
    var request = new CardArchiveController.BulkArchiveRequest(List.of(8L, 9L));
    when(service.bulkArchive(3L, List.of(8L, 9L))).thenReturn(views);

    // When
    List<CardView> result = controller.bulkArchive(3L, request);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void delete_delegatesToService() {
    // When
    controller.delete(3L, 8L);

    // Then
    verify(service).delete(3L, 8L);
  }

  @Test
  void bulkDelete_delegatesToService() {
    // Given
    var request = new CardArchiveController.BulkDeleteRequest(List.of(8L, 9L));

    // When
    controller.bulkDelete(3L, request);

    // Then
    verify(service).bulkDelete(3L, List.of(8L, 9L));
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
}
