package org.mwolff.manban.card.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.CardActivityRepository;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.springframework.stereotype.Component;

/** Adapter des {@link CardActivityRepository}-Ports auf Spring Data JPA. */
@Component
class CardActivityRepositoryAdapter implements CardActivityRepository {

  private final CardActivityJpaRepository jpa;

  CardActivityRepositoryAdapter(CardActivityJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public void add(
      long cardId,
      long actorUserId,
      CardActivityType type,
      String detail,
      Instant createdAt,
      ActorStamp stamp,
      @Nullable CardStatus statusAfter) {
    jpa.save(
        new CardActivityEntity(
            cardId, actorUserId, type.name(), detail, createdAt, stamp, statusAfter));
  }

  @Override
  public List<CardActivity> findByCardId(long cardId) {
    return jpa.findByCardIdOrderByCreatedAt(cardId).stream()
        .map(CardActivityRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<CardActivity> findTokenActivitiesInWindow(
      long projectId, String tokenName, Instant von, Instant bis) {
    return jpa.findTokenActivitiesInWindow(projectId, tokenName, von, bis).stream()
        .map(CardActivityRepositoryAdapter::toDomain)
        .toList();
  }

  private static CardActivity toDomain(CardActivityEntity e) {
    String origin = e.getOrigin();
    String statusAfter = e.getStatusAfter();
    return new CardActivity(
        e.getId(),
        e.getCardId(),
        e.getActorUserId(),
        CardActivityType.valueOf(e.getType()),
        e.getDetail(),
        e.getCreatedAt(),
        origin == null ? null : CardActivityOrigin.valueOf(origin),
        e.getTokenName(),
        e.getAgent(),
        e.getRunStartedAt(),
        statusAfter == null ? null : CardStatus.valueOf(statusAfter));
  }
}
