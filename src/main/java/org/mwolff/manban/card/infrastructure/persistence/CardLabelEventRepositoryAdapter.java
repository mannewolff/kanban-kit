package org.mwolff.manban.card.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.CardLabelEventRepository;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardLabelEvent;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter des {@link CardLabelEventRepository}-Ports: die Ereignisse über Spring Data JPA, der
 * Erfassungsbeginn per SQL — {@code card_label_event_capture} hat keinen Schlüssel und keine ID.
 */
@Component
class CardLabelEventRepositoryAdapter implements CardLabelEventRepository {

  private final CardLabelEventJpaRepository jpa;
  private final NamedParameterJdbcTemplate jdbc;

  CardLabelEventRepositoryAdapter(
      CardLabelEventJpaRepository jpa, NamedParameterJdbcTemplate jdbc) {
    this.jpa = jpa;
    this.jdbc = jdbc;
  }

  @Override
  public void add(long cardId, String labelName, Instant zeitpunkt, ActorStamp stamp) {
    jpa.save(new CardLabelEventEntity(cardId, labelName, zeitpunkt, stamp));
  }

  @Override
  public List<CardLabelEvent> findLaufEreignisse(Collection<Long> cardIds, Instant von) {
    if (cardIds.isEmpty()) {
      return List.of();
    }
    return jpa.findLaufEreignisse(cardIds, von).stream()
        .map(CardLabelEventRepositoryAdapter::toDomain)
        .toList();
  }

  /**
   * Die eine Zeile, die V55 anlegt. Fehlt sie, ist das Schema beschädigt — {@code queryForObject}
   * scheitert dann laut, statt einen Beginn zu erfinden.
   */
  @Override
  public Instant erfassungSeit() {
    OffsetDateTime seit =
        jdbc.queryForObject(
            "SELECT since FROM card_label_event_capture", Map.of(), OffsetDateTime.class);
    return Objects.requireNonNull(seit).toInstant();
  }

  private static CardLabelEvent toDomain(CardLabelEventEntity e) {
    String origin = e.getOrigin();
    return new CardLabelEvent(
        e.getCardId(),
        e.getLabelName(),
        e.getCreatedAt(),
        origin == null ? null : CardActivityOrigin.valueOf(origin),
        e.getTokenName(),
        e.getAgent(),
        Objects.requireNonNull(e.getRunStartedAt()));
  }
}
