package org.mwolff.manban.push.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link PushAboEntity}. */
interface PushAboJpaRepository extends JpaRepository<PushAboEntity, Long> {

  Optional<PushAboEntity> findByEndpoint(String endpoint);
}
