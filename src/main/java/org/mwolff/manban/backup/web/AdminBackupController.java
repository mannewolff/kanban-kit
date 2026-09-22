package org.mwolff.manban.backup.web;

import org.mwolff.manban.backup.application.BackupStatusService;
import org.mwolff.manban.backup.application.BackupStatusService.BackupStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plattform-Admin: Stand der Sicherung (Issue #826). Session-Auth erforderlich (siehe {@code
 * SecurityConfig}, {@code /api/admin/**}); die Admin-Autorisierung selbst erledigt der {@link
 * BackupStatusService}.
 */
@RestController
class AdminBackupController {

  private final BackupStatusService service;

  AdminBackupController(BackupStatusService service) {
    this.service = service;
  }

  @GetMapping("/api/admin/backup/status")
  BackupStatus get(@AuthenticationPrincipal Long userId) {
    return service.status(userId);
  }
}
