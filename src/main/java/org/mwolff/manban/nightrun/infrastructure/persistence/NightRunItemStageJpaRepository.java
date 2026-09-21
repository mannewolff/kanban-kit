package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring-Data-Repository für {@link NightRunItemStageEntity} (Issue #1112): der Lesepfad.
 * Geschrieben wird über JDBC im {@link NightRunRepositoryAdapter}, gelöscht über den Fremdschlüssel
 * {@code ON DELETE CASCADE} am Arbeitspaket.
 */
interface NightRunItemStageJpaRepository extends JpaRepository<NightRunItemStageEntity, Long> {

  /**
   * Die Stufen der genannten Arbeitspakete in einer Abfrage. Der Index {@code
   * idx_night_run_item_stage_item} aus {@code V37} trägt sie.
   *
   * <p>Sortiert nach Paket und Einfügereihenfolge — wie {@code
   * findByNightRunIdInOrderByNightRunIdAscIdAsc} bei den Paketen selbst: Die Reihenfolge der Stufen
   * ist die, in der sie gemeldet wurden.
   */
  List<NightRunItemStageEntity> findByNightRunItemIdInOrderByNightRunItemIdAscIdAsc(
      Collection<Long> nightRunItemIds);
}
