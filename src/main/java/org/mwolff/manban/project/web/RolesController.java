package org.mwolff.manban.project.web;

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
@RestController
@RequestMapping("/api/roles")
class RolesController {

  private final RoleMatrixService roleMatrix;

  RolesController(RoleMatrixService roleMatrix) {
    this.roleMatrix = roleMatrix;
  }

  @GetMapping("/matrix")
  RoleMatrixView matrix() {
    return roleMatrix.matrix();
  }
}
