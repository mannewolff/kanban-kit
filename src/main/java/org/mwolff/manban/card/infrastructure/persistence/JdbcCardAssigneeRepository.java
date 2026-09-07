package org.mwolff.manban.card.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.manban.card.application.CardAssigneeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
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

  /**
   * Sammelzugriff für Kartenlisten: eine Abfrage statt einer je Karte.
   *
   * <p>Die leere Eingabe wird ohne Abfrage beantwortet — {@code IN ()} ist kein gültiges SQL, und
   * ein Roundtrip für eine Antwort, die feststeht, wäre ohnehin verschenkt.
   */
  // Sonar java:S2077: Der konkatenierte Teil ist ausschließlich "platzhalter" — eine
  // Aneinanderreihung des Literals "?" per Collections.nCopies, deren einzige variable Größe die
  // Anzahl der IDs ist. Kein Zeichen der übergebenen Werte gelangt in den SQL-Text; die IDs selbst
  // sind typisierte Long-Bindeparameter (cardIds.toArray()). Damit ist die Konkatenation
  // injektionsfest — die Regel greift bereits auf die String-Verknüpfung, ohne den Datenfluss zu
  // prüfen. Derselbe Fall und dieselbe Begründung wie bei
  // JdbcCardDependencyRepository#findByCardIds
  // (Issue #625). Ein Umbau auf NamedParameterJdbcTemplate scheidet aus: Er hängte diesen
  // Sammelzugriff auf ein zweites JDBC-Template um, ohne etwas sicherer zu machen.
  @SuppressWarnings("java:S2077")
  @Override
  public Map<Long, List<Long>> findByCardIds(Collection<Long> cardIds) {
    if (cardIds.isEmpty()) {
      return Map.of();
    }
    String platzhalter = String.join(",", Collections.nCopies(cardIds.size(), "?"));
    Map<Long, List<Long>> ergebnis = new LinkedHashMap<>();
    jdbc.query(
        "SELECT card_id, user_id FROM card_assignee WHERE card_id IN ("
            + platzhalter
            + ") ORDER BY card_id, user_id",
        (RowCallbackHandler)
            rs ->
                ergebnis
                    .computeIfAbsent(rs.getLong("card_id"), k -> new ArrayList<>())
                    .add(rs.getLong("user_id")),
        cardIds.toArray());
    return ergebnis;
  }

  @Override
  public void deleteByCardId(long cardId) {
    jdbc.update("DELETE FROM card_assignee WHERE card_id = ?", cardId);
  }
}
