package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Timestamp;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V35__night_run_no_work_reason.sql} (Issue #1068) gegen einen Datenbestand, der
 * <b>vor</b> der Migration angelegt wurde.
 *
 * <p>Die Migration gibt {@code night_run} die Spalte {@code no_work_reason}. Es gibt <b>keinen</b>
 * Backfill (E1, AK 4 der fachlichen Quelle #1060): Ein bereits gespeicherter Lauf wird nicht
 * umgefaerbt, auch wenn er nichts abgearbeitet hat — gegen leere Tabellen bewiese ein gruener Lauf
 * davon nichts. Eigener Container, um gezielt bis {@code V34} zu migrieren.
 */
class NachtlaufOhneArbeitMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  /** Ein Lauf von vor {@code V35}, der <b>nichts</b> abgearbeitet hat — der Fall aus AK 4. */
  private static final Instant BESTAND_OHNE_ARBEIT = Instant.parse("2026-09-01T22:00:00Z");

  /** Ein Lauf von vor {@code V35}, der gearbeitet hat — die Gegenprobe. */
  private static final Instant BESTAND_MIT_ARBEIT = Instant.parse("2026-09-02T22:00:00Z");

  private static JdbcTemplate jdbc;
  private static long projectId;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void bestandBisV34AnlegenUndDannNachV35Migrieren() {
    DriverManagerDataSource dataSource = datenquelle(POSTGRES.getDatabaseName());
    jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "34");
    projectId = seedBestand(jdbc);
    migrateTo(dataSource, "35");
  }

  /**
   * AK 4 der fachlichen Quelle: Kein Backfill. Der Bestandslauf <b>ohne Arbeit</b> ist der Fall,
   * den eine Ableitung aus {@code processed_count == 0} rueckwirkend umgefaerbt haette (E1).
   */
  @Test
  void bestandslaeufeTragenNachDerMigrationKeinenGrund() {
    assertThat(
            jdbc.query(
                // Ausdruecklich auf die beiden Bestandszeilen eingegrenzt: Andere Tests dieser
                // Klasse legen eigene Laeufe an, und die Reihenfolge der Tests ist nicht
                // zugesichert.
                "SELECT started_at, processed_count, no_work_reason FROM night_run"
                    + " WHERE project_id = ? AND started_at IN (?, ?) ORDER BY started_at",
                (rs, row) ->
                    tuple(
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getInt("processed_count"),
                        rs.getString("no_work_reason")),
                projectId,
                Timestamp.from(BESTAND_OHNE_ARBEIT),
                Timestamp.from(BESTAND_MIT_ARBEIT)))
        .as("beide Bestandslaeufe ohne Grund, auch der mit processed_count = 0")
        .containsExactly(tuple(BESTAND_OHNE_ARBEIT, 0, null), tuple(BESTAND_MIT_ARBEIT, 2, null));
  }

  @Test
  void dieSpalteIstNullbarUndOhneVorgabewert() {
    assertThat(
            jdbc.query(
                "SELECT data_type, is_nullable, column_default, character_maximum_length"
                    + " FROM information_schema.columns"
                    + " WHERE table_name = 'night_run' AND column_name = 'no_work_reason'",
                (rs, row) ->
                    tuple(
                        rs.getString("data_type"),
                        rs.getString("is_nullable"),
                        rs.getString("column_default"),
                        rs.getInt("character_maximum_length"))))
        .as("Typ, Nullbarkeit, Vorgabewert und Laenge des Grundes")
        .containsExactly(tuple("character varying", "YES", null, 300));
  }

  /** Die Laengengrenze ist eine Zusicherung der Datenbank, nicht nur eine der Anwendung. */
  @Test
  void einGrundUeberDreihundertZeichenWirdAbgewiesen() {
    assertThatThrownBy(() -> laufMitGrund(Instant.parse("2026-09-05T22:00:00Z"), "x".repeat(301)))
        .isInstanceOf(DataIntegrityViolationException.class);

    laufMitGrund(Instant.parse("2026-09-06T22:00:00Z"), "x".repeat(300));
    assertThat(
            jdbc.queryForObject(
                "SELECT length(no_work_reason) FROM night_run WHERE started_at = ?",
                Integer.class,
                Timestamp.from(Instant.parse("2026-09-06T22:00:00Z"))))
        .as("genau an der Grenze wird angenommen")
        .isEqualTo(300);
  }

  @Test
  void flywayLaeuftAufEinerLeerenDatenbankBisV35Durch() {
    jdbc.execute("CREATE DATABASE leer_v35");
    DriverManagerDataSource leer = datenquelle("leer_v35");

    migrateTo(leer, "35");

    JdbcTemplate aufLeer = new JdbcTemplate(leer);
    assertThat(
            aufLeer.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success"
                    + " ORDER BY installed_rank DESC LIMIT 1",
                String.class))
        .as("zuletzt erfolgreich gefahrene Migration auf der leeren Datenbank")
        .isEqualTo("35");
  }

  private static void laufMitGrund(Instant startedAt, String grund) {
    jdbc.update(
        "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
            + " skipped_count, unparsed_count, created_at, no_work_reason)"
            + " VALUES (?, ?, 'IMPLEMENTATION', 1000, 0, 0, 0, now(), ?)",
        projectId,
        Timestamp.from(startedAt),
        grund);
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

  private static long seedBestand(JdbcTemplate jdbc) {
    Long userId =
        jdbc.queryForObject(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('m@example.com', 'x', 'M') RETURNING id",
            Long.class);
    Long projekt =
        jdbc.queryForObject(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id",
            Long.class,
            userId);
    jdbc.update(
        "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
            + " skipped_count, unparsed_count, created_at)"
            + " VALUES (?, ?, 'IMPLEMENTATION', 1000, 0, 0, 0, now())",
        projekt,
        Timestamp.from(BESTAND_OHNE_ARBEIT));
    jdbc.update(
        "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
            + " skipped_count, unparsed_count, created_at)"
            + " VALUES (?, ?, 'IMPLEMENTATION', 1000, 2, 0, 0, now())",
        projekt,
        Timestamp.from(BESTAND_MIT_ARBEIT));
    return projekt;
  }
}
