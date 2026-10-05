package org.mwolff.manban.project.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.mwolff.manban.project.application.RoleMatrixService;
import org.mwolff.manban.project.application.RoleMatrixService.RoleMatrixView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stellt die feste Rollen-Rechte-Matrix bereit — Quelle der Wahrheit für die {@code
 * /roles}-Ansicht. Nur die Anzeige; die Matrix selbst ist statisch geseedet (konfigurierbar erst
 * mit 2.0).
 */
@Tag(
    name = "Rollen",
    description =
        "Die Projekt-Rollen (OWNER, ADMIN, MEMBER, VIEWER) und die Rechte, die jede umfasst."
            + " Die Rechte-Namen (etwa TICKET_UPDATE) sind dieselben, die andere Aufrufe als"
            + " verlangtes Recht nennen.")
@RestController
@RequestMapping("/api/roles")
class RolesController {

  private final RoleMatrixService roleMatrix;

  RolesController(RoleMatrixService roleMatrix) {
    this.roleMatrix = roleMatrix;
  }

  @Operation(
      summary = "Rollen-Rechte-Matrix lesen",
      description =
          "Liefert die feste Zuordnung von Projekt-Rollen zu Rechten. Sie ist für alle Projekte"
              + " gleich und lässt sich nicht ändern.")
  @ApiResponse(responseCode = "200", description = "Die Rollen-Rechte-Matrix.")
  @GetMapping("/matrix")
  RoleMatrixView matrix() {
    return roleMatrix.matrix();
  }
}
