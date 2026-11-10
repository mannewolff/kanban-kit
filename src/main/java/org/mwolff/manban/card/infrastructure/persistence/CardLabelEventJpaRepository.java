package org.mwolff.manban.card.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository für {@link CardLabelEventEntity}. */
interface CardLabelEventJpaRepository extends JpaRepository<CardLabelEventEntity, Long> {

  /** Ereignisse der Karten mit Laufkennung ab {@code von} (Issue #1754). */
  @Query(
      "select e from CardLabelEventEntity e"
          + " where e.cardId in :cardIds and e.runStartedAt is not null and e.runStartedAt >= :von"
          + " order by e.createdAt, e.id")
  List<CardLabelEventEntity> findLaufEreignisse(
      @Param("cardIds") Collection<Long> cardIds, @Param("von") Instant von);

  /** Ereignisse mit einem der Label-Namen ab {@code seit}, mit und ohne Lauf (Issue #2075). */
  @Query(
      "select e from CardLabelEventEntity e"
          + " where e.labelName in :labelNamen and e.createdAt >= :seit"
          + " order by e.createdAt, e.id")
  List<CardLabelEventEntity> findNachLabelNamenSeit(
      @Param("labelNamen") Collection<String> labelNamen, @Param("seit") Instant seit);

  /** Ereignisse der Karten, mit und ohne Lauf (Issue #2110). */
  @Query(
      "select e from CardLabelEventEntity e"
          + " where e.cardId in :cardIds"
          + " order by e.createdAt, e.id")
  List<CardLabelEventEntity> findMitPerson(@Param("cardIds") Collection<Long> cardIds);
}
