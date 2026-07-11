package org.mwolff.manban.project.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link ProjectMembershipEntity}. */
interface ProjectMembershipJpaRepository extends JpaRepository<ProjectMembershipEntity, Long> {

  List<ProjectMembershipEntity> findByUserId(Long userId);

  List<ProjectMembershipEntity> findByProjectId(Long projectId);

  Optional<ProjectMembershipEntity> findByProjectIdAndUserId(Long projectId, Long userId);
}
