package org.mwolff.manban.card.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.domain.CardStatus;

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

  // Herkunft (Issue #517) und Laufkennung (Issue #1426); nullable, V23/V47 ohne Backfill.
  @Embedded private @Nullable HerkunftEmbeddable herkunft;

  // Status nach der Bewegung (Issue #1426); nullable, V47 ohne Backfill.
  @Column(name = "status_after")
  private @Nullable String statusAfter;

  protected CardActivityEntity() {
    // für JPA
  }

  CardActivityEntity(
      long cardId,
      long actorUserId,
      String type,
      String detail,
      Instant createdAt,
      ActorStamp stamp,
      @Nullable CardStatus statusAfter) {
    this.cardId = cardId;
    this.actorUserId = actorUserId;
    this.type = type;
    this.detail = detail;
    this.createdAt = createdAt;
    this.herkunft = new HerkunftEmbeddable(stamp);
    this.statusAfter = statusAfter == null ? null : statusAfter.name();
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

  HerkunftEmbeddable getHerkunft() {
    return herkunft == null ? new HerkunftEmbeddable() : herkunft;
  }

  @Nullable String getStatusAfter() {
    return statusAfter;
  }
}
