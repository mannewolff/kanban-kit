package org.mwolff.manban.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.domain.LoginCode;

/** JPA-Abbildung der Tabelle {@code login_code} (Plan #1801, A1). */
@Entity
@Table(name = "login_code")
class LoginCodeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "email_hash", nullable = false)
  private String emailHash;

  @Column(name = "user_id")
  private @Nullable Long userId;

  @Column(name = "code_hash")
  private @Nullable String codeHash;

  @Column(name = "link_hash")
  private @Nullable String linkHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "failed_attempts", nullable = false)
  private int failedAttempts;

  @Column(name = "consumed_at")
  private @Nullable Instant consumedAt;

  @Column(name = "superseded_at")
  private @Nullable Instant supersededAt;

  protected LoginCodeEntity() {
    // für JPA
  }

  LoginCodeEntity(LoginCode code) {
    this.id = code.id();
    this.emailHash = code.emailHash();
    this.userId = code.userId();
    this.codeHash = code.codeHash();
    this.linkHash = code.linkHash();
    this.createdAt = code.createdAt();
    this.expiresAt = code.expiresAt();
    this.failedAttempts = code.failedAttempts();
    this.consumedAt = code.consumedAt();
    this.supersededAt = code.supersededAt();
  }

  LoginCode toDomain() {
    return new LoginCode(
        id,
        emailHash,
        userId,
        codeHash,
        linkHash,
        createdAt,
        expiresAt,
        failedAttempts,
        consumedAt,
        supersededAt);
  }
}
