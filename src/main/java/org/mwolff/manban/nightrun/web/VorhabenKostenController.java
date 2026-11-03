package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.util.List;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.nightrun.application.VorhabenKostenService;
import org.mwolff.manban.nightrun.application.VorhabenKostenService.VorhabenKostenView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Kostenzeile je Vorhaben an HTTP (Issue #2005, Plan #1990 Paket 5, E11): einmal je Board für
 * die Platte „Vorhaben“, einmal je Karte für das Detail eines Vorhabens, weil das Modal nicht immer
 * ein Board kennt.
 *
 * <p>Ein eigener Endpunkt neben {@code epic-forecast}: Die Platte sieht jedes Mitglied, die Kosten
 * nur, wer den Verbrauch sieht (fachlich #1954 AK 10). Ohne {@code zone} — nichts in der Rechnung
 * hängt an einer Zeitzone. Ein reiner Aufruf des Leitstands, darum ohne {@code @ApiVertrag}. Keine
 * eigenen Exceptions: 403 und 404 liefert der Service.
 */
@Tag(name = "Läufe (Runner)")
@RestController
class VorhabenKostenController {

  private static final String BESCHREIBUNG =
      " Ein laufendes Vorhaben trägt die aktuelle Voraussage (Art, typischer Betrag, Spanne,"
          + " Zahl der Pakete, Pakete ohne Aufgabenstufe) oder den Grund für „zu wenig Daten“,"
          + " dazu die bisherigen Kosten und ob Messungen fehlen. Ein Vorhaben mit festgehaltenem"
          + " Abschluss trägt die tatsächlichen Kosten und beide festgehaltenen Voraussagen mit"
          + " ihrer Abweichung in ganzen Prozent; ohne bezifferte Voraussage steht „keine"
          + " Voraussage“. Beträge in USD aus den gemeldeten Kosten der Nachtläufe. Lesen darf, wer"
          + " den Verbrauch des Projekts sieht.";

  private static final String VERBRAUCH_VERBOTEN =
      "Der Aufrufer ist Mitglied, darf den Verbrauch des Projekts aber nicht sehen.";

  private final VorhabenKostenService service;
  private final Clock clock;

  VorhabenKostenController(VorhabenKostenService service, Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @Operation(
      summary = "Kosten und Voraussage je Vorhaben eines Boards lesen",
      description =
          "Liefert für jedes Vorhaben des Boards, archivierte eingeschlossen, die Kostenzeile."
              + BESCHREIBUNG)
  @ApiResponse(responseCode = "200", description = "Die Kostenzeilen, aufsteigend nach Nummer.")
  @ApiResponse(
      responseCode = "403",
      description = VERBRAUCH_VERBOTEN,
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
  @GetMapping("/api/boards/{boardId}/night-run-epic-costs")
  List<VorhabenKostenView> jeBoard(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Kennung des Boards.", example = "12") @PathVariable long boardId) {
    return service.jeBoard(userId, boardId, clock.instant());
  }

  @Operation(
      summary = "Kosten und Voraussage eines Vorhabens lesen",
      description =
          "Liefert die Kostenzeile des Vorhabens mit dieser Karten-ID, auch archiviert."
              + BESCHREIBUNG)
  @ApiResponse(responseCode = "200", description = "Die Kostenzeile des Vorhabens.")
  @ApiResponse(
      responseCode = "403",
      description = VERBRAUCH_VERBOTEN,
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiResponse(
      responseCode = "404",
      description =
          "Die Karte gibt es nicht oder sie ist kein Vorhaben, oder der Aufrufer ist im Projekt"
              + " der Karte kein Mitglied.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @GetMapping("/api/cards/{cardId}/night-run-epic-costs")
  VorhabenKostenView jeVorhaben(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Interne ID der Vorhaben-Karte.", example = "812") @PathVariable
          long cardId) {
    return service.jeVorhaben(userId, cardId, clock.instant());
  }
}
