package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/**
 * JPA-Abbildung der Tabelle {@code benachrichtigung_person}. Angelegt wird die Zeile über {@link
 * BenachrichtigungPersonJpaRepository#schalteEin}, gelesen über diese Entität.
 */
@Entity
@Table(name = "benachrichtigung_person")
@IdClass(BenachrichtigungPersonEntity.Schluessel.class)
class BenachrichtigungPersonEntity {

  /** Zusammengesetzter Primärschlüssel; die Namen entsprechen den Feldern der Entität. */
  record Schluessel(Long userId, Long projectId) implements Serializable {}

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Id
  @Column(name = "project_id")
  private Long projectId;

  @Column(name = "eingeschaltet_seit", nullable = false)
  private Instant eingeschaltetSeit;

  protected BenachrichtigungPersonEntity() {
    // für JPA
  }

  Instant getEingeschaltetSeit() {
    return eingeschaltetSeit;
  }
}
