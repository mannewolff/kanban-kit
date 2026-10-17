package org.mwolff.manban.deployment.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.deployment.application.DeploymentKennzahlenService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Platte „Auslieferung“ des Board-Leitstands an HTTP (Issue #1835, Plan #1831 E5).
 *
 * <p>Ein reiner Aufruf des Leitstands mit Sitzung, darum ohne {@code @ApiVertrag} — wie die übrigen
 * {@code /dashboard}-Aufrufe. Keine eigenen Exceptions: 404 liefert der Service.
 */
@Tag(name = "Auslieferungen")
@RestController
class DeploymentDashboardController {

  private final DeploymentKennzahlenService service;

  DeploymentDashboardController(DeploymentKennzahlenService service) {
    this.service = service;
  }

  @Operation(
      summary = "Auslieferungs-Kennzahlen eines Boards lesen",
      description =
          "Liefert die Platte „Auslieferung“ für das ganze Projekt des Boards: über die zwölf"
              + " rollenden Wochenfenster bis jetzt die Zahl erfolgreicher Auslieferungen, den"
              + " Median der Lead Time vom Commit bis zur Auslieferung, die Change Failure Rate in"
              + " ganzen Prozent, den Median der Time to Restore, einen noch laufenden Ausfall und"
              + " je Woche die erfolgreichen und gescheiterten Versuche. Gezählt wird allein, was"
              + " Auslieferungsläufe gemeldet haben; maßgeblich ist der Zeitpunkt ihres Ausgangs."
              + " Dauern in Sekunden; null heißt „keine Grundlage“. Verlangt die Mitgliedschaft im"
              + " Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Kennzahlen der Platte „Auslieferung“.")
  @ApiResponse(
      responseCode = "404",
      description =
          "Das Board gibt es nicht, es ist archiviert, oder der Aufrufer ist kein Mitglied seines"
              + " Projekts.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/dashboard/delivery")
  DeliveryView delivery(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Boards.", example = "3") @PathVariable
          long boardId) {
    return DeliveryView.of(service.kennzahlen(userId, boardId));
  }
}
