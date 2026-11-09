package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository der Anlässe. */
interface BenachrichtigungAnlassJpaRepository
    extends JpaRepository<BenachrichtigungAnlassEntity, Long> {

  /**
   * {@code ON CONFLICT DO NOTHING}: Bei parallelen Takten wartet der zweite auf den ersten und legt
   * dann nichts an, statt zu scheitern.
   *
   * @return 1, wenn die Zeile neu ist, sonst 0
   */
  @Modifying
  @Query(
      value =
          "insert into benachrichtigung_anlass"
              + " (art, project_id, lauf_id, card_id, eingetreten_at, angelegt_at)"
              + " values (:art, :projectId, :laufId, cast(:cardId as bigint), :eingetretenAt,"
              + " :angelegtAt)"
              + " on conflict (lauf_id, art) do nothing",
      nativeQuery = true)
  int legeZumLaufAn(
      @Param("art") String art,
      @Param("projectId") long projectId,
      @Param("laufId") long laufId,
      @Param("cardId") @Nullable Long cardId,
      @Param("eingetretenAt") Instant eingetretenAt,
      @Param("angelegtAt") Instant angelegtAt);

  @Query(
      "select e.id from BenachrichtigungAnlassEntity e where e.laufId = :laufId and e.art = :art")
  Long findIdByLaufIdAndArt(@Param("laufId") long laufId, @Param("art") AnlassArt art);
}
