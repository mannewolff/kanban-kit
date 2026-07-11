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
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.project.domain.ProjectRole;

/** JPA-Abbildung der Tabelle {@code project_membership}. */
@Entity
@Table(name = "project_membership")
class ProjectMembershipEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "project_id", nullable = false)
  private Long projectId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false)
  private ProjectRole role;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ProjectMembershipEntity() {
    // für JPA
  }

  ProjectMembershipEntity(
      @Nullable Long id, Long projectId, Long userId, ProjectRole role, Instant createdAt) {
    this.id = id;
    this.projectId = projectId;
    this.userId = userId;
    this.role = role;
    this.createdAt = createdAt;
  }

  @Nullable Long getId() {
    return id;
  }

  Long getProjectId() {
    return projectId;
  }

  Long getUserId() {
    return userId;
  }

  ProjectRole getRole() {
    return role;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
