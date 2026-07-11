package org.mwolff.manban.auth.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link AppUserEntity}. */
interface AppUserJpaRepository extends JpaRepository<AppUserEntity, Long> {

  Optional<AppUserEntity> findByEmail(String email);

  boolean existsByEmail(String email);
}
