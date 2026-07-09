package org.mwolff.manban.card.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring-Data-Repository für {@link CardEntity}. */
interface CardJpaRepository extends JpaRepository<CardEntity, Long> {

    List<CardEntity> findByBoardIdOrderByNumber(Long boardId);

    @Query("select c from CardEntity c where c.archived = false "
            + "and c.movedToDoneAt is not null and c.movedToDoneAt < ?1")
    List<CardEntity> findArchivableDoneCards(Instant threshold);

    @Query("select coalesce(max(c.number), 0) from CardEntity c where c.boardId = ?1")
    int maxNumberInBoard(Long boardId);

    @Query("select coalesce(max(c.positionInColumn), -1) from CardEntity c "
            + "where c.columnId = ?1 and c.archived = false")
    int maxActivePositionInColumn(Long columnId);
}
