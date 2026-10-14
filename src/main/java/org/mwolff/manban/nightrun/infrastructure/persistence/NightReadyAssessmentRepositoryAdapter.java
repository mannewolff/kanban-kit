package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.application.NightReadyAssessmentRepository;
import org.mwolff.manban.nightrun.domain.ReadyBewertung;
import org.mwolff.manban.nightrun.domain.ReadyBewertung.Eintrag;
import org.mwolff.manban.nightrun.domain.ReadyBewertung.Grund;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

/**
 * JDBC-Adapter der Bewertung der Ready-Karten (Issue #1726) — drei Tabellen aus {@code V54}: Kopf,
 * Einträge, Gründe.
 *
 * <p>Ersetzt wird durch Löschen des Kopfs und Neuschreiben; Einträge und Gründe fallen über {@code
 * ON DELETE CASCADE} mit. Die Transaktion stellt der Dienst, ebenso die Projektsperre.
 */
@Component
class NightReadyAssessmentRepositoryAdapter implements NightReadyAssessmentRepository {

  private static final String P_PROJECT_ID = "projectId";

  private static final String P_CARD_NUMBER = "cardNumber";

  private static final String C_CARD_NUMBER = "card_number";

  /** Trennzeichen der Kartennummern in {@code card_numbers}. */
  private static final String NUMMERN_TRENNER = ",";

  /**
   * Trennzeichen der Pfade in {@code paths}: ein Zeilenumbruch und kein Komma, denn ein Pfad darf
   * ein Komma enthalten, aber keinen Zeilenumbruch.
   */
  private static final String PFAD_TRENNER = "\n";

  private static final String DELETE =
      "DELETE FROM night_ready_assessment WHERE project_id = :projectId";

  private static final String INSERT_KOPF =
      "INSERT INTO night_ready_assessment (project_id, assessed_at, run_started_at, label)"
          + " VALUES (:projectId, :assessedAt, :runStartedAt, :label)";

  private static final String INSERT_EINTRAG =
      "INSERT INTO night_ready_assessment_entry (project_id, card_number, ordinal, position)"
          + " VALUES (:projectId, :cardNumber, :ordinal, :position)";

  private static final String INSERT_GRUND =
      "INSERT INTO night_ready_assessment_reason (project_id, card_number, ordinal, code, kind,"
          + " card_numbers, paths, text)"
          + " VALUES (:projectId, :cardNumber, :ordinal, :code, :kind, :cardNumbers, :paths,"
          + " :text)";

  private static final String SELECT_KOPF =
      "SELECT assessed_at, run_started_at, label FROM night_ready_assessment"
          + " WHERE project_id = :projectId";

  private static final String SELECT_EINTRAEGE =
      "SELECT card_number, position FROM night_ready_assessment_entry"
          + " WHERE project_id = :projectId ORDER BY ordinal";

  private static final String SELECT_GRUENDE =
      "SELECT card_number, code, kind, card_numbers, paths, text"
          + " FROM night_ready_assessment_reason WHERE project_id = :projectId"
          + " ORDER BY card_number, ordinal";

  private final NamedParameterJdbcTemplate jdbc;

  NightReadyAssessmentRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ReadyBewertung> findByProjectId(long projectId) {
    MapSqlParameterSource projekt = new MapSqlParameterSource(P_PROJECT_ID, projectId);
    List<Kopf> koepfe =
        jdbc.query(
            SELECT_KOPF,
            projekt,
            (rs, n) -> {
              OffsetDateTime lauf = rs.getObject("run_started_at", OffsetDateTime.class);
              return new Kopf(
                  rs.getObject("assessed_at", OffsetDateTime.class).toInstant(),
                  lauf == null ? null : lauf.toInstant(),
                  rs.getString("label"));
            });
    if (koepfe.isEmpty()) {
      return Optional.empty();
    }
    Map<Integer, List<Grund>> gruende = new LinkedHashMap<>();
    jdbc.query(
        SELECT_GRUENDE,
        projekt,
        (RowCallbackHandler)
            rs ->
                gruende
                    .computeIfAbsent(rs.getInt(C_CARD_NUMBER), k -> new ArrayList<>())
                    .add(
                        new Grund(
                            rs.getString("code"),
                            rs.getString("kind"),
                            nummern(rs.getString("card_numbers")),
                            pfade(rs.getString("paths")),
                            rs.getString("text"))));
    List<Eintrag> eintraege =
        jdbc.query(
            SELECT_EINTRAEGE,
            projekt,
            (rs, n) -> {
              int karte = rs.getInt(C_CARD_NUMBER);
              int position = rs.getInt("position");
              return new Eintrag(
                  karte, rs.wasNull() ? null : position, gruende.getOrDefault(karte, List.of()));
            });
    Kopf kopf = koepfe.getFirst();
    return Optional.of(
        new ReadyBewertung(kopf.assessedAt(), kopf.runStartedAt(), kopf.label(), eintraege));
  }

