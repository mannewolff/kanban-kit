package org.mwolff.manban.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** JPA-Abbildung der Tabelle {@code password_reset_token}. */
@Entity
@Table(name = "password_reset_token")
class PasswordResetTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private @Nullable Instant usedAt;

  protected PasswordResetTokenEntity() {
    // für JPA
  }

  PasswordResetTokenEntity(
      @Nullable Long id,
      Long userId,
      String tokenHash,
      Instant expiresAt,
      @Nullable Instant usedAt) {
    this.id = id;
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.usedAt = usedAt;
  }

  @Nullable Long getId() {
    return id;
  }

  Long getUserId() {
    return userId;
  }

  String getTokenHash() {
    return tokenHash;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }

  @Nullable Instant getUsedAt() {
    return usedAt;
  }
}
