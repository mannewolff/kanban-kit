package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring-Data-Repository für {@link NightRunItemEntity}: der Lesepfad und das Löschen verwaister
 * Pakete. Geschrieben wird über JDBC im {@link NightRunRepositoryAdapter}.
 */
interface NightRunItemJpaRepository extends JpaRepository<NightRunItemEntity, Long> {

  /** Arbeitspakete der genannten Läufe, nach Lauf und Einfügereihenfolge sortiert. */
  List<NightRunItemEntity> findByNightRunIdInOrderByNightRunIdAscIdAsc(
      Collection<Long> nightRunIds);

  /**
   * Die Anläufe einer Karte über Läufe hinweg (Issue #967); der Index {@code
   * idx_night_run_item_card} aus {@code V33} trägt die Abfrage.
   */
  List<NightRunItemEntity> findByProjectIdAndCardNumberOrderByStartedAtDescIdDesc(
      long projectId, int cardNumber);

  /**
   * Löscht die verwaisten Pakete eines Laufs (Issue #965). Der Teilindex {@code
   * idx_night_run_item_orphan} aus {@code V33} trägt die Bedingung.
   *
   * <p>{@code @Transactional} wie bei den Löschmethoden von Spring Data selbst: Im Service tritt
   * der Aufruf dessen Transaktion bei, allein aufgerufen öffnet er eine eigene.
   */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "delete from #{#entityName} i where i.projectId = :projectId"
          + " and i.startedAt = :startedAt and i.nightRunId is null")
  int deleteOrphansOfRun(@Param("projectId") long projectId, @Param("startedAt") Instant startedAt);

  /**
   * Kappt die verwaisten Pakete des Projekts auf die {@code keep} jüngsten (Issue #966). Nativ,
   * weil JPQL kein {@code LIMIT} kennt; die Auswahl steht als Unterabfrage wie bei der Verdrängung
   * der Läufe. Bei gleichem Startzeitpunkt entscheidet die ID. Der Teilindex {@code
   * idx_night_run_item_orphan} trägt beide Abfragen.
   */
  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "delete from night_run_item where project_id = :projectId and night_run_id is null"
              + " and id not in (select id from night_run_item where project_id = :projectId"
              + " and night_run_id is null order by started_at desc, id desc limit :keep)",
      nativeQuery = true)
  int deleteOrphansOlderThanNewest(@Param("projectId") long projectId, @Param("keep") int keep);
}
