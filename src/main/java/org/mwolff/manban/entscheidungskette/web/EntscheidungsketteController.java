package org.mwolff.manban.entscheidungskette.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteService;
import org.mwolff.manban.entscheidungskette.application.EntscheidungsketteView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entscheidungskette im Karten-Detail (Issue #2117, Plan #2108 E7): Abruf über die Karten-ID mit
 * derselben 404-Regel wie der Herkunftsbaum. Delegiert wird an den {@link
 * EntscheidungsketteService}.
 */
@Tag(name = "Karten")
@RestController
class EntscheidungsketteController {

  private final EntscheidungsketteService service;

  EntscheidungsketteController(EntscheidungsketteService service) {
    this.service = service;
  }

  @Operation(
      summary = "Entscheidungskette einer Karte lesen",
      description =
          "Liefert, wie es zu der Karte kam: die Idee, Fachplan und Plan mit Herkunft und"
              + " Prüfrunden, am Paket dazu Issue-Review, Freigabe zur Umsetzung, Umsetzung,"
              + " Code-Review, Übertragung und Veröffentlichung, am Fachplan und am Plan je"
              + " Nachfahre einen Verweis mit Spalte und zuletzt erreichtem Glied. Jedes Glied"
              + " nennt, wer es getan hat, wann, mit welchem Ergebnis und aus welcher Quelle; was"
              + " fehlt, ist null und gilt als nicht erfasst. Es zählen nur Meldungen, die das Kit"
              + " über einen Projekt-Zugang geschrieben hat. Ein Glied aus einem anderen Projekt"
              + " trägt weder Nummer noch Titel. sichtbar ist false in einem Projekt, in dem das"
              + " Kit nicht arbeitet, an einer Idee und an einem Vorhaben. Verlangt die"
              + " Mitgliedschaft im Projekt der Karte; eine eigene Rolle gibt es nicht.")
  @ApiResponse(responseCode = "200", description = "Die Kette der Karte.")
  @ApiResponse(
      responseCode = "404",
      description =
          "Die Karte gibt es nicht, sie liegt im Papierkorb, oder der Aufrufer ist kein Mitglied"
              + " ihres Projekts — das ist nicht zu unterscheiden.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/cards/{cardId}/entscheidungskette")
  EntscheidungsketteView entscheidungskette(
      @AuthenticationPrincipal Long userId,
      @Parameter(
              description = "Interne ID der Karte (Feld id), nicht die projektweite Nummer.",
              example = "1962")
          @PathVariable
          long cardId) {
    return service.kette(userId, cardId);
  }
}
