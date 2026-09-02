package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link NightRunItemEntity} (Lesepfad). */
interface NightRunItemJpaRepository extends JpaRepository<NightRunItemEntity, Long> {

  /** Arbeitspakete der genannten Läufe, nach Lauf und Einfügereihenfolge sortiert. */
  List<NightRunItemEntity> findByNightRunIdInOrderByNightRunIdAscIdAsc(
      Collection<Long> nightRunIds);
}
