package org.mwolff.manban.card.web;

import java.util.List;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.CardService.CardSearchHit;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Projektübergreifende Kartensuche nach Nummer (#489): Eingang für ein Suchfeld, das ohne
 * Projektkontext auskommt. Session-Auth erforderlich; welche Projekte durchsucht werden,
 * entscheidet der {@link CardService} anhand der Mitgliedschaften des Aufrufers.
 *
 * <p>Antwort ist stets eine Liste — auch leer. Sie unterscheidet nicht zwischen „Nummer existiert
 * nirgends" und „Nummer existiert nur in fremden Projekten"; ein 403 gäbe es hier nicht, weil
 * fremde Projekte gar nicht erst durchsucht werden.
 */
@RestController
class CardSearchController {

  private final CardService cards;

  CardSearchController(CardService cards) {
    this.cards = cards;
  }

  @GetMapping("/api/cards/search")
  List<CardSearchHit> search(@AuthenticationPrincipal Long userId, @RequestParam int number) {
    return cards.searchByNumber(userId, number);
  }
}
