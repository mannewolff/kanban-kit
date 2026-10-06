package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.mwolff.manban.card.application.CardSearchService;
import org.mwolff.manban.card.application.CardSearchService.CardSearchHit;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Projektübergreifende Kartensuche nach Nummer (#489): Eingang für ein Suchfeld, das ohne
 * Projektkontext auskommt. Session-Auth erforderlich; welche Projekte durchsucht werden,
 * entscheidet der {@link CardSearchService} anhand der Mitgliedschaften des Aufrufers.
 *
 * <p>Antwort ist stets eine Liste — auch leer. Sie unterscheidet nicht zwischen „Nummer existiert
 * nirgends" und „Nummer existiert nur in fremden Projekten"; ein 403 gäbe es hier nicht, weil
 * fremde Projekte gar nicht erst durchsucht werden.
 */
@Tag(name = "Karten")
@RestController
class CardSearchController {

  private final CardSearchService cards;

  CardSearchController(CardSearchService cards) {
    this.cards = cards;
  }

  @Operation(
      summary = "Karten projektübergreifend nach Nummer suchen",
      description =
          "Sucht die projektweite Nummer in allen Projekten, in denen der Aufrufer lesen darf,"
              + " und liefert je Treffer die Karte samt Projekt, Board und Spalte. Kartennummern"
              + " sind nur je Projekt eindeutig, deshalb ist die Antwort eine Liste — leer, wenn"
              + " die Nummer in keinem lesbaren Projekt vorkommt. Fremde Projekte werden nicht"
              + " durchsucht; ihr Bestand ist aus der Antwort nicht ablesbar.")
  @ApiResponse(responseCode = "200", description = "Die Treffer, möglicherweise leer.")
  @ApiResponse(
      responseCode = "400",
      description = "Der Parameter number fehlt oder ist keine ganze Zahl.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/cards/search")
  List<CardSearchHit> search(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Gesuchte projektweite Kartennummer.", example = "1404")
          @RequestParam
          int number) {
    return cards.searchByNumber(userId, number);
  }
}
