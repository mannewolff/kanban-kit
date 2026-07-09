package org.mwolff.manban.project.application;

import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Zentrale Rechteprüfung: Hat der Benutzer das Recht X im Projekt Y? Grundlage ist
 * die Projekt-Mitgliedschaft (Rolle) plus die feste Rollen-Rechte-Matrix.
 *
 * <p>Alle projekt-/board-/karten-verändernden Use-Cases rufen {@link #require} auf.
 * Nichtmitglieder erhalten 404 (kein Existenz-Leak), Mitglieder ohne das Recht 403.
 */
@Component
public class PermissionChecker {

    private final ProjectMembershipRepository memberships;
    private final RolePermissionRepository rolePermissions;

    public PermissionChecker(ProjectMembershipRepository memberships, RolePermissionRepository rolePermissions) {
        this.memberships = memberships;
        this.rolePermissions = rolePermissions;
    }

    /**
     * Stellt sicher, dass der Benutzer das Recht im Projekt hat.
     *
     * @return die Mitgliedschaft des Benutzers (u. a. für die Rolle in Responses)
     * @throws ProjectNotFoundException     wenn der Benutzer kein Mitglied ist (404)
     * @throws ProjectAccessDeniedException wenn die Rolle das Recht nicht umfasst (403)
     */
    @Transactional(readOnly = true)
    public ProjectMembership require(long userId, long projectId, Permission permission) {
        ProjectMembership membership = memberships.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(ProjectNotFoundException::new);
        if (!rolePermissions.isGranted(membership.role(), permission)) {
            throw new ProjectAccessDeniedException();
        }
        return membership;
    }
}
