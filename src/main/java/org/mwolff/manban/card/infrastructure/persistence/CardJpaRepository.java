package org.mwolff.manban.card.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring-Data-Repository für {@link CardEntity}. */
interface CardJpaRepository extends JpaRepository<CardEntity, Long> {

  /** Aktive (nicht gelöschte) Karten des Boards. */
  List<CardEntity> findByBoardIdAndDeletedAtIsNullOrderByNumber(Long boardId);

  /** Ideen-Karten eines Projekts (board-los + Legacy), neueste zuerst. */
  List<CardEntity> findByProjectIdAndIdeaStoredTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
      Long projectId);

  /** Karten im Papierkorb des Boards. */
  List<CardEntity> findByBoardIdAndDeletedAtIsNotNullOrderByNumber(Long boardId);

  /** Karten, die vor dem Zeitpunkt gelöscht wurden (Papierkorb-Retention). */
  List<CardEntity> findByDeletedAtNotNullAndDeletedAtBefore(Instant threshold);

  @Query(
      "select c from CardEntity c where c.archived = false and c.deletedAt is null "
          + "and c.movedToDoneAt is not null and c.movedToDoneAt < ?1")
  List<CardEntity> findArchivableDoneCards(Instant threshold);

  @Query("select coalesce(max(c.number), 0) from CardEntity c where c.projectId = ?1")
  int maxNumberInProject(Long projectId);

  @Query(
      "select coalesce(max(c.positionInColumn), -1) from CardEntity c "
          + "where c.columnId = ?1 and c.archived = false and c.ideaStored = false "
          + "and c.deletedAt is null and c.type <> 'EPIC'")
  int maxActivePositionInColumn(Long columnId);
}
