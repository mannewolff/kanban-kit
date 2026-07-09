package org.mwolff.manban.project.application;

import java.time.Instant;
import java.util.List;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projekt-Use-Cases. Owner-Isolation läuft über die Mitgliedschaft: Nichtmitglieder
 * bekommen 404 (kein Existenz-Leak). Umbenennen/Löschen wird über den
 * {@link PermissionChecker} anhand der Rollen-Rechte-Matrix durchgesetzt
 * (PROJECT_EDIT / PROJECT_DELETE).
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;
    private final PermissionChecker permissions;

    public ProjectService(ProjectRepository projects, ProjectMembershipRepository memberships,
                          PermissionChecker permissions) {
        this.projects = projects;
        this.memberships = memberships;
        this.permissions = permissions;
    }

    @Transactional
    public ProjectView create(long userId, String name) {
        Instant now = Instant.now();
        Project project = projects.save(new Project(null, name.trim(), userId, now));
        memberships.save(new ProjectMembership(null, project.id(), userId, ProjectRole.OWNER, now));
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

    @Transactional
    public void delete(long userId, long projectId) {
        permissions.require(userId, projectId, Permission.PROJECT_DELETE);
        projects.deleteById(projectId);
    }

    /** Projektdarstellung inkl. der Rolle des anfragenden Benutzers. */
    public record ProjectView(Long id, String name, ProjectRole role, Instant createdAt) {
    }
}
