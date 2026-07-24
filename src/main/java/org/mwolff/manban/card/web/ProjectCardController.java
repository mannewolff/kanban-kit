package org.mwolff.manban.card.web;

import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.CardView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Projektweiter Karten-Lookup: löst eine projektweite Nummer zu ihrer Karte auf (board-gebundene
 * Karte oder board-lose Pool-Idee). Session-Auth erforderlich; Rechte prüft der {@link CardService}
 * (Mitglied, sonst 404). Basis für klickbare {@code #N}-Verweise (#403).
 */
@RestController
class ProjectCardController {

  private final CardService cards;

  ProjectCardController(CardService cards) {
    this.cards = cards;
  }

  @GetMapping("/api/projects/{projectId}/cards/by-number/{number}")
  CardView byNumber(
      @AuthenticationPrincipal Long userId,
      @PathVariable long projectId,
      @PathVariable int number) {
    return cards.getByNumber(userId, projectId, number);
  }
}
