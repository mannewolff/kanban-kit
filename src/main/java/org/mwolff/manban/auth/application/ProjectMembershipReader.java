package org.mwolff.manban.auth.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Ausgehender Lese-Port für die Projekt-Mitgliedschaften eines Benutzers. (Anlage/Verwaltung von
 * Mitgliedschaften kommt mit den Projekt-Issues P1–P3.)
 */
@FunctionalInterface
public interface ProjectMembershipReader {

  List<Membership> findByUserId(long userId);

  /** Mitgliedschaft eines Benutzers in einem Projekt mit zugehöriger Rolle. */
  @Schema(description = "Mitgliedschaft in einem Projekt.")
  record Membership(
      @Schema(description = "Interne ID des Projekts.", example = "1") long projectId,
      @Schema(description = "Projekt-Rolle des Benutzers.", example = "OWNER") String role) {}
}
