package org.mwolff.manban.project.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * Mitgliedschaft eines Benutzers in einem Projekt mit zugehöriger Rolle.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param projectId Projekt
 * @param userId Benutzer
 * @param role Projekt-Rolle
 * @param createdAt Beitrittszeitpunkt
 */
public record ProjectMembership(
    @Nullable Long id, Long projectId, Long userId, ProjectRole role, Instant createdAt)
    implements Identifiable {

  public ProjectMembership withRole(ProjectRole newRole) {
    return new ProjectMembership(id, projectId, userId, newRole, createdAt);
  }
}
