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
import org.mwolff.manban.card.domain.CardActivityOrigin;

/** JPA-Abbildung der Tabelle {@code card_activity}. */
@Entity
@Table(name = "card_activity")
class CardActivityEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "card_id", nullable = false)
  private Long cardId;

  @Column(name = "actor_user_id")
  private @Nullable Long actorUserId;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "detail", nullable = false)
  private String detail;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  // Herkunft (Issue #517): origin/tokenName verifiziert, agent Selbstauskunft; alle drei
  // nullable — Alt-Einträge tragen keine Herkunft (V23 ohne Backfill).
  @Column(name = "origin")
  private @Nullable String origin;

  @Column(name = "token_name")
  private @Nullable String tokenName;

  @Column(name = "agent")
  private @Nullable String agent;

  protected CardActivityEntity() {
    // für JPA
  }

  CardActivityEntity(
      long cardId,
      long actorUserId,
      String type,
      String detail,
      Instant createdAt,
      ActorStamp stamp) {
    this.cardId = cardId;
    this.actorUserId = actorUserId;
    this.type = type;
    this.detail = detail;
    this.createdAt = createdAt;
    CardActivityOrigin stampOrigin = stamp.origin();
    this.origin = stampOrigin == null ? null : stampOrigin.name();
    this.tokenName = stamp.tokenName();
    this.agent = stamp.agent();
  }

  @Nullable Long getId() {
    return id;
  }

  Long getCardId() {
    return cardId;
  }

  @Nullable Long getActorUserId() {
    return actorUserId;
  }

  String getType() {
    return type;
  }

  String getDetail() {
    return detail;
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
}
