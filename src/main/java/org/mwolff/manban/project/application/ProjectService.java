package org.mwolff.manban.project.application;

import java.time.Instant;
import java.util.List;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projekt-Use-Cases. Owner-Isolation läuft über die Mitgliedschaft: Nichtmitglieder
 * bekommen 404 (kein Existenz-Leak). Umbenennen/Löschen erfordert die Rolle OWNER
 * (die feingranulare RBAC-Durchsetzung folgt mit P2).
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMembershipRepository memberships;

    public ProjectService(ProjectRepository projects, ProjectMembershipRepository memberships) {
        this.projects = projects;
        this.memberships = memberships;
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
        return memberships.findByUserId(userId).stream()
                .map(m -> projects.findById(m.projectId())
                        .map(p -> new ProjectView(p.id(), p.name(), m.role(), p.createdAt()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public ProjectView rename(long userId, long projectId, String newName) {
        ProjectMembership membership = requireOwner(userId, projectId);
        Project project = projects.findById(projectId).orElseThrow(ProjectNotFoundException::new);
        Project renamed = projects.save(project.withName(newName.trim()));
        return new ProjectView(renamed.id(), renamed.name(), membership.role(), renamed.createdAt());
    }

    @Transactional
    public void delete(long userId, long projectId) {
        requireOwner(userId, projectId);
        projects.deleteById(projectId);
    }

    private ProjectMembership requireOwner(long userId, long projectId) {
        ProjectMembership membership = memberships.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(ProjectNotFoundException::new);
        if (membership.role() != ProjectRole.OWNER) {
            throw new ProjectAccessDeniedException();
        }
        return membership;
    }

    /** Projektdarstellung inkl. der Rolle des anfragenden Benutzers. */
    public record ProjectView(Long id, String name, ProjectRole role, Instant createdAt) {
    }
}
