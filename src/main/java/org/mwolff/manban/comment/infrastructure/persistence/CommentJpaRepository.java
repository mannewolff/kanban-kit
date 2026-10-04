package org.mwolff.manban.comment.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository für {@link CommentEntity}. */
interface CommentJpaRepository extends JpaRepository<CommentEntity, Long> {

  /**
   * Kommentare der Karte in chronologischer Reihenfolge. Der Tie-Break auf der ID hält die
   * Reihenfolge auch bei identischem Zeitstempel fest (#472) — sonst entscheidet die physische
   * Zeilenlage, die sich durch UPDATE/VACUUM oder einen anderen Ausführungsplan ändert.
   */
  List<CommentEntity> findByCardIdOrderByCreatedAtAscIdAsc(Long cardId);

  /**
   * Je Karte des Projekts der jüngste Kommentar mit dem Anker {@code ## Laufstand} am Textanfang
   * (Issue #1373). Natives SQL, weil {@code DISTINCT ON} die Auswahl „einer je Karte" in einem
   * Zugriff trägt; das Projekt kommt über die Karte.
   */
  @Query(
      value =
          "SELECT DISTINCT ON (c.card_id) c.* FROM comment c JOIN card k ON k.id = c.card_id"
              + " WHERE k.project_id = :projectId AND c.body LIKE '## Laufstand%'"
              + " ORDER BY c.card_id, c.created_at DESC, c.id DESC",
      nativeQuery = true)
  List<CommentEntity> findLaufstaendeImProjekt(@Param("projectId") long projectId);
}
