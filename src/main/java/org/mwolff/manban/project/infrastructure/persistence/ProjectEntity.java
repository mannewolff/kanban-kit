package org.mwolff.manban.project.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** JPA-Abbildung der Tabelle {@code project}. */
@Entity
@Table(name = "project")
class ProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectEntity() {
        // für JPA
    }

    ProjectEntity(Long id, String name, Long ownerUserId, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.ownerUserId = ownerUserId;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    Long getOwnerUserId() {
        return ownerUserId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
