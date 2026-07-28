package org.mwolff.manban.comment.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link CommentEntity}. */
interface CommentJpaRepository extends JpaRepository<CommentEntity, Long> {

  /**
   * Kommentare der Karte in chronologischer Reihenfolge. Der Tie-Break auf der ID hält die
   * Reihenfolge auch bei identischem Zeitstempel fest (#472) — sonst entscheidet die physische
   * Zeilenlage, die sich durch UPDATE/VACUUM oder einen anderen Ausführungsplan ändert.
   */
  List<CommentEntity> findByCardIdOrderByCreatedAtAscIdAsc(Long cardId);
}
