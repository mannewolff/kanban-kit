package org.mwolff.manban.card.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link CardActivityEntity}. */
interface CardActivityJpaRepository extends JpaRepository<CardActivityEntity, Long> {

  List<CardActivityEntity> findByCardIdOrderByCreatedAt(Long cardId);
}
