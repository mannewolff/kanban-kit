package org.mwolff.manban.card.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.CardActivityRepository;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
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
      ActorStamp stamp) {
    CardActivityOrigin origin = stamp.origin();
    jpa.save(
        new CardActivityEntity(
            cardId,
            actorUserId,
            type.name(),
            detail,
            createdAt,
            origin == null ? null : origin.name(),
            stamp.tokenName(),
            stamp.agent()));
  }

  @Override
  public List<CardActivity> findByCardId(long cardId) {
    return jpa.findByCardIdOrderByCreatedAt(cardId).stream()
        .map(CardActivityRepositoryAdapter::toDomain)
        .toList();
  }

  private static CardActivity toDomain(CardActivityEntity e) {
    String origin = e.getOrigin();
    return new CardActivity(
        e.getId(),
        e.getCardId(),
        e.getActorUserId(),
        CardActivityType.valueOf(e.getType()),
        e.getDetail(),
        e.getCreatedAt(),
        origin == null ? null : CardActivityOrigin.valueOf(origin),
        e.getTokenName(),
        e.getAgent());
  }
}
