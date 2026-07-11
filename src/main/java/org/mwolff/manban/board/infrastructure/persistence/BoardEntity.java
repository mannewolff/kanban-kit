package org.mwolff.manban.board.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA-Abbildung der Tabelle {@code board}. */
@Entity
@Table(name = "board")
class BoardEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "project_id", nullable = false)
  private Long projectId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected BoardEntity() {
    // für JPA
  }

  BoardEntity(Long id, Long projectId, String name, Instant createdAt) {
    this.id = id;
    this.projectId = projectId;
    this.name = name;
    this.createdAt = createdAt;
  }

  Long getId() {
    return id;
  }

  Long getProjectId() {
    return projectId;
  }

  String getName() {
    return name;
  }

  Instant getCreatedAt() {
    return createdAt;
  }
}
