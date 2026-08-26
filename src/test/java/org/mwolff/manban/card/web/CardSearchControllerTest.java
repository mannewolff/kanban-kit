package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.CardSearchHit;
import org.mwolff.manban.card.application.CardService.CardView;
import org.mwolff.manban.card.domain.CardType;

/** Unit-Tests der projektuebergreifenden Kartensuche (Service gemockt). */
class CardSearchControllerTest {

  private CardService service;
  private CardSearchController controller;

  private static CardSearchHit hit() {
    CardView card =
        new CardView(
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
    return new CardSearchHit(card, 3L, "Projekt A", 10L, "Board A", false, 20L, "Ready");
  }

  @BeforeEach
  void setUp() {
    service = mock(CardService.class);
    controller = new CardSearchController(service);
  }

  @Test
  void search_delegatesToSearchByNumber() {
    when(service.searchByNumber(7L, 42)).thenReturn(List.of(hit()));

    List<CardSearchHit> result = controller.search(7L, 42);

    verify(service).searchByNumber(7L, 42);
    assertThat(result)
        .singleElement()
        .extracting(CardSearchHit::projectName)
        .isEqualTo("Projekt A");
  }

  @Test
  void search_returnsEmptyList_whenNothingFound() {
    when(service.searchByNumber(7L, 99)).thenReturn(List.of());

    assertThat(controller.search(7L, 99)).isEmpty();
  }
}
