package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.nightrun.application.DisruptionRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter des {@link DisruptionRepository}-Ports (Issue #1080).
 *
 * <p>Über {@link NamedParameterJdbcTemplate} wie der Schreibpfad des {@code
 * NightRunRepositoryAdapter}: Die Abfrage geht über vier Tabellen und braucht ein {@code LEFT JOIN
 * … IS NULL}, das sich als JPA-Query nur umständlich ausdrücken ließe.
 */
@Component
class DisruptionRepositoryAdapter implements DisruptionRepository {

  /**
   * Die Kandidaten, jüngster Lauf zuoberst.
   *
   * <p>Vier Bedingungen, alle in der Datenbank entscheidbar: die Gattung (eine interaktive Sitzung
   * ist kein Nachtlauf), der Abschluss (ein laufender Lauf hat noch keinen Ausgang), die Teilnahme
   * des Projekts und die fehlende Quittung. <b>Was eine Störung ist, steht hier nicht</b> — das
   * entscheidet {@code NightRunOutcome} in der Domäne, und zwar als einziger Ort.
   *
   * <p>Kein {@code LIMIT} (Plan #1072 E13): AK 4 verlangt jede offene Störung, und ein stilles
   * Limit sähe aus wie Vollständigkeit. Die Menge ist durch den Ringpuffer je Projekt nach oben
   * beschränkt.
   */
  private static final String KANDIDATEN =
      """
      SELECT r.id AS night_run_id, r.project_id, p.name AS project_name,
             r.started_at, r.no_work_reason
        FROM night_run r
        JOIN project p ON p.id = r.project_id
        LEFT JOIN night_run_disruption_ack a ON a.night_run_id = r.id
       WHERE r.kind = 'NIGHT'
         AND r.complete = true
         AND p.dashboard_participation = true
         AND a.night_run_id IS NULL
       ORDER BY r.started_at DESC, r.id DESC
      """;

  /** Lauf und Teilnahme in einer Abfrage — beide Verneinungen enden beim Aufrufer als 404. */
  private static final String ACK_ZIEL =
      """
      SELECT r.id AS night_run_id, r.project_id
        FROM night_run r
        JOIN project p ON p.id = r.project_id
       WHERE r.id = :nightRunId
         AND p.dashboard_participation = true
      """;

  /**
   * {@code ON CONFLICT DO NOTHING} ist die Idempotenz (AK 8): Der erste Quittierende bleibt
   * vermerkt, der zweite Aufruf ist ein stiller No-Op statt eines Constraint-Fehlers.
   */
  private static final String QUITTUNG =
      """
      INSERT INTO night_run_disruption_ack (night_run_id, acknowledged_by, acknowledged_at)
      VALUES (:nightRunId, :userId, :at)
      ON CONFLICT (night_run_id) DO NOTHING
      """;

  private final NamedParameterJdbcTemplate jdbc;

  DisruptionRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<DisruptionCandidate> openCandidates() {
    return jdbc.query(
        KANDIDATEN,
        new MapSqlParameterSource(),
        (rs, zeile) ->
            new DisruptionCandidate(
                rs.getLong("night_run_id"),
                rs.getLong("project_id"),
                rs.getString("project_name"),
                rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                rs.getString("no_work_reason")));
  }

  @Override
  public Optional<AckTarget> ackTarget(long nightRunId) {
    return jdbc
        .query(
            ACK_ZIEL,
            new MapSqlParameterSource("nightRunId", nightRunId),
            (rs, zeile) -> new AckTarget(rs.getLong("night_run_id"), rs.getLong("project_id")))
        .stream()
        .findFirst();
  }

  @Override
  public void acknowledge(long nightRunId, long userId, Instant at) {
    jdbc.update(
        QUITTUNG,
        new MapSqlParameterSource()
            .addValue("nightRunId", nightRunId)
            .addValue("userId", userId)
            .addValue("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC)));
  }
}