  @Override
  public void ersetze(long projectId, ReadyBewertung bewertung) {
    jdbc.update(DELETE, new MapSqlParameterSource(P_PROJECT_ID, projectId));
    Instant lauf = bewertung.runStartedAt();
    jdbc.update(
        INSERT_KOPF,
        new MapSqlParameterSource()
            .addValue(P_PROJECT_ID, projectId)
            .addValue("assessedAt", zeitpunkt(bewertung.assessedAt()))
            .addValue(
                "runStartedAt",
                lauf == null ? null : zeitpunkt(lauf),
                Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("label", bewertung.label()));
    List<SqlParameterSource> eintraege = new ArrayList<>();
    List<SqlParameterSource> gruende = new ArrayList<>();
    List<Eintrag> gemeldet = bewertung.entries();
    for (int ordinal = 0; ordinal < gemeldet.size(); ordinal++) {
      Eintrag eintrag = gemeldet.get(ordinal);
      eintraege.add(
          new MapSqlParameterSource()
              .addValue(P_PROJECT_ID, projectId)
              .addValue(P_CARD_NUMBER, eintrag.cardNumber())
              .addValue("ordinal", ordinal)
              .addValue("position", eintrag.position(), Types.INTEGER));
      List<Grund> liste = eintrag.reasons();
      for (int nr = 0; nr < liste.size(); nr++) {
        gruende.add(grundParameter(projectId, eintrag.cardNumber(), nr, liste.get(nr)));
      }
    }
    if (!eintraege.isEmpty()) {
      jdbc.batchUpdate(INSERT_EINTRAG, eintraege.toArray(SqlParameterSource[]::new));
    }
    if (!gruende.isEmpty()) {
      jdbc.batchUpdate(INSERT_GRUND, gruende.toArray(SqlParameterSource[]::new));
    }
  }

  private static SqlParameterSource grundParameter(
      long projectId, int cardNumber, int ordinal, Grund grund) {
    return new MapSqlParameterSource()
        .addValue(P_PROJECT_ID, projectId)
        .addValue(P_CARD_NUMBER, cardNumber)
        .addValue("ordinal", ordinal)
        .addValue("code", grund.code())
        .addValue("kind", grund.kind(), Types.VARCHAR)
        .addValue(
            "cardNumbers",
            grund.cardNumbers().isEmpty()
                ? null
                : grund.cardNumbers().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(NUMMERN_TRENNER)),
            Types.VARCHAR)
        .addValue(
            "paths",
            grund.paths().isEmpty() ? null : String.join(PFAD_TRENNER, grund.paths()),
            Types.VARCHAR)
        .addValue("text", grund.text());
  }

  private static List<Integer> nummern(@Nullable String gespeichert) {
    if (gespeichert == null) {
      return List.of();
    }
    return Arrays.stream(gespeichert.split(NUMMERN_TRENNER)).map(Integer::valueOf).toList();
  }

  private static List<String> pfade(@Nullable String gespeichert) {
    return gespeichert == null ? List.of() : List.of(gespeichert.split(PFAD_TRENNER));
  }

  private static OffsetDateTime zeitpunkt(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  /** Der Kopf einer gespeicherten Bewertung. */
  private record Kopf(Instant assessedAt, @Nullable Instant runStartedAt, String label) {}
}
