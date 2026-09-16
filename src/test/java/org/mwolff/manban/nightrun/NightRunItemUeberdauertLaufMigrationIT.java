package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Timestamp;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert den Backfill von {@code V33__night_run_item_ueberdauert_lauf.sql} (Issue #964) gegen
 * einen Datenstand, der <b>vor</b> der Migration angelegt wurde.
 *
 * <p>Die Migration fuellt {@code project_id}, {@code started_at} und {@code mode} jedes
 * Bestandspakets aus seinem Lauf und setzt danach {@code NOT NULL}. Scheitert der Backfill auch nur
 * an einer Zeile, bricht die {@code NOT NULL}-Setzung ab — gegen eine leere Tabelle bewiese ein
 * gruener Lauf davon nichts. Eigener Container, um gezielt bis {@code V32} zu migrieren.
 */
class NightRunItemUeberdauertLaufMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  private static final Instant START_A = Instant.parse("2026-09-01T22:00:00Z");
  private static final Instant START_B = Instant.parse("2026-09-02T22:00:00Z");

  static {
    POSTGRES.start();
  }

  @Test
  void backfillFuelltAlleDreiFelderAusDemLaufUndSetztNotNull() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "32");
    long projectId = seedBestand(jdbc);
    migrateTo(dataSource, "33");

    assertThat(
            jdbc.query(
                "SELECT card_number, project_id, started_at, mode FROM night_run_item"
                    + " ORDER BY card_number",
                (rs, row) ->
                    tuple(
                        rs.getInt("card_number"),
                        rs.getLong("project_id"),
                        rs.getTimestamp("started_at"),
                        rs.getString("mode"))))
        .containsExactly(
            tuple(1, projectId, Timestamp.from(START_A), "IMPLEMENTATION"),
            tuple(2, projectId, Timestamp.from(START_A), "IMPLEMENTATION"),
            tuple(3, projectId, Timestamp.from(START_B), "CHAIN"));

    assertThat(
            jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns"
                    + " WHERE table_name = 'night_run_item' AND is_nullable = 'NO'"
                    + " AND column_name IN ('project_id', 'started_at', 'mode', 'night_run_id')",
                String.class))
        .containsExactlyInAnyOrder("project_id", "started_at", "mode");
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
    long userId =
        id(
            jdbc,
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('m@example.com', 'x', 'M') RETURNING id");
    long projectId =
        id(
            jdbc,
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    long laufA = lauf(jdbc, projectId, START_A, "IMPLEMENTATION");
    long laufB = lauf(jdbc, projectId, START_B, "CHAIN");
    paket(jdbc, laufA, 1);
    paket(jdbc, laufA, 2);
    paket(jdbc, laufB, 3);
    return projectId;
  }

  private static long lauf(JdbcTemplate jdbc, long projectId, Instant start, String mode) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
                + " skipped_count, unparsed_count, created_at)"
                + " VALUES (?, ?, ?, 1000, 1, 0, 0, now()) RETURNING id",
            Long.class,
            projectId,
            Timestamp.from(start),
            mode);
    return id == null ? 0L : id;
  }

  private static void paket(JdbcTemplate jdbc, long runId, int cardNumber) {
    jdbc.update(
        "INSERT INTO night_run_item (night_run_id, card_number, title, state)"
            + " VALUES (?, ?, 'Bestand', 'GREEN')",
        runId,
        cardNumber);
  }

  private static long id(JdbcTemplate jdbc, String sql) {
    Long generated = jdbc.queryForObject(sql, Long.class);
    return generated == null ? 0L : generated;
  }
}
