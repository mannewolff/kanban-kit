package org.mwolff.manban.card.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.AgingWipView;
import org.mwolff.manban.card.application.BoardDashboardKpis;
import org.mwolff.manban.card.application.BoardFlowView;
import org.mwolff.manban.card.application.BoardFlussService;
import org.mwolff.manban.card.application.CardCycleTimeService;
import org.mwolff.manban.card.application.EpicForecastView;
import org.mwolff.manban.card.application.ImplementationTimeView;
import org.mwolff.manban.card.application.VorhabenPrognoseService;
import org.mwolff.manban.common.web.Regionszone;
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

  private static final String NICHT_GEFUNDEN =
      "Das Board gibt es nicht, es ist archiviert, oder der Aufrufer ist kein Mitglied seines"
          + " Projekts.";

  private final CardCycleTimeService kennzahlen;
  private final BoardFlussService fluss;
  private final VorhabenPrognoseService prognose;

  DashboardController(
      CardCycleTimeService kennzahlen, BoardFlussService fluss, VorhabenPrognoseService prognose) {
    this.kennzahlen = kennzahlen;
    this.fluss = fluss;
    this.prognose = prognose;
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

  @Operation(
      summary = "Fluss eines Boards lesen",
      description =
          "Liefert die Platte „Fluss“: für die 84 Kalendertage bis einschließlich heute in der"
              + " übergebenen Zone je Tag die Zahl der Arbeitspakete in Backlog, Ready, In Progress"
              + " und In Review am Tagesende, heute zum Stand jetzt, und die seit Beginn des"
              + " Zeitraums fertig gewordenen. Ein Tag ohne sicher bestimmbaren Stand trägt known ="
              + " false und keine Zahlen; dataSince nennt dann den ersten Tag mit Daten. Verlangt"
              + " die Mitgliedschaft im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Tage der Platte „Fluss“.")
  @ApiResponse(
      responseCode = "400",
      description = "zone fehlt, ist unbekannt oder ein fester Versatz statt einer Regionszone.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = NICHT_GEFUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/dashboard/flow")
  BoardFlowView flow(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Boards.", example = "3") @PathVariable long boardId,
      @Parameter(
              description =
                  "Regionszone des Lesers, in der die Kalendertage beginnen und enden; ein fester"
                      + " Versatz wie +05:30 wird abgewiesen.",
              example = "Europe/Berlin")
          @RequestParam
          ZoneId zone) {
    return fluss.flow(userId, boardId, Regionszone.of(zone));
  }

  @Operation(
      summary = "Alter in Arbeit eines Boards lesen",
      description =
          "Liefert die Platte „Alter in Arbeit“: je Zustand Ready, In Progress und In Review die"
              + " Arbeitspakete nach der Zeit seit ihrem letzten Eintritt, die älteste zuerst, die"
              + " Zahl der Vergleichswerte der letzten zwölf Wochen und die 85-%-Schwelle in"
              + " Sekunden (null unter fünf Vergleichswerten). Verlangt die Mitgliedschaft im"
              + " Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Zustände der Platte „Alter in Arbeit“.")
  @ApiResponse(
      responseCode = "404",
      description = NICHT_GEFUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/dashboard/aging")
  AgingWipView aging(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Boards.", example = "3") @PathVariable
          long boardId) {
    return fluss.aging(userId, boardId);
  }

  @Operation(
      summary = "Prognose der Vorhaben eines Boards lesen",
      description =
          "Liefert die Prognose der Platte „Vorhaben“: je nicht archiviertem Vorhaben die Lage,"
              + " die Zahl der Arbeitspakete (In Review zählt als fertig) und bei der Lage FORECAST"
              + " die Tage, bis zu denen mit 50 % und 85 % alle offenen Pakete umgesetzt sind."
              + " Grundlage ist der Durchsatz des Boards der letzten 30 abgeschlossenen Tage; ein"
              + " Tag reicht von 12 bis 12 Uhr in der übergebenen Zone und trägt das Datum seines"
              + " Endes, der laufende Tag ist der erste vorhergesagte. Verlangt die Mitgliedschaft"
              + " im Projekt.")
  @ApiResponse(responseCode = "200", description = "Die Prognose der Platte „Vorhaben“.")
  @ApiResponse(
      responseCode = "400",
      description = "zone fehlt, ist unbekannt oder ein fester Versatz statt einer Regionszone.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description = NICHT_GEFUNDEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/boards/{boardId}/dashboard/epic-forecast")
  EpicForecastView epicForecast(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID des Boards.", example = "3") @PathVariable long boardId,
      @Parameter(
              description =
                  "Regionszone des Lesers, in der die Tage um 12 Uhr beginnen und enden; ein fester"
                      + " Versatz wie +05:30 wird abgewiesen.",
              example = "Europe/Berlin")
          @RequestParam
          ZoneId zone) {
    return prognose.epicForecast(userId, boardId, Regionszone.of(zone));
  }
}
