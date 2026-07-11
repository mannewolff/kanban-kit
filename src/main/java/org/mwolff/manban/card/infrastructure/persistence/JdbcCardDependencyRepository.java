package org.mwolff.manban.card.infrastructure.persistence;

import java.util.List;
import org.mwolff.manban.card.application.CardDependencyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verwaltet die Tabelle {@code card_dependency} (zusammengesetzter Schlüssel ohne eigene ID) direkt
 * per SQL.
 */
@Component
class JdbcCardDependencyRepository implements CardDependencyRepository {

  private final JdbcTemplate jdbc;

  JdbcCardDependencyRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void replaceDependencies(long cardId, List<Integer> dependsOnNumbers) {
    jdbc.update("DELETE FROM card_dependency WHERE card_id = ?", cardId);
    for (Integer number : dependsOnNumbers) {
      jdbc.update(
          "INSERT INTO card_dependency (card_id, depends_on_card_number) VALUES (?, ?)",
          cardId,
          number);
    }
  }

  @Override
  public List<Integer> findByCardId(long cardId) {
    return jdbc.queryForList(
        "SELECT depends_on_card_number FROM card_dependency WHERE card_id = ? "
            + "ORDER BY depends_on_card_number",
        Integer.class,
        cardId);
  }

  @Override
  public void deleteByCardId(long cardId) {
    jdbc.update("DELETE FROM card_dependency WHERE card_id = ?", cardId);
  }
}
