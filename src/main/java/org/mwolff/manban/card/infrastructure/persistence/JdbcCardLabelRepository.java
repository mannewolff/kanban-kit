package org.mwolff.manban.card.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.mwolff.manban.card.application.CardLabelRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/** Verwaltet die Tabelle {@code card_label} (zusammengesetzter Schlüssel ohne ID) per SQL. */
@Component
class JdbcCardLabelRepository implements CardLabelRepository {

  private final JdbcTemplate jdbc;

  JdbcCardLabelRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void replaceLabels(long cardId, List<Long> labelIds) {
    jdbc.update("DELETE FROM card_label WHERE card_id = ?", cardId);
    for (Long labelId : labelIds) {
      jdbc.update("INSERT INTO card_label (card_id, label_id) VALUES (?, ?)", cardId, labelId);
    }
  }

  @Override
  public List<Long> findByCardId(long cardId) {
    return jdbc.queryForList(
        "SELECT label_id FROM card_label WHERE card_id = ? ORDER BY label_id", Long.class, cardId);
  }

  @Override
  public Map<Long, List<Long>> findByCardIds(Collection<Long> cardIds) {
    if (cardIds.isEmpty()) {
      return Map.of();
    }
    String placeholders = cardIds.stream().map(id -> "?").collect(Collectors.joining(", "));
    Map<Long, List<Long>> byCard = new LinkedHashMap<>();
    jdbc.query(
        "SELECT card_id, label_id FROM card_label WHERE card_id IN ("
            + placeholders
            + ") ORDER BY card_id, label_id",
        (RowCallbackHandler)
            rs ->
                byCard
                    .computeIfAbsent(rs.getLong("card_id"), k -> new ArrayList<>())
                    .add(rs.getLong("label_id")),
        cardIds.toArray());
    return byCard;
  }
}
