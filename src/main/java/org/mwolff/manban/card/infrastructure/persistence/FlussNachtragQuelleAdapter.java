package org.mwolff.manban.card.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.FlussNachtragQuelle;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Archivereignis;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Kartenstand;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spalte;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spaltenfolge;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spaltenort;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spaltenzeile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter des {@link FlussNachtragQuelle}-Ports per SQL (Issue #1770, Plan #1765 E6): vier Abfragen
 * über den ganzen Bestand, je Karte gruppiert. Das Board einer Spaltenzeile kommt aus {@code
 * board_column.board_id} ihrer Spalte, nicht aus dem heutigen {@code card.board_id}.
 */
@Component
class FlussNachtragQuelleAdapter implements FlussNachtragQuelle {

  private final JdbcTemplate jdbc;

  FlussNachtragQuelleAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Bestand lesen() {
    Map<Long, List<Spaltenzeile>> zeilen = new LinkedHashMap<>();
    jdbc.query(
        "SELECT t.card_id, t.column_id, bc.board_id, t.column_name, t.entered_at, t.left_at"
            + " FROM card_column_transition t"
            + " LEFT JOIN board_column bc ON bc.id = t.column_id"
            + " ORDER BY t.card_id, t.entered_at, t.id",
        rs -> {
          long columnId = rs.getLong("column_id");
          Spaltenort ort = rs.wasNull() ? null : new Spaltenort(columnId, rs.getLong("board_id"));
          zeilen
              .computeIfAbsent(rs.getLong("card_id"), id -> new ArrayList<>())
              .add(
                  new Spaltenzeile(
                      ort,
                      rs.getString("column_name"),
                      Objects.requireNonNull(zeit(rs, "entered_at")),
                      zeit(rs, "left_at")));
        });
    Map<Long, List<Archivereignis>> ereignisse = new LinkedHashMap<>();
    jdbc.query(
        "SELECT card_id, type, detail, created_at FROM card_activity"
            + " WHERE type IN ('ARCHIVED', 'RESTORED') ORDER BY card_id, created_at, id",
        rs -> {
          ereignisse
              .computeIfAbsent(rs.getLong("card_id"), id -> new ArrayList<>())
              .add(
                  new Archivereignis(
                      CardActivityType.valueOf(rs.getString("type")),
                      Objects.requireNonNull(zeit(rs, "created_at")),
                      rs.getString("detail")));
        });
    List<Kartenverlauf> karten =
        jdbc.query(
            "SELECT id, status, created_at, moved_to_done_at, deleted_at FROM card"
                + " WHERE status IS NOT NULL ORDER BY id",
            (rs, rowNum) -> {
              long cardId = rs.getLong("id");
              return new Kartenverlauf(
                  new Kartenstand(
                      cardId,
                      CardStatus.valueOf(rs.getString("status")),
                      Objects.requireNonNull(zeit(rs, "created_at")),
                      zeit(rs, "moved_to_done_at"),
                      zeit(rs, "deleted_at")),
                  zeilen.getOrDefault(cardId, List.of()),
                  ereignisse.getOrDefault(cardId, List.of()));
            });
    return new Bestand(karten, spaltenfolgen());
  }

  private List<Spaltenfolge> spaltenfolgen() {
    Map<Long, List<Spalte>> folgen = new LinkedHashMap<>();
    jdbc.query(
        "SELECT id, board_id, name FROM board_column ORDER BY board_id, position",
        rs -> {
          folgen
              .computeIfAbsent(rs.getLong("board_id"), id -> new ArrayList<>())
              .add(new Spalte(rs.getLong("id"), rs.getString("name")));
        });
    return folgen.entrySet().stream()
        .map(folge -> new Spaltenfolge(folge.getKey(), folge.getValue()))
        .toList();
  }

  private static @Nullable Instant zeit(ResultSet rs, String spalte) throws SQLException {
    OffsetDateTime wert = rs.getObject(spalte, OffsetDateTime.class);
    return wert == null ? null : wert.toInstant();
  }
}
