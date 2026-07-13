package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert den Backfill der Migration {@code V9__card_column_transition.sql}: bestehende Karten
 * (vor Einführung des Trackings) erhalten genau dann eine offene Transition-Zeile, wenn sie aktiv
 * und keine Epics sind. Nutzt einen eigenen Container, um gezielt bis {@code V8} zu migrieren,
 * Bestandsdaten zu seeden und dann {@code V9} laufen zu lassen.
 */
class CardColumnTransitionBackfillIT {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  static {
    POSTGRES.start();
  }

  @Test
  void backfillCreatesOneOpenRowPerActiveNonEpicCard() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "8");
    seedPreTrackingData(jdbc);
    migrateTo(dataSource, "9");

    Long total = jdbc.queryForObject("SELECT count(*) FROM card_column_transition", Long.class);
    Long open =
        jdbc.queryForObject(
            "SELECT count(*) FROM card_column_transition WHERE left_at IS NULL", Long.class);
    String columnName =
        jdbc.queryForObject(
            "SELECT column_name FROM card_column_transition ORDER BY id LIMIT 1", String.class);

    // Zwei aktive Nicht-Epic-Karten -> zwei offene Zeilen; archivierte Karte und Epic zählen nicht.
    assertThat(total).isEqualTo(2L);
    assertThat(open).isEqualTo(2L);
    assertThat(columnName).isEqualTo("Ready");
  }

  private static void migrateTo(DriverManagerDataSource dataSource, String version) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(version)
        .load()
        .migrate();
  }

  private static void seedPreTrackingData(JdbcTemplate jdbc) {
    long userId =
        id(
            jdbc,
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('b@example.com', 'x', 'B') RETURNING id");
    long projectId =
        id(
            jdbc,
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    long boardId =
        id(
            jdbc,
            "INSERT INTO board (project_id, name) VALUES (" + projectId + ", 'B') RETURNING id");
    long ready =
        id(
            jdbc,
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + boardId
                + ", 'Ready', 0) RETURNING id");
    long done =
        id(
            jdbc,
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + boardId
                + ", 'Done', 1) RETURNING id");

    // Zwei aktive Nicht-Epic-Karten (werden gebackfillt).
    jdbc.update(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column) "
            + "VALUES ("
            + boardId
            + ", "
            + ready
            + ", 1, 'Active-A', 0)");
    jdbc.update(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column) "
            + "VALUES ("
            + boardId
            + ", "
            + done
            + ", 2, 'Active-B', 0)");
    // Archivierte Karte (kein Backfill).
    jdbc.update(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column, archived) "
            + "VALUES ("
            + boardId
            + ", "
            + ready
            + ", 3, 'Archived', 1, true)");
    // Epic (kein Backfill).
    jdbc.update(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column, type) "
            + "VALUES ("
            + boardId
            + ", "
            + ready
            + ", 4, 'Epic', 2, 'EPIC')");
  }

  private static long id(JdbcTemplate jdbc, String sql) {
    Long generated = jdbc.queryForObject(sql, Long.class);
    return generated == null ? 0L : generated;
  }
}
