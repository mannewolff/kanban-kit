package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository der Projektschalter. */
interface BenachrichtigungProjektJpaRepository
    extends JpaRepository<BenachrichtigungProjektEntity, Long> {

  /**
   * {@code ON CONFLICT DO NOTHING}: Auch gleichzeitiges Einschalten behält den ersten Zeitpunkt.
   */
  @Modifying
  @Query(
      value =
          "insert into benachrichtigung_projekt (project_id, eingeschaltet_seit)"
              + " values (:projectId, :seit)"
              + " on conflict (project_id) do nothing",
      nativeQuery = true)
  void schalteEin(@Param("projectId") long projectId, @Param("seit") Instant seit);

  @Modifying
  @Query("delete from BenachrichtigungProjektEntity e where e.projectId = :projectId")
  void schalteAus(@Param("projectId") long projectId);
}
