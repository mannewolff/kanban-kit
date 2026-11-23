package org.mwolff.manban.deployment.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.deployment.application.Auslieferungsdaten;
import org.mwolff.manban.deployment.application.DeploymentStore;
import org.mwolff.manban.deployment.application.GemeldeterStand;
import org.mwolff.manban.deployment.application.NeueMeldung;
import org.mwolff.manban.deployment.application.NeueMeldung.Commit;
import org.mwolff.manban.deployment.domain.Ausgang;
import org.mwolff.manban.deployment.domain.Auslieferungsart;
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
 *
 * <p>Art und Adresse aus {@code V81} (Issue #2215, Plan #2206 E10): Letzter Stand und Commits der
 * Auswertung betrachten nur Veröffentlichungen — {@code art} leer oder {@code veroeffentlichung}.
 */
@Component
class JdbcDeploymentStore implements DeploymentStore {

  private static final String P_PROJECT_ID = "projectId";

  private static final String P_ART = "art";

  /** Bedingung einer Veröffentlichung; eine Meldung ohne Art zählt dazu. */
  private static final String VEROEFFENTLICHUNG = "(art IS NULL OR art = 'veroeffentlichung')";

  private static final String INSERT_VERSUCH =
      "INSERT INTO deployment_attempt (project_id, attempt_id, outcome, finished_at, revision,"
          + " token_name, created_at, art, url)"
          + " VALUES (:projectId, :attemptId, :outcome, :finishedAt, :revision, :tokenName,"
          + " :createdAt, :art, :url) RETURNING id";

  private static final String INSERT_COMMIT =
      "INSERT INTO deployment_commit (attempt_ref, project_id, sha, committed_at)"
          + " VALUES (:attemptRef, :projectId, :sha, :committedAt)";

  private static final String SPALTEN = "id, attempt_id, outcome, finished_at, revision, art";

  private static final String SELECT_VERSUCH =
      "SELECT "
          + SPALTEN
          + ", url FROM deployment_attempt"
          + " WHERE project_id = :projectId AND attempt_id = :attemptId";

  private static final String SELECT_COMMITS_DES_VERSUCHS =
      "SELECT sha, committed_at FROM deployment_commit WHERE attempt_ref = :attemptRef";

  private static final String SELECT_VERSUCHE =
      "SELECT " + SPALTEN + " FROM deployment_attempt WHERE project_id = :projectId";

  /**
   * Die Commits, deren Auslieferung ins Fenster fallen kann (E12). Je {@code sha} gilt der früheste
   * meldende Versuch; ausgelassen wird ein {@code sha}, für den ein erfolgreicher Versuch am oder
   * nach dieser ersten Meldung, aber vor dem Fenster liegt — dieselbe Regel wie in der {@code
   * AuslieferungsErmittlung}. Bleibt ein {@code sha}, kommen alle seine Zeilen mit, damit die
   * Ermittlung den ersten meldenden Versuch selbst bestimmt.
   *
   * <p>Gerechnet wird nur über Veröffentlichungen (Issue #2215): Ein Commit, den zuerst eine
   * Vorschau meldete, zählt erst mit der Veröffentlichung, die ihn meldet.
   */
  private static final String SELECT_COMMITS =
      "WITH veroeffentlicht AS ("
          + " SELECT id, outcome, finished_at FROM deployment_attempt"
          + " WHERE project_id = :projectId AND "
          + VEROEFFENTLICHUNG
          + "), erste AS ("
          + " SELECT c.sha, min(a.finished_at) AS gemeldet"
          + " FROM deployment_commit c JOIN veroeffentlicht a ON a.id = c.attempt_ref"
          + " GROUP BY c.sha)"
          + " SELECT c.attempt_ref, c.sha, c.committed_at"
          + " FROM deployment_commit c JOIN veroeffentlicht v ON v.id = c.attempt_ref"
          + " JOIN erste e ON e.sha = c.sha"
          + " WHERE NOT EXISTS ("
          + " SELECT 1 FROM veroeffentlicht s"
          + " WHERE s.outcome = 'SUCCESS'"
          + " AND s.finished_at >= e.gemeldet AND s.finished_at < :fensterBeginn)";

  /** Ordnung des jüngsten Versuchs: spätester Abschluss, bei Gleichstand die höhere Id. */
  private static final String JUENGSTER_ORDNUNG = " ORDER BY finished_at DESC, id DESC LIMIT 1";

  private static final String SELECT_LETZTER =
      SELECT_VERSUCHE + " AND " + VEROEFFENTLICHUNG + JUENGSTER_ORDNUNG;

  private static final String SELECT_JUENGSTER =
      "SELECT revision, url, finished_at, created_at FROM deployment_attempt"
          + " WHERE project_id = :projectId AND ";

  private static final String SELECT_JUENGSTE_VORSCHAU =
      SELECT_JUENGSTER + "art = 'vorschau'" + JUENGSTER_ORDNUNG;

  private static final String SELECT_JUENGSTE_VEROEFFENTLICHUNG =
      SELECT_JUENGSTER + VEROEFFENTLICHUNG + JUENGSTER_ORDNUNG;

  private static final RowMapper<Auslieferungsversuch> VERSUCH = (rs, n) -> versuchAus(rs);

  private static final RowMapper<GemeldeterStand> STAND =
      (rs, n) ->
          new GemeldeterStand(
              rs.getString("revision"),
              rs.getString("url"),
              zeitpunkt(rs, "finished_at"),
              zeitpunkt(rs, "created_at"));

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
                    .addValue("createdAt", zeitpunkt(eingang))
                    .addValue(P_ART, wert(meldung.art()))
                    .addValue("url", meldung.url()),
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
    List<VersuchMitAdresse> treffer =
        jdbc.query(
            SELECT_VERSUCH,
            new MapSqlParameterSource()
                .addValue(P_PROJECT_ID, projectId)
                .addValue("attemptId", attemptId),
            (rs, n) -> new VersuchMitAdresse(versuchAus(rs), rs.getString("url")));
    return treffer.stream()
        .findFirst()
        .map(
            gefunden -> {
              Auslieferungsversuch versuch = gefunden.versuch();
              return new NeueMeldung(
                  versuch.attemptId(),
                  versuch.ausgang(),
                  versuch.finishedAt(),
                  versuch.revision(),
                  jdbc.query(
                      SELECT_COMMITS_DES_VERSUCHS,
                      new MapSqlParameterSource("attemptRef", versuch.id()),
                      (rs, n) -> new Commit(rs.getString("sha"), zeitpunkt(rs, "committed_at"))),
                  versuch.art(),
                  gefunden.url());
            });
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

  @Override
  public Optional<GemeldeterStand> juengster(long projectId, Auslieferungsart art) {
    String sql =
        art == Auslieferungsart.VORSCHAU
            ? SELECT_JUENGSTE_VORSCHAU
            : SELECT_JUENGSTE_VEROEFFENTLICHUNG;
    return jdbc.query(sql, new MapSqlParameterSource(P_PROJECT_ID, projectId), STAND).stream()
        .findFirst();
  }

  private static Auslieferungsversuch versuchAus(ResultSet rs) throws SQLException {
    return new Auslieferungsversuch(
        rs.getLong("id"),
        rs.getString("attempt_id"),
        Ausgang.valueOf(rs.getString("outcome")),
        zeitpunkt(rs, "finished_at"),
        rs.getString("revision"),
        art(rs.getString(P_ART)));
  }

  private static @Nullable String wert(@Nullable Auslieferungsart art) {
    return art == null ? null : art.wert();
  }

  private static @Nullable Auslieferungsart art(@Nullable String wert) {
    return wert == null ? null : Auslieferungsart.aus(wert);
  }

  /** Ein gelesener Versuch mit der Adresse seiner Meldung. */
  private record VersuchMitAdresse(Auslieferungsversuch versuch, @Nullable String url) {}

  private static Instant zeitpunkt(ResultSet rs, String spalte) throws SQLException {
    return rs.getObject(spalte, OffsetDateTime.class).toInstant();
  }

  private static OffsetDateTime zeitpunkt(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
