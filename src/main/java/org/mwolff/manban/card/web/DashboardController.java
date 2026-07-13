package org.mwolff.manban.card.web;

import org.mwolff.manban.card.application.BoardDashboardKpis;
import org.mwolff.manban.card.application.CardCycleTimeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liefert die Zykluszeit-Kennzahlen eines Boards für das Dashboard (Leserecht wie Board-Ansicht).
 */
@RestController
class DashboardController {

  private final CardCycleTimeService cycleTimes;

  DashboardController(CardCycleTimeService cycleTimes) {
    this.cycleTimes = cycleTimes;
  }

  @GetMapping("/api/boards/{boardId}/dashboard")
  BoardDashboardKpis dashboard(@AuthenticationPrincipal Long userId, @PathVariable long boardId) {
    return cycleTimes.dashboard(userId, boardId);
  }
}
