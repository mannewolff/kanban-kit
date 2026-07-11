package org.mwolff.manban.board.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link BoardEntity}. */
interface BoardJpaRepository extends JpaRepository<BoardEntity, Long> {

  List<BoardEntity> findByProjectIdOrderByCreatedAt(Long projectId);
}
