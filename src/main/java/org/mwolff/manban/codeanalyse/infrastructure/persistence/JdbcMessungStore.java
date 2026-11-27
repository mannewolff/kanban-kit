package org.mwolff.manban.codeanalyse.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.codeanalyse.application.MessungStore;
import org.mwolff.manban.codeanalyse.domain.Messung;
import org.mwolff.manban.codeanalyse.domain.Zustand;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * JDBC-Adapter der gemeldeten Messungen der Codeanalyse (Issue #2274) — Tabelle aus {@code V83}.
 * Die erste Meldung je Projekt und {@code published_commit} gilt (Plan #2272 E3): Das Einfügen
 * weicht einem Konflikt aus, danach wird die gespeicherte Zeile gelesen, sodass auch zwei
 * gleichzeitige Meldungen dieselbe Antwort bekommen. Die Transaktion stellt der Dienst.
 */
@Component
class JdbcMessungStore implements MessungStore {

  private static final String P_PROJECT_ID = "projectId";

  private static final String P_PUBLISHED_COMMIT = "publishedCommit";

  private static final String VORHER = "before_";

  private static final String ENDE = "final_";

  private static final String INSERT =
      "INSERT INTO code_analysis_measurement (project_id, published_commit, published_at,"
          + " base_commit, kit_version, counts, reason, cards, before_bugs,"
          + " before_vulnerabilities, before_code_smells, before_changed_lines, final_bugs,"
          + " final_vulnerabilities, final_code_smells, final_changed_lines, token_name,"
          + " created_at) VALUES (:projectId, :publishedCommit, :publishedAt, :baseCommit,"
          + " :kitVersion, :counts, :reason, :cards, :before_bugs, :before_vulnerabilities,"
          + " :before_code_smells, :before_changed_lines, :final_bugs, :final_vulnerabilities,"
          + " :final_code_smells, :final_changed_lines, :tokenName, :createdAt)"
          + " ON CONFLICT (project_id, published_commit) DO NOTHING";

  private static final String SELECT =
      "SELECT published_commit, published_at, base_commit, kit_version, counts, reason, cards,"
          + " before_bugs, before_vulnerabilities, before_code_smells, before_changed_lines,"
          + " final_bugs, final_vulnerabilities, final_code_smells, final_changed_lines"
          + " FROM code_analysis_measurement WHERE project_id = :projectId";

  private static final String SELECT_GESPEICHERTE =
      SELECT + " AND published_commit = :publishedCommit";

  private static final String SELECT_AB =
      SELECT + " AND published_at >= :ab ORDER BY published_at, id";

  private static final String SELECT_ERSTES =
      "SELECT min(published_at) FROM code_analysis_measurement WHERE project_id = :projectId";

  private static final String SELECT_VORHANDEN =
      "SELECT EXISTS (SELECT 1 FROM code_analysis_measurement WHERE project_id = :projectId)";

  private static final RowMapper<Messung> MESSUNG = (rs, n) -> messungAus(rs);

  private final NamedParameterJdbcTemplate jdbc;

  JdbcMessungStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Messung speichereErste(
      long projectId, String tokenName, Messung messung, Instant eingang) {
    MapSqlParameterSource werte =
        new MapSqlParameterSource()
            .addValue(P_PROJECT_ID, projectId)
            .addValue(P_PUBLISHED_COMMIT, messung.publishedCommit())
            .addValue("publishedAt", zeitpunkt(messung.publishedAt()))
            .addValue("baseCommit", messung.baseCommit())
            .addValue("kitVersion", messung.kitVersion())
            .addValue("counts", messung.zaehlt())
            .addValue("reason", messung.grund(), Types.VARCHAR)
            .addValue("cards", messung.karten())
            .addValue("tokenName", tokenName)
            .addValue("createdAt", zeitpunkt(eingang));
    zustand(werte, VORHER, messung.vorher());
    zustand(werte, ENDE, messung.endzustand());
    jdbc.update(INSERT, werte);
    return Objects.requireNonNull(
        jdbc.queryForObject(
            SELECT_GESPEICHERTE,
            new MapSqlParameterSource()
                .addValue(P_PROJECT_ID, projectId)
                .addValue(P_PUBLISHED_COMMIT, messung.publishedCommit()),
            MESSUNG));
  }

  @Override
  public List<Messung> ladeAb(long projectId, Instant ab) {
    return jdbc.query(
        SELECT_AB,
        new MapSqlParameterSource().addValue(P_PROJECT_ID, projectId).addValue("ab", zeitpunkt(ab)),
        MESSUNG);
  }

  @Override
  public Optional<Instant> erstesPublishedAt(long projectId) {
    OffsetDateTime erstes =
        jdbc.queryForObject(
            SELECT_ERSTES,
            new MapSqlParameterSource(P_PROJECT_ID, projectId),
            OffsetDateTime.class);
    return Optional.ofNullable(erstes).map(OffsetDateTime::toInstant);
  }

  @Override
  public boolean hatMessungen(long projectId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            SELECT_VORHANDEN, new MapSqlParameterSource(P_PROJECT_ID, projectId), Boolean.class));
  }

  private static void zustand(
      MapSqlParameterSource werte, String praefix, @Nullable Zustand zustand) {
    zahl(werte, praefix + "bugs", zustand == null ? null : zustand.fehler());
    zahl(werte, praefix + "vulnerabilities", zustand == null ? null : zustand.sicherheitsluecken());
    zahl(werte, praefix + "code_smells", zustand == null ? null : zustand.wartbarkeitsmaengel());
    zahl(werte, praefix + "changed_lines", zustand == null ? null : zustand.zeilen());
  }

  /** Eine Zahl eines Zustands; der Typ steht dabei, weil sie leer sein darf. */
  private static void zahl(MapSqlParameterSource werte, String name, @Nullable Integer wert) {
    werte.addValue(name, wert, Types.INTEGER);
  }

  private static Messung messungAus(ResultSet rs) throws SQLException {
    return new Messung(
        rs.getString("published_commit"),
        rs.getObject("published_at", OffsetDateTime.class).toInstant(),
        rs.getString("base_commit"),
        rs.getString("kit_version"),
        rs.getBoolean("counts"),
        rs.getString("reason"),
        rs.getInt("cards"),
        zustandAus(rs, VORHER),
        zustandAus(rs, ENDE));
  }

  /** Der Zustand der Zeile; leer, wenn sie ihn nicht trägt. */
  private static @Nullable Zustand zustandAus(ResultSet rs, String praefix) throws SQLException {
    if (rs.getObject(praefix + "bugs", Integer.class) == null) {
      return null;
    }
    return new Zustand(
        rs.getInt(praefix + "bugs"),
        rs.getInt(praefix + "vulnerabilities"),
        rs.getInt(praefix + "code_smells"),
        rs.getInt(praefix + "changed_lines"));
  }

  private static OffsetDateTime zeitpunkt(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
