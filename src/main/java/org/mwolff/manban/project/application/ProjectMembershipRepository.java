package org.mwolff.manban.project.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.project.domain.ProjectMembership;

/** Ausgehender Port für die Persistenz von Projekt-Mitgliedschaften. */
public interface ProjectMembershipRepository {

    ProjectMembership save(ProjectMembership membership);

    List<ProjectMembership> findByUserId(long userId);

    List<ProjectMembership> findByProjectId(long projectId);

    Optional<ProjectMembership> findByProjectIdAndUserId(long projectId, long userId);

    void deleteById(long membershipId);
}
