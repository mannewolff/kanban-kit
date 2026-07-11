package org.mwolff.manban.auth.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link EmailVerificationTokenEntity}. */
interface EmailVerificationTokenJpaRepository
    extends JpaRepository<EmailVerificationTokenEntity, Long> {

  Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);
}
