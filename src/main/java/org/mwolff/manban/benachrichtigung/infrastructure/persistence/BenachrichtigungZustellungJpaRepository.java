package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository der Zustellungen. */
interface BenachrichtigungZustellungJpaRepository
    extends JpaRepository<BenachrichtigungZustellungEntity, Long> {

  /** {@code ON CONFLICT DO NOTHING}: Je Anlass und Person bleibt es bei einer Zeile. */
  @Modifying
  @Query(
      value =
          "insert into benachrichtigung_zustellung"
              + " (anlass_id, user_id, faellig_ab, verschoben, zustand)"
              + " values (:anlassId, :userId, :faelligAb, :verschoben, 'OFFEN')"
              + " on conflict (anlass_id, user_id) do nothing",
      nativeQuery = true)
  void legeAn(
      @Param("anlassId") long anlassId,
      @Param("userId") long userId,
      @Param("faelligAb") Instant faelligAb,
      @Param("verschoben") boolean verschoben);
}
