package org.mwolff.manban.card.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring-Data-Repository für {@link CardEntity}. */
interface CardJpaRepository extends JpaRepository<CardEntity, Long> {

  /** Aktive (nicht gelöschte) Karten des Boards. */
  List<CardEntity> findByBoardIdAndDeletedAtIsNullOrderByNumber(Long boardId);

  List<CardEntity> findByProjectIdAndDeletedAtIsNull(Long projectId);

  /** Nicht-gelöschte Karte eines Projekts nach projektweiter Nummer (projektweit eindeutig). */
  Optional<CardEntity> findByProjectIdAndNumberAndDeletedAtIsNull(Long projectId, Integer number);

  /**
   * Ideen-Karten eines Projekts (board-los + Legacy), <b>älteste zuerst</b> (#419): Der Pool wird
   * von oben nach unten abgearbeitet, und das Backlog daneben ist aufsteigend sortiert — bei
   * absteigendem Pool kehrte sich die Reihenfolge beim Einplanen um.
   */
  List<CardEntity> findByProjectIdAndIdeaStoredTrueAndDeletedAtIsNullOrderByCreatedAtAsc(
      Long projectId);

  /** Karten im Papierkorb des Boards. */
  List<CardEntity> findByBoardIdAndDeletedAtIsNotNullOrderByNumber(Long boardId);

  /** Karten, die vor dem Zeitpunkt gelöscht wurden (Papierkorb-Retention). */
  List<CardEntity> findByDeletedAtNotNullAndDeletedAtBefore(Instant threshold);

  @Query(
      "select c from CardEntity c where c.archived = false and c.deletedAt is null "
          + "and c.movedToDoneAt is not null and c.movedToDoneAt < ?1")
  List<CardEntity> findArchivableDoneCards(Instant threshold);

  @Query(
      "select coalesce(max(c.positionInColumn), -1) from CardEntity c "
          + "where c.columnId = ?1 and c.archived = false and c.ideaStored = false "
          + "and c.deletedAt is null and c.type <> 'EPIC'")
  int maxActivePositionInColumn(Long columnId);
}
