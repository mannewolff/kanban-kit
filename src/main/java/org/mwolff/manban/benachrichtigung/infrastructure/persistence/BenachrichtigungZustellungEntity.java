package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA-Abbildung der Tabelle {@code benachrichtigung_zustellung}. Angelegt wird die Zeile über
 * {@link BenachrichtigungZustellungJpaRepository#legeAn}.
 */
@Entity
@Table(name = "benachrichtigung_zustellung")
class BenachrichtigungZustellungEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "anlass_id", nullable = false)
  private Long anlassId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "faellig_ab", nullable = false)
  private Instant faelligAb;

  @Column(name = "verschoben", nullable = false)
  private boolean verschoben;

  @Column(name = "zustand", nullable = false)
  private String zustand;

  protected BenachrichtigungZustellungEntity() {
    // für JPA
  }
}
