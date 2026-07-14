package org.mwolff.manban.card.infrastructure.persistence;

import java.util.List;
import org.mwolff.manban.card.application.CardLabelRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
}
