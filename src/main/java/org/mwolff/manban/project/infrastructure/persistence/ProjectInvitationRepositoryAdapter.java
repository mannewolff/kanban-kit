package org.mwolff.manban.project.infrastructure.persistence;

import java.util.Optional;
import org.mwolff.manban.project.application.ProjectInvitationRepository;
import org.mwolff.manban.project.domain.ProjectInvitation;
import org.springframework.stereotype.Component;

/** Adapter des {@link ProjectInvitationRepository}-Ports auf Spring Data JPA. */
@Component
class ProjectInvitationRepositoryAdapter implements ProjectInvitationRepository {

  private final ProjectInvitationJpaRepository jpa;

  ProjectInvitationRepositoryAdapter(ProjectInvitationJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public ProjectInvitation save(ProjectInvitation invitation) {
    return toDomain(jpa.save(toEntity(invitation)));
  }

  @Override
  public Optional<ProjectInvitation> findByTokenHash(String tokenHash) {
    return jpa.findByTokenHash(tokenHash).map(ProjectInvitationRepositoryAdapter::toDomain);
  }

  private static ProjectInvitationEntity toEntity(ProjectInvitation i) {
    return new ProjectInvitationEntity(
        i.id(),
        i.projectId(),
        i.email(),
        i.role(),
        i.tokenHash(),
        i.expiresAt(),
        i.acceptedAt(),
        i.invitedBy());
  }

  private static ProjectInvitation toDomain(ProjectInvitationEntity e) {
    return new ProjectInvitation(
        e.getId(),
        e.getProjectId(),
        e.getEmail(),
        e.getRole(),
        e.getTokenHash(),
        e.getExpiresAt(),
        e.getAcceptedAt(),
        e.getInvitedBy());
  }
}
