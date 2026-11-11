package org.mwolff.manban.card.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.CardLabelEventRepository;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardLabelEvent;
import org.mwolff.manban.card.domain.LabelEreignisMitPerson;
import org.mwolff.manban.card.domain.LabelEreignisZeile;
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
  public void add(long cardId, String labelName, Instant zeitpunkt, ActorStamp stamp, long userId) {
    jpa.save(new CardLabelEventEntity(cardId, labelName, zeitpunkt, stamp, userId));
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

  @Override
  public List<LabelEreignisZeile> findNachLabelNamenSeit(
      Collection<String> labelNamen, Instant seit) {
    if (labelNamen.isEmpty()) {
      return List.of();
    }
    return jpa.findNachLabelNamenSeit(labelNamen, seit).stream()
        .map(CardLabelEventRepositoryAdapter::toZeile)
        .toList();
  }

  @Override
  public List<LabelEreignisMitPerson> findMitPerson(Collection<Long> cardIds) {
    if (cardIds.isEmpty()) {
      return List.of();
    }
    return jpa.findMitPerson(cardIds).stream()
        .map(CardLabelEventRepositoryAdapter::toMitPerson)
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
    HerkunftEmbeddable herkunft = e.getHerkunft();
    return new CardLabelEvent(
        e.getCardId(),
        e.getLabelName(),
        e.getCreatedAt(),
        origin(herkunft),
        herkunft.getTokenName(),
        herkunft.getAgent(),
        Objects.requireNonNull(herkunft.getRunStartedAt()));
  }

  private static LabelEreignisMitPerson toMitPerson(CardLabelEventEntity e) {
    HerkunftEmbeddable herkunft = e.getHerkunft();
    return new LabelEreignisMitPerson(
        Objects.requireNonNull(e.getId()),
        e.getCardId(),
        e.getLabelName(),
        e.getCreatedAt(),
        e.getActorUserId(),
        origin(herkunft),
        herkunft.getTokenName());
  }

  private static @Nullable CardActivityOrigin origin(HerkunftEmbeddable herkunft) {
    String origin = herkunft.getOrigin();
    return origin == null ? null : CardActivityOrigin.valueOf(origin);
  }

  private static LabelEreignisZeile toZeile(CardLabelEventEntity e) {
    return new LabelEreignisZeile(
        Objects.requireNonNull(e.getId()),
        e.getCardId(),
        e.getLabelName(),
        e.getCreatedAt(),
        e.getHerkunft().getRunStartedAt());
  }
}
