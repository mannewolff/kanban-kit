package org.mwolff.manban.card.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** Spring-Data-Repository für {@link LabelEntity}. */
interface LabelJpaRepository extends JpaRepository<LabelEntity, Long> {

  List<LabelEntity> findByBoardIdOrderByName(Long boardId);

  boolean existsByBoardIdAndName(Long boardId, String name);

  /**
   * Legt das Label an, wenn es am Board fehlt; liefert die Zahl der angelegten Zeilen (0 oder 1).
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO label (board_id, name, color, count_on_epic_tile) VALUES (?1, ?2, ?3, false)"
              + " ON CONFLICT ON CONSTRAINT uq_label_board_name DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(Long boardId, String name, String color);
}
