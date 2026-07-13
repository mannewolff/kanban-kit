package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.BoardDashboardKpis;
import org.mwolff.manban.card.application.CardCycleTimeService;

/** Unit-Test des Dashboard-Controllers (Service gemockt). */
class DashboardControllerTest {

  private CardCycleTimeService service;
  private DashboardController controller;

  @BeforeEach
  void setUp() {
    service = mock(CardCycleTimeService.class);
    controller = new DashboardController(service);
  }

  @Test
  void dashboard_delegatesToServiceWithUserAndBoard() {
    BoardDashboardKpis kpis = new BoardDashboardKpis(List.of(), List.of(), 100L, 200L, List.of());
    when(service.dashboard(3L, 7L)).thenReturn(kpis);

    BoardDashboardKpis result = controller.dashboard(3L, 7L);

    assertThat(result).isSameAs(kpis);
  }
}
