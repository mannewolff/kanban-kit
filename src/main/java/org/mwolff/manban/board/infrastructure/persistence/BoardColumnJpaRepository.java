package org.mwolff.manban.board.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link BoardColumnEntity}. */
interface BoardColumnJpaRepository extends JpaRepository<BoardColumnEntity, Long> {

  List<BoardColumnEntity> findByBoardIdOrderByPosition(Long boardId);
}
