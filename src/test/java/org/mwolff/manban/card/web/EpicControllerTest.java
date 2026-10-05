package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.card.application.EpicService;
import org.mwolff.manban.card.application.EpicService.EpicView;
import org.mwolff.manban.card.domain.CardType;

/**
 * Unit-Tests des Vorhaben-Controllers (Service gemockt). Die Testmethoden stammen unverändert aus
 * {@link CardControllerTest} (Issue #1393, Plan #1387 E6/E12).
 */
class EpicControllerTest {

  private EpicService service;
  private EpicController controller;

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
    service = mock(EpicService.class);
    controller = new EpicController(service);
  }

  @Test
  void assignRequirement_delegiertNummerUnveraendert() {
    when(service.assignRequirement(3L, 1L, 7)).thenReturn(card());

    CardView result =
        controller.assignRequirement(3L, 1L, new EpicController.AssignRequirementRequest(7));

    assertThat(result).isNotNull();
    verify(service).assignRequirement(3L, 1L, 7);
  }

  /**
   * {@code null} muss durchgereicht werden — es loescht die Zuordnung, statt sie zu ueberspringen.
   */
  @Test
  void assignRequirement_reichtNullDurch() {
    when(service.assignRequirement(3L, 1L, null)).thenReturn(card());

    controller.assignRequirement(3L, 1L, new EpicController.AssignRequirementRequest(null));

    verify(service).assignRequirement(3L, 1L, null);
  }

  @Test
  void epics_delegatesToService() {
    // Given
    List<EpicView> views =
        List.of(new EpicView(1L, 4, "Epic", "Desc", "EP-1", 1, 3, List.of(7, 8, 9), List.of(7), 7));
    when(service.listEpics(3L, 2L)).thenReturn(views);

    // When
    List<EpicView> result = controller.epics(3L, 2L);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void assignParent_delegatesToService() {
    // Given
    CardView view = card();
    when(service.assignParent(3L, 8L, 9L)).thenReturn(view);

    // When
    CardView result = controller.assignParent(3L, 8L, new EpicController.AssignParentRequest(9L));

    // Then
    assertThat(result).isSameAs(view);
  }
}
