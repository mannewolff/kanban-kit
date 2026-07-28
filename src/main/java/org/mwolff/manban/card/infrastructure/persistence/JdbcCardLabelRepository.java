package org.mwolff.manban.card.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.manban.card.application.CardLabelRepository;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Verwaltet die Tabelle {@code card_label} (zusammengesetzter Schlüssel ohne ID) per SQL. */
@Component
class JdbcCardLabelRepository implements CardLabelRepository {

  private final NamedParameterJdbcTemplate jdbc;

  JdbcCardLabelRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void replaceLabels(long cardId, List<Long> labelIds) {
    jdbc.update("DELETE FROM card_label WHERE card_id = :cardId", Map.of("cardId", cardId));
    for (Long labelId : labelIds) {
      jdbc.update(
          "INSERT INTO card_label (card_id, label_id) VALUES (:cardId, :labelId)",
          Map.of("cardId", cardId, "labelId", labelId));
    }
  }

  @Override
  public List<Long> findByCardId(long cardId) {
    return jdbc.queryForList(
        "SELECT label_id FROM card_label WHERE card_id = :cardId ORDER BY label_id",
        Map.of("cardId", cardId),
        Long.class);
  }

  @Override
  public Map<Long, List<Long>> findByCardIds(Collection<Long> cardIds) {
    if (cardIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, List<Long>> byCard = new LinkedHashMap<>();
    jdbc.query(
        "SELECT card_id, label_id FROM card_label WHERE card_id IN (:cardIds)"
            + " ORDER BY card_id, label_id",
        Map.of("cardIds", cardIds),
        (RowCallbackHandler)
            rs ->
                byCard
                    .computeIfAbsent(rs.getLong("card_id"), k -> new ArrayList<>())
                    .add(rs.getLong("label_id")));
    return byCard;
  }
}
