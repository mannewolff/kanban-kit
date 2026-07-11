package org.mwolff.manban.auth.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link PasswordResetTokenEntity}. */
interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, Long> {

  Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);
}
