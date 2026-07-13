package org.mwolff.manban.card.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link CardColumnTransitionEntity}. */
interface CardColumnTransitionJpaRepository
    extends JpaRepository<CardColumnTransitionEntity, Long> {

  List<CardColumnTransitionEntity> findByCardIdOrderByEnteredAt(Long cardId);

  List<CardColumnTransitionEntity> findByCardIdAndLeftAtIsNull(Long cardId);
}
