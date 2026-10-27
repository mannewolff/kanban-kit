package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring-Data-Repository für {@link NightRunAttemptEntity} (Issue #1944): der Lesepfad. Geschrieben
 * wird über JDBC im {@link NightRunRepositoryAdapter}, gelöscht über den Fremdschlüssel {@code ON
 * DELETE CASCADE} am Arbeitspaket.
 */
interface NightRunAttemptJpaRepository extends JpaRepository<NightRunAttemptEntity, Long> {

  /**
   * Die Vorgänge der genannten Arbeitspakete in einer Abfrage, je Paket in der Reihenfolge der
   * Ausführung. Der Index {@code idx_night_run_attempt_item} aus {@code V63} trägt sie.
   */
  List<NightRunAttemptEntity> findByNightRunItemIdInOrderByNightRunItemIdAscPositionAsc(
      Collection<Long> nightRunItemIds);
}
