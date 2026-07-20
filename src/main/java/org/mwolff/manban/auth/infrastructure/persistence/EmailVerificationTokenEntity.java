package org.mwolff.manban.auth.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** JPA-Abbildung der Tabelle {@code email_verification_token}. */
@Entity
@Table(name = "email_verification_token")
class EmailVerificationTokenEntity extends AbstractAuthTokenEntity {

  protected EmailVerificationTokenEntity() {
    // für JPA
  }

  EmailVerificationTokenEntity(
      @Nullable Long id,
      Long userId,
      String tokenHash,
      Instant expiresAt,
      @Nullable Instant usedAt) {
    super(id, userId, tokenHash, expiresAt, usedAt);
  }
}
