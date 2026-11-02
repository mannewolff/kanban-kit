package org.mwolff.manban.nightrun.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA-Abbildung der Tabelle {@code vorhaben_kosten_einfuehrung} (Issue #2004, Plan #1990 E8): je
 * Projekt der erste Lauf des Jobs, der die Kosten je Vorhaben festhält.
 */
@Entity
@Table(name = "vorhaben_kosten_einfuehrung")
class VorhabenKostenEinfuehrungEntity {

  @Id
  @Column(name = "project_id", nullable = false)
  private Long projectId;

  @Column(name = "eingefuehrt_at", nullable = false)
  private Instant eingefuehrtAt;

  protected VorhabenKostenEinfuehrungEntity() {
    // für JPA
  }

  VorhabenKostenEinfuehrungEntity(long projectId, Instant eingefuehrtAt) {
    this.projectId = projectId;
    this.eingefuehrtAt = eingefuehrtAt;
  }

  Instant getEingefuehrtAt() {
    return eingefuehrtAt;
  }
}
