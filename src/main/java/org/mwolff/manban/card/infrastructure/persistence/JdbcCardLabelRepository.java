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

  /** Name des benannten SQL-Parameters für die Karten-ID (Sonar java:S1192). */
  private static final String P_CARD_ID = "cardId";

  /** Name des benannten SQL-Parameters für die Label-ID (Sonar java:S1192). */
  private static final String P_LABEL_ID = "labelId";

  private final NamedParameterJdbcTemplate jdbc;

  JdbcCardLabelRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void replaceLabels(long cardId, List<Long> labelIds) {
    jdbc.update("DELETE FROM card_label WHERE card_id = :cardId", Map.of(P_CARD_ID, cardId));
    for (Long labelId : labelIds) {
      jdbc.update(
          "INSERT INTO card_label (card_id, label_id) VALUES (:cardId, :labelId)",
          Map.of(P_CARD_ID, cardId, P_LABEL_ID, labelId));
    }
  }

  /**
   * Ein einzelnes {@code INSERT … ON CONFLICT DO NOTHING} — bewusst ohne vorgelagertes {@code
   * SELECT}: Der Primärschlüssel {@code pk_card_label} entscheidet über die Idempotenz, damit
   * zwischen Prüfung und Einfügen kein Rennen offen bleibt.
   */
  @Override
  public boolean addLabel(long cardId, long labelId) {
    return jdbc.update(
            "INSERT INTO card_label (card_id, label_id) VALUES (:cardId, :labelId)"
                + " ON CONFLICT (card_id, label_id) DO NOTHING",
            Map.of(P_CARD_ID, cardId, P_LABEL_ID, labelId))
        > 0;
  }

  @Override
  public boolean removeLabel(long cardId, long labelId) {
    return jdbc.update(
            "DELETE FROM card_label WHERE card_id = :cardId AND label_id = :labelId",
            Map.of(P_CARD_ID, cardId, P_LABEL_ID, labelId))
        > 0;
  }

  @Override
  public List<Long> findByCardId(long cardId) {
    return jdbc.queryForList(
        "SELECT label_id FROM card_label WHERE card_id = :cardId ORDER BY label_id",
        Map.of(P_CARD_ID, cardId),
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
