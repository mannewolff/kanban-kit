package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.mwolff.manban.card.application.EpicService.DerivationNodeView;
import org.mwolff.manban.card.application.KartenZweigService;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Herkunftsbaum im Karten-Detail (Issue #1985, Plan #1980): Abruf über die Karten-ID, weil manche
 * Aufrufer die Karte ohne {@code boardId} kennen. Delegiert wird an den {@link KartenZweigService}.
 */
@Tag(name = "Karten")
@RestController
class KartenZweigController {

  private final KartenZweigService kartenZweigService;

  KartenZweigController(KartenZweigService kartenZweigService) {
    this.kartenZweigService = kartenZweigService;
  }

  @Operation(
      summary = "Herkunftsbaum einer Karte lesen",
      description =
          "Liefert den Zweig der Karte als Baum ihrer Herkunft, flach in Baumreihenfolge und in"
              + " denselben Zeilen wie der Baum eines Vorhabens: ab der obersten Anforderung der"
              + " Herkunftslinie abwärts, mit allem, was daraus entstanden ist. Archivierte"
              + " Karten und Vorhaben zählen nicht dazu. externalOrigin heißt, die Herkunft des"
              + " Anfangs liegt auf einem anderen Board, in einem anderen Projekt oder ist nicht"
              + " mehr erreichbar; derivedFrom nennt sie nur, wenn sie im selben Projekt liegt."
              + " Eine leere Liste heißt: Die Karte gehört zu keiner Kette, ist archiviert oder"
              + " ein Vorhaben. Verlangt die Mitgliedschaft im Projekt der Karte.")
  @ApiResponse(responseCode = "200", description = "Die Zeilen des Baums.")
  @ApiResponse(
      responseCode = "404",
      description =
          "Die Karte gibt es nicht, oder der Aufrufer ist kein Mitglied ihres Projekts — beides"
              + " ist nicht zu unterscheiden.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/cards/{cardId}/tree")
  List<DerivationNodeView> tree(
      @AuthenticationPrincipal Long userId,
      @Parameter(
              description = "Interne ID der Karte (Feld id), nicht die projektweite Nummer.",
              example = "1962")
          @PathVariable
          long cardId) {
    return kartenZweigService.kartenZweig(userId, cardId);
  }
}
