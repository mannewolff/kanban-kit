package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;

/**
 * JPA-Abbildung der Tabelle {@code benachrichtigung_ruhezeit}. Geschrieben wird die Zeile über
 * {@link BenachrichtigungRuhezeitJpaRepository#speichere}, gelesen über diese Entität.
 */
@Entity
@Table(name = "benachrichtigung_ruhezeit")
class BenachrichtigungRuhezeitEntity {

  @Id
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "beginn", nullable = false)
  private LocalTime beginn;

  @Column(name = "ende", nullable = false)
  private LocalTime ende;

  @Column(name = "zeitzone", nullable = false)
  private String zeitzone;

  protected BenachrichtigungRuhezeitEntity() {
    // für JPA
  }

  LocalTime getBeginn() {
    return beginn;
  }

  LocalTime getEnde() {
    return ende;
  }

  String getZeitzone() {
    return zeitzone;
  }
}
