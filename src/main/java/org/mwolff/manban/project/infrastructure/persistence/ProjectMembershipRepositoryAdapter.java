package org.mwolff.manban.project.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.stereotype.Component;

/** Adapter des {@link ProjectMembershipRepository}-Ports auf Spring Data JPA. */
@Component
class ProjectMembershipRepositoryAdapter implements ProjectMembershipRepository {

  private final ProjectMembershipJpaRepository jpa;

  ProjectMembershipRepositoryAdapter(ProjectMembershipJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public ProjectMembership save(ProjectMembership membership) {
    return toDomain(jpa.save(toEntity(membership)));
  }

  @Override
  public List<ProjectMembership> findByUserId(long userId) {
    return jpa.findByUserId(userId).stream()
        .map(ProjectMembershipRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<ProjectMembership> findByProjectId(long projectId) {
    return jpa.findByProjectId(projectId).stream()
        .map(ProjectMembershipRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public Optional<ProjectMembership> findByProjectIdAndUserId(long projectId, long userId) {
    return jpa.findByProjectIdAndUserId(projectId, userId)
        .map(ProjectMembershipRepositoryAdapter::toDomain);
  }

  @Override
  public void deleteById(long membershipId) {
    jpa.deleteById(membershipId);
  }

  @Override
  public List<Long> lockOwnerUserIds(long projectId) {
    // Erst sperren, dann lesen: Die zweite Abfrage sieht damit garantiert den Stand, den keine
    // gleichzeitige Transaktion mehr verändern kann, solange diese hier läuft.
    jpa.lockIdsByProjectId(projectId);
    return jpa.findUserIdsByProjectIdAndRole(projectId, ProjectRole.OWNER.name());
  }

  private static ProjectMembershipEntity toEntity(ProjectMembership m) {
    return new ProjectMembershipEntity(m.id(), m.projectId(), m.userId(), m.role(), m.createdAt());
  }

  private static ProjectMembership toDomain(ProjectMembershipEntity e) {
    return new ProjectMembership(
        e.getId(), e.getProjectId(), e.getUserId(), e.getRole(), e.getCreatedAt());
  }
}
