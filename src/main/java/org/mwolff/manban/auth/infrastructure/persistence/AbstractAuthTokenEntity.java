package org.mwolff.manban.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Gemeinsame JPA-Basis der Auth-Token-Entities (Passwort-Reset, E-Mail-Verifikation). Beide bilden
 * dieselbe Spaltenstruktur ab (user_id, token_hash, expires_at, used_at) und unterscheiden sich nur
 * in der Zieltabelle — die Felder, Konstruktoren und Getter liegen deshalb hier.
 */
// PMD.AbstractClassWithoutAbstractMethod: bewusst abstrakt — eine gemeinsame JPA-@MappedSuperclass,
// die selbst keine Tabelle abbildet und nicht instanziierbar sein soll, nicht Polymorphie-Basis.
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
@MappedSuperclass
abstract class AbstractAuthTokenEntity {

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

  protected AbstractAuthTokenEntity() {
    // für JPA
  }

  protected AbstractAuthTokenEntity(
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
