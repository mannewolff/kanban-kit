package org.mwolff.manban.attachment.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mwolff.manban.attachment.application.StorageReconciliationService;
import org.mwolff.manban.attachment.application.StorageReconciliationService.ReconciliationReport;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plattform-Admin: Abgleich zwischen Anhang-Metadaten und Objektspeicher (Issue #503). Session-Auth
 * erforderlich (siehe {@code SecurityConfig}, {@code /api/admin/**}); die Admin-Autorisierung
 * selbst erledigt der {@link StorageReconciliationService}.
 */
@Tag(name = "Plattform-Verwaltung")
@RestController
class AdminStorageController {

  private final StorageReconciliationService service;

  AdminStorageController(StorageReconciliationService service) {
    this.service = service;
  }

  @Operation(
      summary = "Objektspeicher abgleichen",
      description =
          "Vergleicht die Anhang-Metadaten mit dem Objektspeicher und nennt beide Arten von"
              + " Abweichung: verwaiste Objekte (im Speicher, aber ohne Anhang) und fehlende"
              + " Objekte (Anhang ohne Datei im Speicher; sein Download scheitert). Nur ein"
              + " Bericht — gelöscht wird nichts, denn ein laufender Upload hat kurzzeitig ein"
              + " Objekt ohne gespeicherte Metadaten.")
  @ApiResponse(responseCode = "200", description = "Der Abgleich.")
  @ApiResponse(
      responseCode = "403",
      description = "Der Aufrufer ist kein Plattform-Admin.",
      content =
          @Content(
              mediaType = ApiSchemas.PROBLEM_JSON,
              schema = @Schema(ref = ApiSchemas.PROBLEM_DETAIL_REF)))
  @ApiVertrag(recht = "PLATTFORM_ADMIN")
  @GetMapping("/api/admin/storage/reconciliation")
  ReconciliationReport get(@AuthenticationPrincipal Long userId) {
    return service.report(userId);
  }
}
