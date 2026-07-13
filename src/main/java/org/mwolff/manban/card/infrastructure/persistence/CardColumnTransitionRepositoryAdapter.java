package org.mwolff.manban.card.infrastructure.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.mwolff.manban.card.application.CardColumnTransitionRepository;
import org.mwolff.manban.card.domain.CardColumnTransition;
import org.springframework.stereotype.Component;

/** Adapter des {@link CardColumnTransitionRepository}-Ports auf Spring Data JPA. */
@Component
class CardColumnTransitionRepositoryAdapter implements CardColumnTransitionRepository {

  private final CardColumnTransitionJpaRepository jpa;

  CardColumnTransitionRepositoryAdapter(CardColumnTransitionJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public void open(long cardId, long columnId, String columnName, Instant enteredAt) {
    jpa.save(new CardColumnTransitionEntity(cardId, columnId, columnName, enteredAt));
  }

  @Override
  public void closeOpen(long cardId, Instant leftAt) {
    List<CardColumnTransitionEntity> open = jpa.findByCardIdAndLeftAtIsNull(cardId);
    for (CardColumnTransitionEntity entity : open) {
      long seconds = Duration.between(entity.getEnteredAt(), leftAt).toSeconds();
      entity.close(leftAt, seconds);
    }
    jpa.saveAll(open);
  }

  @Override
  public List<CardColumnTransition> findByCardId(long cardId) {
    return jpa.findByCardIdOrderByEnteredAt(cardId).stream()
        .map(CardColumnTransitionRepositoryAdapter::toDomain)
        .toList();
  }

  private static CardColumnTransition toDomain(CardColumnTransitionEntity e) {
    return new CardColumnTransition(
        e.getId(),
        e.getCardId(),
        e.getColumnId(),
        e.getColumnName(),
        e.getEnteredAt(),
        e.getLeftAt(),
        e.getDurationSeconds());
  }
}
