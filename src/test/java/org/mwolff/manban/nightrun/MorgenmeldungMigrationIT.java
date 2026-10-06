package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Verifiziert {@code V48__night_run_release_preparation.sql} (Issue #1456) gegen einen
 * Datenbestand, der <b>vor</b> der Migration angelegt wurde — nach dem Muster von {@link
 * NightRunAbbruchGrundMigrationIT}.
 *
 * <p>Die Migration ist rein additiv: zwei neue Tabellen, kein Backfill. Ein Bestandslauf trägt
 * danach keine Morgenmeldung, kann aber eine bekommen. Eigener Container, um gezielt bis {@code
 * V47} zu migrieren.
 */
class MorgenmeldungMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.15");

  private static final Instant BESTAND = Instant.parse("2026-09-01T22:00:00Z");

  private static JdbcTemplate jdbc;
  private static long bestandslauf;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void bestandBisV47AnlegenUndDannNachV48Migrieren() {
    DriverManagerDataSource dataSource = datenquelle(POSTGRES.getDatabaseName());
    jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "47");
    bestandslauf = seedBestand(jdbc);
    migrateTo(dataSource, "48");
  }

  /** Kein Backfill: Der Bestandslauf trägt nach der Migration keine Morgenmeldung. */
  @Test
  void einBestandslaufTraegtNachDerMigrationKeineMorgenmeldung() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM night_run_release_preparation WHERE night_run_id = ?",
                Long.class,
                bestandslauf))
        .isZero();
  }

  /**
   * Ein Bestandslauf kann eine Morgenmeldung bekommen, und wird er gelöscht, nimmt er sie samt
   * Einträgen mit (ON DELETE CASCADE über beide Tabellen).
   */
  @Test
  void eineMorgenmeldungAmBestandslaufFaelltMitIhmWeg() {
    long lauf = lauf(Instant.parse("2026-09-03T22:00:00Z"));
    vorbereitung(lauf, "GREEN");
    jdbc.update(
        "INSERT INTO night_run_release_entry (night_run_id, kind, position, card_number)"
            + " VALUES (?, 'CARD', 0, 1449)",
        lauf);

    jdbc.update("DELETE FROM night_run WHERE id = ?", lauf);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM night_run_release_entry WHERE night_run_id = ?",
                Long.class,
                lauf))
        .isZero();
  }

  /** Der Wertebereich von {@code result} ist eine Zusicherung der Datenbank. */
  @Test
  void einUnbekanntesErgebnisWirdAbgewiesen() {
    long lauf = lauf(Instant.parse("2026-09-04T22:00:00Z"));

    assertThatThrownBy(() -> vorbereitung(lauf, "BLUE"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** Eine Kartenzeile trägt eine Nummer und keinen Text, eine offene Zeile umgekehrt. */
  @Test
  void dieFormEinesEintragsIstEineZusicherungDerDatenbank() {
    long lauf = lauf(Instant.parse("2026-09-05T22:00:00Z"));
    vorbereitung(lauf, "GREEN_PENDING");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO night_run_release_entry (night_run_id, kind, position, text)"
                        + " VALUES (?, 'CARD', 0, 'kein Kartenwert')",
                    lauf))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO night_run_release_entry (night_run_id, kind, position,"
                        + " card_number) VALUES (?, 'PENDING', 0, 1449)",
                    lauf))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void flywayLaeuftAufEinerLeerenDatenbankBisV48Durch() {
    jdbc.execute("CREATE DATABASE leer_v48");
    DriverManagerDataSource leer = datenquelle("leer_v48");

    migrateTo(leer, "48");

    assertThat(
            new JdbcTemplate(leer)
                .queryForObject(
                    "SELECT version FROM flyway_schema_history WHERE success"
                        + " ORDER BY installed_rank DESC LIMIT 1",
                    String.class))
        .as("zuletzt erfolgreich gefahrene Migration auf der leeren Datenbank")
        .isEqualTo("48");
  }

  private static void vorbereitung(long lauf, String ergebnis) {
    jdbc.update(
        "INSERT INTO night_run_release_preparation (night_run_id, result, received_at)"
            + " VALUES (?, ?, now())",
        lauf,
        ergebnis);
  }

  private static long lauf(Instant startedAt) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
                + " skipped_count, unparsed_count, created_at)"
                + " SELECT project_id, ?, 'CHAIN', 1000, 0, 0, 0, now() FROM night_run"
                + " WHERE id = ? RETURNING id",
            Long.class,
            Timestamp.from(startedAt),
            bestandslauf);
    return id == null ? 0L : id;
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
    Long lauf =
        jdbc.queryForObject(
            "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
                + " skipped_count, unparsed_count, created_at, complete)"
                + " VALUES (?, ?, 'CHAIN', 1000, 1, 0, 0, now(), true) RETURNING id",
            Long.class,
            projekt,
            Timestamp.from(BESTAND));
    return lauf == null ? 0L : lauf;
  }
}
