package org.mwolff.manban.auth.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** JPA-Abbildung der Tabelle {@code password_reset_token}. */
@Entity
@Table(name = "password_reset_token")
class PasswordResetTokenEntity extends AbstractAuthTokenEntity {

  protected PasswordResetTokenEntity() {
    // für JPA
  }

  PasswordResetTokenEntity(
      @Nullable Long id,
      Long userId,
      String tokenHash,
      Instant expiresAt,
      @Nullable Instant usedAt) {
    super(id, userId, tokenHash, expiresAt, usedAt);
  }
}
