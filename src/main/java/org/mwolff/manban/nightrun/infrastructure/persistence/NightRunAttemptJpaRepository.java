package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /**
   * Die Vorgänge der Pakete des Projekts mit Start ab {@code von}, je mit Start und Gattung ihres
   * Pakets (Issue #1947) — auch verwaiste Pakete, weil die Abfrage am Paket und nicht am Lauf
   * hängt. Sortiert nach Start, Paket und Stelle im Paket.
   */
  @Query(
      "select new org.mwolff.manban.nightrun.infrastructure.persistence"
          + ".NightRunAttemptJpaRepository$MitPaket(a, i.startedAt, i.kind)"
          + " from NightRunAttemptEntity a, NightRunItemEntity i"
          + " where i.id = a.nightRunItemId and i.projectId = :projectId and i.startedAt >= :von"
          + " order by i.startedAt, i.id, a.position")
  List<MitPaket> findSince(@Param("projectId") long projectId, @Param("von") Instant von);

  /** Ein Vorgang mit Start und Gattung seines Pakets. */
  record MitPaket(NightRunAttemptEntity attempt, Instant startedAt, String kind) {}
}
