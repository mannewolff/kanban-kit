package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.BoardDashboardKpis;
import org.mwolff.manban.card.application.CardCycleTimeService;
import org.mwolff.manban.card.application.ImplementationTimeView;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Liefert die Kennzahlen eines Boards für das Dashboard (Leserecht wie Board-Ansicht). */
@Tag(name = "Karten")
@RestController
class DashboardController {

  private final CardCycleTimeService kennzahlen;

  DashboardController(CardCycleTimeService kennzahlen) {
    this.kennzahlen = kennzahlen;
  }

  @Operation(
      summary = "Kennzahlen eines Boards lesen",
      description =
          "Liefert die Kennzahlen des Boards für das Dashboard: Verweildauer je Spalte, Durchsatz"
              + " der letzten zwölf Wochen, durchschnittliche Durchlauf- und Umsetzungszeit samt"
              + " Stichprobengröße, die Umsetzungszeit je Woche in denselben zwölf Fenstern wie der"
              + " Durchsatz und Ausreißer. Dauern in Sekunden; null heißt „keine"
              + " Datenbasis“. Verlangt die Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Kennzahlen des Boards.")
  @ApiResponse(
      responseCode = "404",
      description =
          "Das Board gibt es nicht, es ist archiviert, oder der Aufrufer ist kein Mitglied seines"
              + " Projekts.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/dashboard")
  BoardDashboardKpis dashboard(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Boards.", example = "3") @PathVariable
          long boardId) {
    return kennzahlen.dashboard(userId, boardId);
  }

  @Operation(
      summary = "Umsetzungszeit eines Boards in einem Zeitraum lesen",
      description =
          "Liefert die durchschnittliche Umsetzungszeit der Karten des Boards, die im Zeitraum"
              + " from (einschließlich) bis to (ausschließlich) fertig wurden, samt Zahl der"
              + " Karten. Ohne Grenzen gilt der Wert über alle gemessenen Karten des Boards und"
              + " gleicht dem des Kennzahlen-Abrufs. Dauer in Sekunden; null heißt „keine"
              + " Datenbasis“. Verlangt die Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Umsetzungszeit im Zeitraum.")
  @ApiResponse(
      responseCode = "400",
      description = "Nur eine der beiden Grenzen ist gesetzt, oder from liegt nicht vor to.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description =
          "Das Board gibt es nicht, es ist archiviert, oder der Aufrufer ist kein Mitglied seines"
              + " Projekts.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/dashboard/implementation-time")
  ImplementationTimeView implementationTime(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Boards.", example = "3") @PathVariable long boardId,
      @Parameter(
              description = "Beginn des Zeitraums (einschließlich), nur zusammen mit to.",
              example = "2026-10-01T00:00:00Z")
          @RequestParam(required = false)
          @Nullable Instant from,
      @Parameter(
              description = "Ende des Zeitraums (ausschließlich), nur zusammen mit from.",
              example = "2026-10-08T00:00:00Z")
          @RequestParam(required = false)
          @Nullable Instant to) {
    return kennzahlen.implementationTime(userId, boardId, from, to);
  }
}
