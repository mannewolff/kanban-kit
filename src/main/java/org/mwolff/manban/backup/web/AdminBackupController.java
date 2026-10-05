package org.mwolff.manban.backup.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mwolff.manban.backup.application.BackupStatusService;
import org.mwolff.manban.backup.application.BackupStatusService.BackupStatus;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plattform-Admin: Stand der Sicherung (Issue #826). Session-Auth erforderlich (siehe {@code
 * SecurityConfig}, {@code /api/admin/**}); die Admin-Autorisierung selbst erledigt der {@link
 * BackupStatusService}.
 */
@Tag(name = "Plattform-Verwaltung")
@RestController
class AdminBackupController {

  private final BackupStatusService service;

  AdminBackupController(BackupStatusService service) {
    this.service = service;
  }

  @Operation(
      summary = "Stand der Sicherung lesen",
      description =
          "Liefert den Stand der Sicherung der Installation: ein Gesamturteil (verdict: OK,"
              + " VERALTET, FEHLGESCHLAGEN oder ABGESCHALTET) und je Art der Sicherung — BASIS"
              + " (Basissicherung der Datenbank), WAL (laufendes Archiv des"
              + " Transaktionsprotokolls), SPIEGEL (Spiegel der Datei-Anhänge), OFFSITE"
              + " (verschlüsselte Kopie außer Haus) — den letzten Lauf und ob er zu lange"
              + " zurückliegt. alertMailEnabled sagt, ob ein Alarm per Mail tatsächlich verschickt"
              + " wird; ist er aus, ist diese Ansicht der einzige Weg, einen Ausfall zu sehen.")
  @ApiResponse(responseCode = "200", description = "Der Stand der Sicherung.")
  @ApiResponse(
      responseCode = "403",
      description = "Der Aufrufer ist kein Plattform-Admin.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @GetMapping("/api/admin/backup/status")
  BackupStatus get(@AuthenticationPrincipal Long userId) {
    return service.status(userId);
  }
}
