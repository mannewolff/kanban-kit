package org.mwolff.manban.project.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link ProjectInvitationEntity}. */
interface ProjectInvitationJpaRepository extends JpaRepository<ProjectInvitationEntity, Long> {

    Optional<ProjectInvitationEntity> findByTokenHash(String tokenHash);
}
