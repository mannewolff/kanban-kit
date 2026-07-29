package org.mwolff.manban.attachment.web;

import org.mwolff.manban.attachment.application.StorageReconciliationService;
import org.mwolff.manban.attachment.application.StorageReconciliationService.ReconciliationReport;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plattform-Admin: Abgleich zwischen Anhang-Metadaten und Objektspeicher (Issue #503). Session-Auth
 * erforderlich (siehe {@code SecurityConfig}, {@code /api/admin/**}); die Admin-Autorisierung
 * selbst erledigt der {@link StorageReconciliationService}.
 */
@RestController
class AdminStorageController {

  private final StorageReconciliationService service;

  AdminStorageController(StorageReconciliationService service) {
    this.service = service;
  }

  @GetMapping("/api/admin/storage/reconciliation")
  ReconciliationReport get(@AuthenticationPrincipal Long userId) {
    return service.report(userId);
  }
}
