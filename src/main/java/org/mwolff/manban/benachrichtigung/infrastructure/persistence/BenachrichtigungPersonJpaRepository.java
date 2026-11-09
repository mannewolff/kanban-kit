package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import java.time.Instant;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository der Personenschalter. */
interface BenachrichtigungPersonJpaRepository
    extends JpaRepository<BenachrichtigungPersonEntity, BenachrichtigungPersonEntity.Schluessel> {

  /**
   * {@code ON CONFLICT DO NOTHING}: Auch gleichzeitiges Einschalten behält den ersten Zeitpunkt.
   */
  @Modifying
  @Query(
      value =
          "insert into benachrichtigung_person (user_id, project_id, eingeschaltet_seit)"
              + " values (:userId, :projectId, :seit)"
              + " on conflict (user_id, project_id) do nothing",
      nativeQuery = true)
  void schalteEin(
      @Param("userId") long userId,
      @Param("projectId") long projectId,
      @Param("seit") Instant seit);

  @Modifying
  @Query(
      "delete from BenachrichtigungPersonEntity e"
          + " where e.userId = :userId and e.projectId = :projectId")
  void schalteAus(@Param("userId") long userId, @Param("projectId") long projectId);

  @Query("select e.projectId from BenachrichtigungPersonEntity e where e.userId = :userId")
  Set<Long> findProjectIdsByUserId(@Param("userId") long userId);
}
