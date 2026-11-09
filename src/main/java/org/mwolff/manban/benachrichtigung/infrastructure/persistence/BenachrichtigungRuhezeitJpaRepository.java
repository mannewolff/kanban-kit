package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import java.time.LocalTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository der Ruhezeiten. */
interface BenachrichtigungRuhezeitJpaRepository
    extends JpaRepository<BenachrichtigungRuhezeitEntity, Long> {

  /** {@code clearAutomatically}: Ein Lesen danach sieht die neue Zeile, nicht die geladene. */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          "insert into benachrichtigung_ruhezeit (user_id, beginn, ende, zeitzone)"
              + " values (:userId, :beginn, :ende, :zeitzone)"
              + " on conflict (user_id) do update set beginn = excluded.beginn,"
              + " ende = excluded.ende, zeitzone = excluded.zeitzone",
      nativeQuery = true)
  void speichere(
      @Param("userId") long userId,
      @Param("beginn") LocalTime beginn,
      @Param("ende") LocalTime ende,
      @Param("zeitzone") String zeitzone);

  @Modifying
  @Query("delete from BenachrichtigungRuhezeitEntity e where e.userId = :userId")
  void loesche(@Param("userId") long userId);
}
