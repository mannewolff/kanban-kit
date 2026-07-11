package org.mwolff.manban.project.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.mwolff.manban.project.domain.ProjectRole;

/** JPA-Abbildung der Tabelle {@code project_invitation}. */
@Entity
@Table(name = "project_invitation")
class ProjectInvitationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "project_id", nullable = false)
  private Long projectId;

  @Column(name = "email", nullable = false)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  private ProjectRole role;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "invited_by")
  private Long invitedBy;

  protected ProjectInvitationEntity() {
    // für JPA
  }

  ProjectInvitationEntity(
      Long id,
      Long projectId,
      String email,
      ProjectRole role,
      String tokenHash,
      Instant expiresAt,
      Instant acceptedAt,
      Long invitedBy) {
    this.id = id;
    this.projectId = projectId;
    this.email = email;
    this.role = role;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.acceptedAt = acceptedAt;
    this.invitedBy = invitedBy;
  }

  Long getId() {
    return id;
  }

  Long getProjectId() {
    return projectId;
  }

  String getEmail() {
    return email;
  }

  ProjectRole getRole() {
    return role;
  }

  String getTokenHash() {
    return tokenHash;
  }

  Instant getExpiresAt() {
    return expiresAt;
  }

  Instant getAcceptedAt() {
    return acceptedAt;
  }

  Long getInvitedBy() {
    return invitedBy;
  }
}
