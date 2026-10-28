package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.nightrun.application.NightRunModelsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Platte „Modelle“ des Board-Leitstands an HTTP (Issue #1947, Plan #1905 E13).
 *
 * <p>Ein eigener Endpunkt neben {@link NightRunReworkController}, damit beide Platten einzeln
 * ausfallen können. Ein reiner Aufruf des Leitstands, darum ohne {@code @ApiVertrag}. Keine eigenen
 * Exceptions: 403 und 404 liefert der Service.
 */
@Tag(name = "Läufe (Runner)")
@RestController
class NightRunModelsController {

  private final NightRunModelsService service;

  NightRunModelsController(NightRunModelsService service) {
    this.service = service;
  }

  @Operation(
      summary = "Modellvergleich der Runs an einem Board lesen",
      description =
          "Liefert über die zwölf rollenden Wochenfenster bis jetzt je Stufe der Kette (PLAN,"
              + " REVIEW, PAKETE, ABDECKUNG, UMSETZUNG) eine Zeile je gemeldetem Modell und"
              + " Denktiefe: Zahl der Vorgänge, erfolgreiche Vorgänge und die Medians von Kosten"
              + " und Modellzeit je Vorgang, jeweils mit der Zahl der Vorgänge, die den Wert"
              + " tragen. Es zählen nur Vorgänge aus Nachtläufen mit gemeldetem Ende an Karten"
              + " dieses Boards; Vorgänge ohne gemeldetes Modell zählt nur der Kopf. "
              + NightRunController.LESERECHT)
  @ApiResponse(responseCode = "200", description = "Der Modellvergleich.")
  @ApiResponse(
      responseCode = "403",
      description = NightRunController.LESEN_VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description =
          "Das Board gibt es nicht oder es ist archiviert, oder der Aufrufer ist im Projekt kein"
              + " Mitglied.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/night-run-models")
  NightRunModelsView models(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Kennung des Boards.", example = "12") @PathVariable long boardId) {
    return NightRunModelsView.of(service.modelle(userId, boardId));
  }
}
