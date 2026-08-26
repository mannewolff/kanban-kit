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

/** Unit-Tests des projektweiten Karten-Lookups (Service gemockt). */
class ProjectCardControllerTest {

  private CardService service;
  private ProjectCardController controller;

  private static CardView card() {
    return new CardView(
        1L,
        10L,
        20L,
        42,
        "Karte",
        null,
        0,
        false,
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
        null);
  }

  @BeforeEach
  void setUp() {
    service = mock(CardService.class);
    controller = new ProjectCardController(service);
  }

  @Test
  void byNumber_delegatesToGetByNumber() {
    when(service.getByNumber(7L, 3L, 42)).thenReturn(card());

    CardView result = controller.byNumber(7L, 3L, 42);

    verify(service).getByNumber(7L, 3L, 42);
    assertThat(result.number()).isEqualTo(42);
  }
}
