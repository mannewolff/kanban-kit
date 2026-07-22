package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.CardView;
import org.mwolff.manban.card.domain.CardType;

/** Unit-Tests des Projekt-Ideen-Controllers (Service gemockt). */
class ProjectIdeaControllerTest {

  private CardService service;
  private ProjectIdeaController controller;

  private static CardView idea() {
    return new CardView(
        1L,
        null,
        null,
        null,
        "Idee",
        null,
        0,
        false,
        true,
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
    controller = new ProjectIdeaController(service);
  }

  @Test
  void list_delegatesToListProjectIdeas() {
    when(service.listProjectIdeas(7L, 3L)).thenReturn(List.of(idea()));

    List<CardView> result = controller.list(7L, 3L);

    verify(service).listProjectIdeas(7L, 3L);
    assertThat(result).singleElement().extracting(CardView::id).isEqualTo(1L);
  }

  @Test
  void create_delegatesToCreateProjectIdea() {
    when(service.createProjectIdea(7L, 3L, "T", "d", 9L)).thenReturn(idea());

    CardView result =
        controller.create(7L, 3L, new ProjectIdeaController.CreateIdeaRequest("T", "d", 9L));

    verify(service).createProjectIdea(7L, 3L, "T", "d", 9L);
    assertThat(result.id()).isEqualTo(1L);
  }
}
