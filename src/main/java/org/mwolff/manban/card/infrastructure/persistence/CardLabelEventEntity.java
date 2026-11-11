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

  // Herkunft wie card_activity; nullable — ein Aufruf ohne authentifizierten Kontext trägt keine.
  @Embedded private @Nullable HerkunftEmbeddable herkunft;

  // Wie card_activity: nullable, ON DELETE SET NULL (V72, Issue #2110).
  @Column(name = "actor_user_id")
  private @Nullable Long actorUserId;

  protected CardLabelEventEntity() {
    // für JPA
  }

  CardLabelEventEntity(
      long cardId, String labelName, Instant createdAt, ActorStamp stamp, long actorUserId) {
    this.cardId = cardId;
    this.labelName = labelName;
    this.createdAt = createdAt;
    this.herkunft = new HerkunftEmbeddable(stamp);
    this.actorUserId = actorUserId;
  }

  @Nullable Long getId() {
    return id;
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

  HerkunftEmbeddable getHerkunft() {
    return herkunft == null ? new HerkunftEmbeddable() : herkunft;
  }

  @Nullable Long getActorUserId() {
    return actorUserId;
  }
}
