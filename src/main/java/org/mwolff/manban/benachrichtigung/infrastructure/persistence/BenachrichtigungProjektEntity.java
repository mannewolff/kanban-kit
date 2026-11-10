package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA-Abbildung der Tabelle {@code benachrichtigung_projekt}. Angelegt wird die Zeile über {@link
 * BenachrichtigungProjektJpaRepository#schalteEin}, gelesen über diese Entität.
 */
@Entity
@Table(name = "benachrichtigung_projekt")
class BenachrichtigungProjektEntity {

  @Id
  @Column(name = "project_id")
  private Long projectId;

  @Column(name = "eingeschaltet_seit", nullable = false)
  private Instant eingeschaltetSeit;

  protected BenachrichtigungProjektEntity() {
    // für JPA
  }

  Long getProjectId() {
    return projectId;
  }

  Instant getEingeschaltetSeit() {
    return eingeschaltetSeit;
  }
}
