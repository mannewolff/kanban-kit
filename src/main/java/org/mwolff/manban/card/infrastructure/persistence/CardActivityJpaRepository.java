package org.mwolff.manban.card.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository für {@link CardActivityEntity}. */
interface CardActivityJpaRepository extends JpaRepository<CardActivityEntity, Long> {

  List<CardActivityEntity> findByCardIdOrderByCreatedAt(Long cardId);

  /**
   * Die Aktivitäten eines Nachtlaufs im Projekt (Issue #1373). Das Projekt kommt über die Karte —
   * {@code card_activity} trägt es nicht selbst.
   */
  @Query(
      "select a from CardActivityEntity a, CardEntity c"
          + " where c.id = a.cardId and c.projectId = :projectId"
          + " and a.origin = 'TOKEN' and a.tokenName = :tokenName and a.agent is not null"
          + " and a.createdAt >= :von and a.createdAt <= :bis"
          + " order by a.createdAt, a.id")
  List<CardActivityEntity> findTokenActivitiesInWindow(
      @Param("projectId") Long projectId,
      @Param("tokenName") String tokenName,
      @Param("von") Instant von,
      @Param("bis") Instant bis);
}
