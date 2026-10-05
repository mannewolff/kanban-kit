package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mwolff.manban.card.application.CardSearchService;
import org.mwolff.manban.card.application.CardView;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Projektweiter Karten-Lookup: löst eine projektweite Nummer zu ihrer Karte auf. Session-Auth
 * erforderlich; Rechte prüft der {@link CardSearchService} (Mitglied, sonst 404). Basis für
 * klickbare {@code #N}-Verweise (#403).
 */
@Tag(name = "Karten")
@RestController
class ProjectCardController {

  private final CardSearchService cards;

  ProjectCardController(CardSearchService cards) {
    this.cards = cards;
  }

  @Operation(
      summary = "Karte über ihre projektweite Nummer lesen",
      description =
          "Löst die projektweite Nummer (etwa #1404) im Projekt zu ihrer Karte auf. Verlangt die"
              + " Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Karte mit dieser Nummer.")
  @ApiResponse(
      responseCode = "404",
      description =
          "Es gibt keine Karte mit dieser Nummer im Projekt, oder der Aufrufer ist kein Mitglied"
              + " des Projekts.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/projects/{projectId}/cards/by-number/{number}")
  CardView byNumber(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Projekts.", example = "1") @PathVariable
          long projectId,
      @Parameter(description = "Projektweite Kartennummer.", example = "1404") @PathVariable
          int number) {
    return cards.getByNumber(userId, projectId, number);
  }
}
