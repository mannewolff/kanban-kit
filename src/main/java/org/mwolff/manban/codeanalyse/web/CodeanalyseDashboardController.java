package org.mwolff.manban.codeanalyse.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mwolff.manban.codeanalyse.application.BefunddichteService;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Platte „Codeanalyse“ des Board-Leitstands an HTTP (Issue #2276, Plan #2272 E9).
 *
 * <p>Ein reiner Aufruf des Leitstands mit Sitzung, darum ohne {@code @ApiVertrag} — wie die übrigen
 * {@code /dashboard}-Aufrufe. Keine eigenen Exceptions: 404 liefert der Service.
 */
@Tag(name = "Codeanalyse")
@RestController
class CodeanalyseDashboardController {

  private final BefunddichteService service;

  CodeanalyseDashboardController(BefunddichteService service) {
    this.service = service;
  }

  @Operation(
      summary = "Befunddichte der Codeanalyse eines Boards lesen",
      description =
          "Liefert die Platte „Codeanalyse“ für das ganze Projekt des Boards: über die zwölf"
              + " Kalenderwochen in UTC bis jetzt je Woche die Funde vor der Codeanalyse und im"
              + " Endzustand je Art und als Summe, die geänderten Zeilen, die Befunddichte je 1.000"
              + " geänderte Zeilen, den Rückgang der Funde in Prozent, die Zahl der zählenden"
              + " Messungen und ihrer Karten sowie die nicht zählenden Messungen je Grund."
              + " Maßgeblich ist der Zeitpunkt der Veröffentlichung; null heißt „keine Grundlage“."
              + " Verlangt die Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Wochenwerte der Platte „Codeanalyse“.")
  @ApiResponse(
      responseCode = "404",
      description =
          "Das Board gibt es nicht, es ist archiviert, oder der Aufrufer ist kein Mitglied seines"
              + " Projekts.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/dashboard/code-analysis")
  CodeAnalysisView codeAnalysis(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Boards.", example = "3") @PathVariable
          long boardId) {
    return CodeAnalysisView.of(service.wochenwerte(userId, boardId));
  }
}
