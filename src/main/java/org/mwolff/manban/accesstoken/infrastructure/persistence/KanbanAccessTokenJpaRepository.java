package org.mwolff.manban.accesstoken.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link KanbanAccessTokenEntity}. */
interface KanbanAccessTokenJpaRepository extends JpaRepository<KanbanAccessTokenEntity, Long> {

    List<KanbanAccessTokenEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<KanbanAccessTokenEntity> findByTokenHash(String tokenHash);
}
