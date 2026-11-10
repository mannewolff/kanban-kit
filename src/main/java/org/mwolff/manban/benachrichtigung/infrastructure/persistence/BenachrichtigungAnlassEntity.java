package org.mwolff.manban.benachrichtigung.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;

/**
 * JPA-Abbildung der Tabelle {@code benachrichtigung_anlass}. Angelegt wird die Zeile über {@link
 * BenachrichtigungAnlassJpaRepository#legeZumLaufAn} oder {@link
 * BenachrichtigungAnlassJpaRepository#legeZumLabelAn}.
 */
@Entity
@Table(name = "benachrichtigung_anlass")
class BenachrichtigungAnlassEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "art", nullable = false)
  private AnlassArt art;

  @Column(name = "project_id", nullable = false)
  private Long projectId;

  @Column(name = "lauf_id")
  private Long laufId;

  @Column(name = "label_event_id")
  private Long labelEventId;

  @Column(name = "card_id")
  private Long cardId;

  @Column(name = "eingetreten_at", nullable = false)
  private Instant eingetretenAt;

  @Column(name = "angelegt_at", nullable = false)
  private Instant angelegtAt;

  protected BenachrichtigungAnlassEntity() {
    // für JPA
  }
}
