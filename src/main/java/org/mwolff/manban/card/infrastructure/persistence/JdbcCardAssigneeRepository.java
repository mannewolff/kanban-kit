package org.mwolff.manban.card.infrastructure.persistence;

import java.util.List;
import org.mwolff.manban.card.application.CardAssigneeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Verwaltet die Tabelle {@code card_assignee} (zusammengesetzter Schlüssel ohne ID) per SQL. */
@Component
class JdbcCardAssigneeRepository implements CardAssigneeRepository {

  private final JdbcTemplate jdbc;

  JdbcCardAssigneeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void replaceAssignees(long cardId, List<Long> userIds) {
    jdbc.update("DELETE FROM card_assignee WHERE card_id = ?", cardId);
    for (Long userId : userIds) {
      jdbc.update("INSERT INTO card_assignee (card_id, user_id) VALUES (?, ?)", cardId, userId);
    }
  }

  @Override
  public List<Long> findByCardId(long cardId) {
    return jdbc.queryForList(
        "SELECT user_id FROM card_assignee WHERE card_id = ? ORDER BY user_id", Long.class, cardId);
  }

  @Override
  public void deleteByCardId(long cardId) {
    jdbc.update("DELETE FROM card_assignee WHERE card_id = ?", cardId);
  }
}
