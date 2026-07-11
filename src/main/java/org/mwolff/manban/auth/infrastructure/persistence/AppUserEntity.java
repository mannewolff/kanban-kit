package org.mwolff.manban.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.mwolff.manban.auth.domain.PlatformRole;

/**
 * JPA-Abbildung der Tabelle {@code app_user}. Zeitstempel (created_at/updated_at) verwaltet die
 * Datenbank (Default + Trigger) und sind hier bewusst nicht gemappt.
 */
@Entity
@Table(name = "app_user")
class AppUserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Enumerated(EnumType.STRING)
  @Column(name = "platform_role", nullable = false)
  private PlatformRole platformRole;

  protected AppUserEntity() {
    // für JPA
  }

  AppUserEntity(
      Long id,
      String email,
      String passwordHash,
      String displayName,
      boolean emailVerified,
      PlatformRole platformRole) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.emailVerified = emailVerified;
    this.platformRole = platformRole;
  }

  Long getId() {
    return id;
  }

  String getEmail() {
    return email;
  }

  String getPasswordHash() {
    return passwordHash;
  }

  String getDisplayName() {
    return displayName;
  }

  boolean isEmailVerified() {
    return emailVerified;
  }

  PlatformRole getPlatformRole() {
    return platformRole;
  }
}
