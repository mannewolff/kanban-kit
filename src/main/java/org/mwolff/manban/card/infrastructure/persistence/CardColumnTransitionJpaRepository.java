package org.mwolff.manban.card.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring-Data-Repository für {@link CardColumnTransitionEntity}. */
interface CardColumnTransitionJpaRepository
    extends JpaRepository<CardColumnTransitionEntity, Long> {

  List<CardColumnTransitionEntity> findByCardIdOrderByEnteredAt(Long cardId);

  List<CardColumnTransitionEntity> findByCardIdAndLeftAtIsNull(Long cardId);

  @Query(
      value =
          "SELECT t.* FROM card_column_transition t JOIN card c ON c.id = t.card_id "
              + "WHERE c.board_id = ?1 ORDER BY t.card_id, t.entered_at",
      nativeQuery = true)
  List<CardColumnTransitionEntity> findByBoardId(Long boardId);
}
