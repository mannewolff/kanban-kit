package org.mwolff.manban.auth.application;

import java.util.List;

/**
 * Ausgehender Lese-Port für die Projekt-Mitgliedschaften eines Benutzers. (Anlage/Verwaltung von
 * Mitgliedschaften kommt mit den Projekt-Issues P1–P3.)
 */
@FunctionalInterface
public interface ProjectMembershipReader {

  List<Membership> findByUserId(long userId);

  /** Mitgliedschaft eines Benutzers in einem Projekt mit zugehöriger Rolle. */
  record Membership(long projectId, String role) {}
}
