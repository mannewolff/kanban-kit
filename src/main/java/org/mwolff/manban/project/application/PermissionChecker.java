package org.mwolff.manban.project.application;

import java.time.Clock;
import java.util.Optional;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Zentrale Rechteprüfung: Hat der Benutzer das Recht X im Projekt Y? Grundlage ist die
 * Projekt-Mitgliedschaft (Rolle) plus die feste Rollen-Rechte-Matrix.
 *
 * <p>Ein <b>Plattform-Admin</b> ({@link org.mwolff.manban.auth.domain.PlatformRole#ADMIN}) ist
 * Super-User: er passiert jede Prüfung, auch ohne Mitgliedschaft im Projekt. Für Nicht-Admins gilt:
 * alle projekt-/board-/karten-verändernden Use-Cases rufen {@link #require} auf. Nichtmitglieder
 * erhalten 404 (kein Existenz-Leak), Mitglieder ohne das Recht 403.
 */
@Component
public class PermissionChecker {

  private final ProjectMembershipRepository memberships;
  private final RolePermissionRepository rolePermissions;
  private final PlatformAdminChecker platformAdminChecker;
  private final Clock clock;

  public PermissionChecker(
      ProjectMembershipRepository memberships,
      RolePermissionRepository rolePermissions,
      PlatformAdminChecker platformAdminChecker,
      Clock clock) {
    this.memberships = memberships;
    this.rolePermissions = rolePermissions;
    this.platformAdminChecker = platformAdminChecker;
    this.clock = clock;
  }

  /** Ob der Benutzer plattformweit Admin (Super-User) ist. */
  @Transactional(readOnly = true)
  public boolean isPlatformAdmin(long userId) {
    return platformAdminChecker.isPlatformAdmin(userId);
  }

  /**
   * Prüft (ohne zu werfen), ob der Benutzer das Recht im Projekt hat. Ein Plattform-Admin hat immer
   * Zugriff; ansonsten entscheidet die Projekt-Mitgliedschaft plus Rollen-Rechte-Matrix.
   * Nichtmitglieder erhalten {@code false} (kein Werfen, kein Existenz-Leak) — der Aufrufer
   * entscheidet über die Reaktion (z. B. 403 statt 404 bei der Token-Bindung).
   */
  @Transactional(readOnly = true)
  public boolean hasPermission(long userId, long projectId, Permission permission) {
    return platformAdminChecker.isPlatformAdmin(userId)
        || memberships
            .findByProjectIdAndUserId(projectId, userId)
            .map(m -> rolePermissions.isGranted(m.role(), permission))
            .orElse(false);
  }

  /**
   * Stellt sicher, dass der Benutzer das Recht im Projekt hat.
   *
   * @return die Mitgliedschaft des Benutzers (u. a. für die Rolle in Responses); für einen
   *     Plattform-Admin ohne echte Mitgliedschaft eine synthetische OWNER-Rolle
   * @throws ProjectNotFoundException wenn der Benutzer kein Mitglied und kein Admin ist (404)
   * @throws ProjectAccessDeniedException wenn die Rolle das Recht nicht umfasst (403)
   */
  @Transactional(readOnly = true)
  public ProjectMembership require(long userId, long projectId, Permission permission) {
    Optional<ProjectMembership> membership =
        memberships.findByProjectIdAndUserId(projectId, userId);
    if (platformAdminChecker.isPlatformAdmin(userId)) {
      return membership.orElseGet(() -> adminMembership(projectId, userId));
    }
    ProjectMembership m = membership.orElseThrow(ProjectNotFoundException::new);
    if (!rolePermissions.isGranted(m.role(), permission)) {
      throw new ProjectAccessDeniedException();
    }
    return m;
  }

  /**
   * Stellt sicher, dass der Benutzer im Projekt die Rolle OWNER hat (oder Plattform-Admin ist).
   * Nichtmitglied → 404 (kein Existenz-Leak); Mitglied ohne OWNER-Rolle → 403.
   */
  @Transactional(readOnly = true)
  public void requireOwner(long userId, long projectId) {
    if (platformAdminChecker.isPlatformAdmin(userId)) {
      return;
    }
    ProjectMembership m =
        memberships
            .findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(ProjectNotFoundException::new);
    if (m.role() != ProjectRole.OWNER) {
      throw new ProjectAccessDeniedException();
    }
  }

  /**
   * Stellt nur die Projekt-Mitgliedschaft sicher (für Lesezugriffe, die keiner speziellen
   * Berechtigung bedürfen). Nichtmitglied → 404 (kein Existenz-Leak); ein Plattform-Admin passiert
   * immer.
   */
  @Transactional(readOnly = true)
  public ProjectMembership requireMembership(long userId, long projectId) {
    Optional<ProjectMembership> membership =
        memberships.findByProjectIdAndUserId(projectId, userId);
    if (platformAdminChecker.isPlatformAdmin(userId)) {
      return membership.orElseGet(() -> adminMembership(projectId, userId));
    }
    return membership.orElseThrow(ProjectNotFoundException::new);
  }

  /**
   * Synthetische Mitgliedschaft für einen Plattform-Admin ohne echte Mitgliedschaft (Vollzugriff).
   */
  private ProjectMembership adminMembership(long projectId, long userId) {
    return new ProjectMembership(null, projectId, userId, ProjectRole.OWNER, clock.instant());
  }
}
