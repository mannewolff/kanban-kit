package org.mwolff.manban.card.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardFlowStayRepository;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.FlussStay;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;
import org.mwolff.manban.card.domain.Flusslage;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter des {@link CardFlowStayRepository}-Ports per SQL (Plan #1765, E1, E20): {@code
 * card_flow_stay} und die eine Zeile von {@code card_flow_capture} aus {@code V56}.
 */
@Component
class CardFlowStayRepositoryAdapter implements CardFlowStayRepository {

  private static final String SPALTEN =
      "SELECT id, card_id, board_id, status, entered_at, left_at, entered_known, ended_by"
          + " FROM card_flow_stay";

  private static final String INSERT =
      "INSERT INTO card_flow_stay"
          + " (card_id, board_id, status, entered_at, left_at, entered_known, ended_by)"
          + " VALUES (:cardId, :boardId, :status, :enteredAt, :leftAt, :enteredKnown, :endedBy)";

  /** Name des benannten SQL-Parameters für die Karten-ID (Sonar java:S1192). */
  private static final String P_CARD_ID = "cardId";

  private static final RowMapper<FlussStay> STAY =
      (rs, rowNum) ->
          new FlussStay(
              rs.getLong("id"),
              rs.getLong("card_id"),
              rs.getLong("board_id"),
              status(rs.getString("status")),
              Objects.requireNonNull(zeit(rs, "entered_at")),
              zeit(rs, "left_at"),
              rs.getBoolean("entered_known"),
              endedBy(rs.getString("ended_by")));

  private static final RowMapper<ErfassungsMarke> MARKEN_ZEILE =
      (rs, rowNum) ->
          new ErfassungsMarke(
              Objects.requireNonNull(zeit(rs, "since")), zeit(rs, "reconstructed_at"));

  private final NamedParameterJdbcTemplate jdbc;

  CardFlowStayRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<FlussStay> findByBoardId(long boardId) {
    return jdbc.query(
        SPALTEN + " WHERE board_id = :boardId ORDER BY entered_at, id",
        Map.of("boardId", boardId),
        STAY);
  }

  @Override
  public Optional<FlussStay> openFor(long cardId) {
    return jdbc
        .query(
            SPALTEN + " WHERE card_id = :cardId AND left_at IS NULL",
            Map.of(P_CARD_ID, cardId),
            STAY)
        .stream()
        .findFirst();
  }

  @Override
  public void open(long cardId, Flusslage lage, Instant enteredAt) {
    jdbc.update(
        INSERT,
        parameter(
            new FlussStay(
                null, cardId, lage.boardId(), lage.status(), enteredAt, null, true, null)));
  }

  @Override
  public void close(long cardId, Instant leftAt, EndedBy endedBy) {
    jdbc.update(
        "UPDATE card_flow_stay SET left_at = :leftAt, ended_by = :endedBy"
            + " WHERE card_id = :cardId AND left_at IS NULL",
        new MapSqlParameterSource()
            .addValue(P_CARD_ID, cardId)
            .addValue("leftAt", utc(leftAt))
            .addValue("endedBy", endedBy.name()));
  }

  @Override
  public void nachtragen(List<FlussStay> aufenthalte) {
    jdbc.batchUpdate(
        INSERT,
        aufenthalte.stream()
            .map(CardFlowStayRepositoryAdapter::parameter)
            .toArray(MapSqlParameterSource[]::new));
  }

  @Override
  public List<FlussStay> findEnteredUpTo(Instant zeitpunkt) {
    return jdbc.query(
        SPALTEN + " WHERE entered_at <= :zeitpunkt ORDER BY card_id, entered_at, id",
        Map.of("zeitpunkt", utc(zeitpunkt)),
        STAY);
  }

  @Override
  public void eintrittKorrigieren(long stayId, Instant enteredAt, boolean enteredKnown) {
    jdbc.update(
        "UPDATE card_flow_stay SET entered_at = :enteredAt, entered_known = :enteredKnown"
            + " WHERE id = :id",
        new MapSqlParameterSource()
            .addValue("id", stayId)
            .addValue("enteredAt", utc(enteredAt))
            .addValue("enteredKnown", enteredKnown));
  }

  /**
   * Die eine Zeile, die V56 anlegt. Fehlt sie, ist das Schema beschädigt — {@code queryForObject}
   * scheitert dann laut, statt einen Beginn zu erfinden.
   */
  @Override
  public ErfassungsMarke marke() {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT since, reconstructed_at FROM card_flow_capture", Map.of(), MARKEN_ZEILE));
  }

  @Override
  public ErfassungsMarke markeSperren() {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT since, reconstructed_at FROM card_flow_capture FOR UPDATE",
            Map.of(),
            MARKEN_ZEILE));
  }

  @Override
  public void markeSetzen(Instant reconstructedAt) {
    jdbc.update(
        "UPDATE card_flow_capture SET reconstructed_at = :reconstructedAt",
        Map.of("reconstructedAt", utc(reconstructedAt)));
  }

  private static MapSqlParameterSource parameter(FlussStay stay) {
    CardStatus status = stay.status();
    EndedBy endedBy = stay.endedBy();
    Instant leftAt = stay.leftAt();
    return new MapSqlParameterSource()
        .addValue(P_CARD_ID, stay.cardId())
        .addValue("boardId", stay.boardId())
        .addValue("status", status == null ? null : status.name())
        .addValue("enteredAt", utc(stay.enteredAt()))
        .addValue("leftAt", leftAt == null ? null : utc(leftAt))
        .addValue("enteredKnown", stay.enteredKnown())
        .addValue("endedBy", endedBy == null ? null : endedBy.name());
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static @Nullable Instant zeit(ResultSet rs, String spalte) throws SQLException {
    OffsetDateTime wert = rs.getObject(spalte, OffsetDateTime.class);
    return wert == null ? null : wert.toInstant();
  }

  private static @Nullable CardStatus status(@Nullable String wert) {
    return wert == null ? null : CardStatus.valueOf(wert);
  }

  private static @Nullable EndedBy endedBy(@Nullable String wert) {
    return wert == null ? null : EndedBy.valueOf(wert);
  }
}
