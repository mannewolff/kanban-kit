package org.mwolff.manban.card.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link LabelEntity}. */
interface LabelJpaRepository extends JpaRepository<LabelEntity, Long> {

  List<LabelEntity> findByBoardIdOrderByName(Long boardId);

  boolean existsByBoardIdAndName(Long boardId, String name);
}
