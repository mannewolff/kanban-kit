package org.mwolff.manban.nightrun.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link VorhabenKostenEinfuehrungEntity} (Issue #2004). */
interface VorhabenKostenEinfuehrungJpaRepository
    extends JpaRepository<VorhabenKostenEinfuehrungEntity, Long> {}
