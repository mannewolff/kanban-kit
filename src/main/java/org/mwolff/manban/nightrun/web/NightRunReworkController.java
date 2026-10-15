package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.nightrun.application.NightRunReworkService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Die Platte „Nacharbeit“ des Board-Leitstands an HTTP (Issue #1757, Plan #1752 E12).
 *
 * <p>Ein reiner Aufruf des Leitstands, darum ohne {@code @ApiVertrag} — wie die UI-Aufrufe in
 * {@link NightRunNowController}. Keine eigenen Exceptions: 403 und 404 liefert der Service.
 */
@Tag(name = "Läufe (Runner)")
@RestController
class NightRunReworkController {

  private final NightRunReworkService service;

  NightRunReworkController(NightRunReworkService service) {
    this.service = service;
  }

  @Operation(
      summary = "Nacharbeit der Runs an einem Board lesen",
      description =
          "Liefert über die zwölf rollenden Wochenfenster bis jetzt, wie viele Arbeitspakete die"
              + " Runs des Runners aus In Progress nach Ready oder Backlog zurückzogen (packages)"
              + " und an wie viele Fachpläne und Pläne sie kit:clarify oder run:waiting hängten"
              + " (documents) — jeweils gemessen an den Karten des Boards, die die Runs bearbeitet"
              + " haben, mit der Kartenliste und dem Verlauf je Woche (weeks). Es zählen nur"
              + " Spuren, deren Laufkennung zu einem aufbewahrten Nachtlauf des Projekts gehört."
              + " Wochen vor dem Datenbeginn einer Gruppe tragen den Zustand KEINE_DATEN. "
              + NightRunController.LESERECHT)
  @ApiResponse(responseCode = "200", description = "Die Nacharbeit.")
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
  @GetMapping("/api/boards/{boardId}/night-run-rework")
  NightRunReworkView rework(
      @AuthenticationPrincipal Long userId,
      @Parameter(description = "Kennung des Boards.", example = "12") @PathVariable long boardId) {
    return NightRunReworkView.of(service.nacharbeit(userId, boardId));
  }
}
