package org.mwolff.manban.card.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;

/** JPA-Abbildung der Tabelle {@code card_label_event} (V55, Issue #1754). */
@Entity
@Table(name = "card_label_event")
class CardLabelEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "card_id", nullable = false)
  private Long cardId;

  @Column(name = "label_name", nullable = false)
  private String labelName;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  // Herkunft wie card_activity: origin/tokenName verifiziert, agent und runStartedAt
  // Selbstauskunft; alle nullable — ein Aufruf ohne authentifizierten Kontext trägt keine.
  @Column(name = "origin")
  private @Nullable String origin;

  @Column(name = "token_name")
  private @Nullable String tokenName;

  @Column(name = "agent")
  private @Nullable String agent;

  @Column(name = "run_started_at")
  private @Nullable Instant runStartedAt;

  protected CardLabelEventEntity() {
    // für JPA
  }

  CardLabelEventEntity(long cardId, String labelName, Instant createdAt, ActorStamp stamp) {
    this.cardId = cardId;
    this.labelName = labelName;
    this.createdAt = createdAt;
    this.origin = stamp.originName();
    this.tokenName = stamp.tokenName();
    this.agent = stamp.agent();
    this.runStartedAt = stamp.laufStart();
  }

  Long getCardId() {
    return cardId;
  }

  String getLabelName() {
    return labelName;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  @Nullable String getOrigin() {
    return origin;
  }

  @Nullable String getTokenName() {
    return tokenName;
  }

  @Nullable String getAgent() {
    return agent;
  }

  @Nullable Instant getRunStartedAt() {
    return runStartedAt;
  }
}
