package org.mwolff.manban.project.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link ProjectEntity}. */
interface ProjectJpaRepository extends JpaRepository<ProjectEntity, Long> {
}
