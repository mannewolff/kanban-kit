package org.mwolff.manban.project.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projekt-Use-Cases. <b>Anlegen und Löschen sind System-Admins vorbehalten</b>
 * (Plattform-Rolle ADMIN); beim Anlegen bestimmt der Admin den Owner per E-Mail.
 * Umbenennen läuft über den {@link PermissionChecker} (PROJECT_EDIT, i. d. R. Owner).
 * Owner-Isolation beim Lesen über die Mitgliedschaft: Nichtmitglieder bekommen 404.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;
    private final PermissionChecker permissions;
    private final AppUserRepository users;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, ProjectMembershipRepository memberships,
                          PermissionChecker permissions, AppUserRepository users, Clock clock) {
        this.projects = projects;
        this.memberships = memberships;
        this.permissions = permissions;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Legt ein Projekt an — nur für System-Admins. Der Owner wird per E-Mail bestimmt und
     * als OWNER-Mitglied eingetragen.
     *
     * @throws ProjectAccessDeniedException    wenn der Aufrufer kein System-Admin ist (403)
     * @throws ProjectOwnerNotFoundException   wenn zur Owner-E-Mail kein Nutzer existiert (400)
     */
    @Transactional
    public ProjectView create(long adminUserId, String name, String ownerEmail) {
        requirePlatformAdmin(adminUserId);
        AppUser owner = users.findByEmail(ownerEmail.trim())
                .orElseThrow(() -> new ProjectOwnerNotFoundException(ownerEmail));

        Instant now = clock.instant();
        Project project = projects.save(new Project(null, name.trim(), owner.id(), now));
        memberships.save(new ProjectMembership(null, project.id(), owner.id(), ProjectRole.OWNER, now));
        return new ProjectView(project.id(), project.name(), ProjectRole.OWNER, project.createdAt());
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list(long userId) {
        // Plattform-Admin: alle Projekte. Rolle = eigene Mitgliedschaft, sonst OWNER (Vollzugriff).
        if (permissions.isPlatformAdmin(userId)) {
            java.util.Map<Long, ProjectRole> ownRoles = memberships.findByUserId(userId).stream()
                    .collect(java.util.stream.Collectors.toMap(ProjectMembership::projectId, ProjectMembership::role));
            return projects.findAll().stream()
                    .map(p -> new ProjectView(p.id(), p.name(),
                            ownRoles.getOrDefault(p.id(), ProjectRole.OWNER), p.createdAt()))
                    .toList();
        }
        return memberships.findByUserId(userId).stream()
                .map(m -> projects.findById(m.projectId())
                        .map(p -> new ProjectView(p.id(), p.name(), m.role(), p.createdAt()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public ProjectView rename(long userId, long projectId, String newName) {
        ProjectMembership membership = permissions.require(userId, projectId, Permission.PROJECT_EDIT);
        Project project = projects.findById(projectId).orElseThrow(ProjectNotFoundException::new);
        Project renamed = projects.save(project.withName(newName.trim()));
        return new ProjectView(renamed.id(), renamed.name(), membership.role(), renamed.createdAt());
    }

    /**
     * Löscht ein Projekt (kaskadiert Boards/Epics/Tickets über die DB) — nur für System-Admins.
     *
     * @throws ProjectAccessDeniedException wenn der Aufrufer kein System-Admin ist (403)
     */
    @Transactional
    public void delete(long adminUserId, long projectId) {
        requirePlatformAdmin(adminUserId);
        projects.deleteById(projectId);
    }

    /** Stellt sicher, dass der Aufrufer System-Admin ist (sonst 403). */
    private void requirePlatformAdmin(long userId) {
        if (!permissions.isPlatformAdmin(userId)) {
            throw new ProjectAccessDeniedException();
        }
    }

    /** Projektdarstellung inkl. der Rolle des anfragenden Benutzers. */
    public record ProjectView(Long id, String name, ProjectRole role, Instant createdAt) {
    }
}
