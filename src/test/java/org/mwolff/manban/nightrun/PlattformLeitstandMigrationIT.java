package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Timestamp;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V36__plattform_leitstand.sql} (Issue #1076, Plan #1072 E5) gegen einen
 * Datenbestand, der <b>vor</b> der Migration angelegt wurde.
 *
 * <p>Die Migration gibt {@code project} die Teilnahme am Plattform-Leitstand (Vorgabe {@code
 * false}, AK 17 — Bestand und neue Projekte sind nicht angehakt) und legt {@code
 * night_run_disruption_ack} für die Quittung angezeigter Störungen an. Eigener Container, um
 * gezielt bis {@code V35} zu migrieren, bevor der Bestand entsteht.
 */
class PlattformLeitstandMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  private static JdbcTemplate jdbc;
  private static long projectId;
  private static long nightRunId;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void bestandBisV35AnlegenUndDannNachV36Migrieren() {
    DriverManagerDataSource dataSource = datenquelle(POSTGRES.getDatabaseName());
    jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "35");
    seedBestand();
    migrateTo(dataSource, "36");
  }

  @Test
  void bestandsprojektHatDieTeilnahmeAmPlattformLeitstandNichtAngehakt() {
    assertThat(
            jdbc.queryForObject(
                "SELECT dashboard_participation FROM project WHERE id = ?",
                Boolean.class,
                projectId))
        .as("AK 17: Bestand ist nicht angehakt")
        .isFalse();
  }

  @Test
  void dieTeilnahmeSpalteIstNichtNullbarMitVorgabewertFalse() {
    assertThat(
            jdbc.query(
                "SELECT is_nullable, column_default FROM information_schema.columns"
                    + " WHERE table_name = 'project' AND column_name = 'dashboard_participation'",
                (rs, row) -> tuple(rs.getString("is_nullable"), rs.getString("column_default"))))
        .as("Nullbarkeit und Vorgabewert der Teilnahme-Spalte")
        .containsExactly(tuple("NO", "false"));
  }

  @Test
  void nightRunDisruptionAckExistiertMitDenDreiSpalten() {
    assertThat(
            jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns"
                    + " WHERE table_name = 'night_run_disruption_ack' ORDER BY column_name",
                String.class))
        .as("die drei Spalten der Quittungstabelle")
        .containsExactly("acknowledged_at", "acknowledged_by", "night_run_id");
  }

  @Test
  void nightRunDisruptionAckLaesstSichZuEinemNachtlaufEintragen() {
    jdbc.update(
        "INSERT INTO night_run_disruption_ack (night_run_id, acknowledged_at) VALUES (?, now())",
        nightRunId);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM night_run_disruption_ack WHERE night_run_id = ?",
                Integer.class,
                nightRunId))
        .isEqualTo(1);
  }

  @Test
  void flywayLaeuftAufEinerLeerenDatenbankBisV36Durch() {
    jdbc.execute("CREATE DATABASE leer");
    DriverManagerDataSource leer = datenquelle("leer");

    migrateTo(leer, "36");

    JdbcTemplate aufLeer = new JdbcTemplate(leer);
    assertThat(
            aufLeer.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success"
                    + " ORDER BY installed_rank DESC LIMIT 1",
                String.class))
        .as("zuletzt erfolgreich gefahrene Migration auf der leeren Datenbank")
        .isEqualTo("36");
  }

  private static DriverManagerDataSource datenquelle(String datenbank) {
    return new DriverManagerDataSource(
        "jdbc:postgresql://"
            + POSTGRES.getHost()
            + ":"
            + POSTGRES.getFirstMappedPort()
            + "/"
            + datenbank,
        POSTGRES.getUsername(),
        POSTGRES.getPassword());
  }

  private static void migrateTo(DriverManagerDataSource dataSource, String version) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(version)
        .load()
        .migrate();
  }

  private static void seedBestand() {
    long userId =
        id(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('m@example.com', 'x', 'M') RETURNING id");
    projectId =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    Long laufId =
        jdbc.queryForObject(
            "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
                + " skipped_count, unparsed_count, created_at)"
                + " VALUES (?, ?, 'IMPLEMENTATION', 1000, 2, 0, 0, now()) RETURNING id",
            Long.class,
            projectId,
            Timestamp.from(Instant.parse("2026-09-01T22:00:00Z")));
    nightRunId = laufId == null ? 0L : laufId;
  }

  private static long id(String sql) {
    Long generated = jdbc.queryForObject(sql, Long.class);
    return generated == null ? 0L : generated;
  }
}
