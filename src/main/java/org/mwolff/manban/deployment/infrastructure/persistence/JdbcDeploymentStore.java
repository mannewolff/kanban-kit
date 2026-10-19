package org.mwolff.manban.deployment.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.mwolff.manban.deployment.application.Auslieferungsdaten;
import org.mwolff.manban.deployment.application.DeploymentStore;
import org.mwolff.manban.deployment.application.NeueMeldung;
import org.mwolff.manban.deployment.application.NeueMeldung.Commit;
import org.mwolff.manban.deployment.domain.Ausgang;
import org.mwolff.manban.deployment.domain.Auslieferungsversuch;
import org.mwolff.manban.deployment.domain.GemeldeterCommit;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

/**
 * JDBC-Adapter der gemeldeten Auslieferungsversuche (Issue #1833) — zwei Tabellen aus {@code V59}:
 * Versuche und ihre Commits. Die Transaktion stellt der Dienst.
 */
@Component
class JdbcDeploymentStore implements DeploymentStore {

  private static final String P_PROJECT_ID = "projectId";

  private static final String INSERT_VERSUCH =
      "INSERT INTO deployment_attempt (project_id, attempt_id, outcome, finished_at, revision,"
          + " token_name, created_at)"
          + " VALUES (:projectId, :attemptId, :outcome, :finishedAt, :revision, :tokenName,"
          + " :createdAt) RETURNING id";

  private static final String INSERT_COMMIT =
      "INSERT INTO deployment_commit (attempt_ref, project_id, sha, committed_at)"
          + " VALUES (:attemptRef, :projectId, :sha, :committedAt)";

  private static final String SELECT_VERSUCH =
      "SELECT id, attempt_id, outcome, finished_at, revision FROM deployment_attempt"
          + " WHERE project_id = :projectId AND attempt_id = :attemptId";

  private static final String SELECT_COMMITS_DES_VERSUCHS =
      "SELECT sha, committed_at FROM deployment_commit WHERE attempt_ref = :attemptRef";

  private static final String SELECT_VERSUCHE =
      "SELECT id, attempt_id, outcome, finished_at, revision FROM deployment_attempt"
          + " WHERE project_id = :projectId";

  /**
   * Die Commits, deren Auslieferung ins Fenster fallen kann (E12). Je {@code sha} gilt der früheste
   * meldende Versuch; ausgelassen wird ein {@code sha}, für den ein erfolgreicher Versuch am oder
   * nach dieser ersten Meldung, aber vor dem Fenster liegt — dieselbe Regel wie in der {@code
   * AuslieferungsErmittlung}. Bleibt ein {@code sha}, kommen alle seine Zeilen mit, damit die
   * Ermittlung den ersten meldenden Versuch selbst bestimmt.
   */
  private static final String SELECT_COMMITS =
      "WITH erste AS ("
          + " SELECT c.sha, min(a.finished_at) AS gemeldet"
          + " FROM deployment_commit c JOIN deployment_attempt a ON a.id = c.attempt_ref"
          + " WHERE c.project_id = :projectId GROUP BY c.sha)"
          + " SELECT c.attempt_ref, c.sha, c.committed_at"
          + " FROM deployment_commit c JOIN erste e ON e.sha = c.sha"
          + " WHERE c.project_id = :projectId AND NOT EXISTS ("
          + " SELECT 1 FROM deployment_attempt s"
          + " WHERE s.project_id = :projectId AND s.outcome = 'SUCCESS'"
          + " AND s.finished_at >= e.gemeldet AND s.finished_at < :fensterBeginn)";

  private static final String SELECT_LETZTER =
      SELECT_VERSUCHE + " ORDER BY finished_at DESC, id DESC LIMIT 1";

  private static final RowMapper<Auslieferungsversuch> VERSUCH =
      (rs, n) ->
          new Auslieferungsversuch(
              rs.getLong("id"),
              rs.getString("attempt_id"),
              Ausgang.valueOf(rs.getString("outcome")),
              zeitpunkt(rs, "finished_at"),
              rs.getString("revision"));

  private final NamedParameterJdbcTemplate jdbc;

  JdbcDeploymentStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void fuegeEin(long projectId, String tokenName, NeueMeldung meldung, Instant eingang) {
    Long id =
        Objects.requireNonNull(
            jdbc.queryForObject(
                INSERT_VERSUCH,
                new MapSqlParameterSource()
                    .addValue(P_PROJECT_ID, projectId)
                    .addValue("attemptId", meldung.attemptId())
                    .addValue("outcome", meldung.outcome().name())
                    .addValue("finishedAt", zeitpunkt(meldung.finishedAt()))
                    .addValue("revision", meldung.revision())
                    .addValue("tokenName", tokenName)
                    .addValue("createdAt", zeitpunkt(eingang)),
                Long.class));
    SqlParameterSource[] commits =
        meldung.commits().stream()
            .map(
                commit ->
                    new MapSqlParameterSource()
                        .addValue("attemptRef", id)
                        .addValue(P_PROJECT_ID, projectId)
                        .addValue("sha", commit.sha())
                        .addValue("committedAt", zeitpunkt(commit.committedAt())))
            .toArray(SqlParameterSource[]::new);
    if (commits.length > 0) {
      jdbc.batchUpdate(INSERT_COMMIT, commits);
    }
  }

  @Override
  public Optional<NeueMeldung> findeVersuch(long projectId, String attemptId) {
    List<Auslieferungsversuch> treffer =
        jdbc.query(
            SELECT_VERSUCH,
            new MapSqlParameterSource()
                .addValue(P_PROJECT_ID, projectId)
                .addValue("attemptId", attemptId),
            VERSUCH);
    return treffer.stream()
        .findFirst()
        .map(
            versuch ->
                new NeueMeldung(
                    versuch.attemptId(),
                    versuch.ausgang(),
                    versuch.finishedAt(),
                    versuch.revision(),
                    jdbc.query(
                        SELECT_COMMITS_DES_VERSUCHS,
                        new MapSqlParameterSource("attemptRef", versuch.id()),
                        (rs, n) ->
                            new Commit(rs.getString("sha"), zeitpunkt(rs, "committed_at")))));
  }

  @Override
  public Auslieferungsdaten ladeAuswertung(long projectId, Instant fensterBeginn) {
    MapSqlParameterSource projekt = new MapSqlParameterSource(P_PROJECT_ID, projectId);
    List<Auslieferungsversuch> versuche = jdbc.query(SELECT_VERSUCHE, projekt, VERSUCH);
    List<GemeldeterCommit> commits =
        jdbc.query(
            SELECT_COMMITS,
            new MapSqlParameterSource()
                .addValue(P_PROJECT_ID, projectId)
                .addValue("fensterBeginn", zeitpunkt(fensterBeginn)),
            (rs, n) ->
                new GemeldeterCommit(
                    rs.getLong("attempt_ref"), rs.getString("sha"), zeitpunkt(rs, "committed_at")));
    return new Auslieferungsdaten(versuche, commits);
  }

  @Override
  public Optional<Auslieferungsversuch> letzterVersuch(long projectId) {
    return jdbc
        .query(SELECT_LETZTER, new MapSqlParameterSource(P_PROJECT_ID, projectId), VERSUCH)
        .stream()
        .findFirst();
  }

  private static Instant zeitpunkt(ResultSet rs, String spalte) throws SQLException {
    return rs.getObject(spalte, OffsetDateTime.class).toInstant();
  }

  private static OffsetDateTime zeitpunkt(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
