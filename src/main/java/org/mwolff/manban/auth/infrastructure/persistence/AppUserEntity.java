package org.mwolff.manban.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.domain.AppUser;
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
  private @Nullable Long id;

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

  @Column(name = "approved_at")
  private @Nullable Instant approvedAt;

  @Column(name = "approved_by")
  private @Nullable Long approvedBy;

  protected AppUserEntity() {
    // für JPA
  }

  /** Baut die Entity direkt aus dem Domänenobjekt (statt aus 8 Einzelparametern, Sonar S107). */
  AppUserEntity(AppUser u) {
    this.id = u.id();
    this.email = u.email();
    this.passwordHash = u.passwordHash();
    this.displayName = u.displayName();
    this.emailVerified = u.emailVerified();
    this.platformRole = u.platformRole();
    this.approvedAt = u.approvedAt();
    this.approvedBy = u.approvedBy();
  }

  @Nullable Long getId() {
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

  @Nullable Instant getApprovedAt() {
    return approvedAt;
  }

  @Nullable Long getApprovedBy() {
    return approvedBy;
  }
}
