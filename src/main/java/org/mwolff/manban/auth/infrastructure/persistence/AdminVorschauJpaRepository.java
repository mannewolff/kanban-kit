package org.mwolff.manban.auth.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.auth.domain.VorschauEndgrund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring-Data-Repository für {@link AdminVorschauEntity}. Das Beenden ist je <strong>eine</strong>
 * Anweisung mit der Bedingung „noch offen“ im {@code WHERE} (Issue #1917, Muster aus Issue #497).
 */
interface AdminVorschauJpaRepository extends JpaRepository<AdminVorschauEntity, Long> {

  Optional<AdminVorschauEntity> findByKennung(String kennung);

  List<AdminVorschauEntity> findByZielIdOrderByGestartetAmDescIdDesc(long zielId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update AdminVorschauEntity v set v.beendetAm = :beendetAm, v.endgrund = :endgrund"
          + " where v.id = :id and v.beendetAm is null")
  int beende(
      @Param("id") long id,
      @Param("beendetAm") Instant beendetAm,
      @Param("endgrund") VorschauEndgrund endgrund);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update AdminVorschauEntity v set v.beendetAm = v.laeuftAbAm, v.endgrund = :endgrund"
          + " where v.zielId = :zielId and v.beendetAm is null and v.laeuftAbAm <= :now")
  int beendeAbgelaufene(
      @Param("zielId") long zielId,
      @Param("now") Instant now,
      @Param("endgrund") VorschauEndgrund endgrund);
}
