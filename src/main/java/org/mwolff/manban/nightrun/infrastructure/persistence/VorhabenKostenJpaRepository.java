package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link VorhabenKostenEntity} (Issue #2004). */
interface VorhabenKostenJpaRepository extends JpaRepository<VorhabenKostenEntity, Long> {

  /** Die Zeilen aller Vorhaben des Projekts, aufsteigend nach Vorhaben-ID. */
  List<VorhabenKostenEntity> findByProjectIdOrderByEpicIdAsc(long projectId);
}
