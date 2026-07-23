package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert die Migration {@code V19__card_number_project_scoped.sql}: bestehende, board-lokal
 * kollidierende Kartennummern werden minimal-invasiv auf projektweite Eindeutigkeit umnummeriert;
 * der Behalter je Kollision (zuerst erstelltes Board, Tiebreak kleinste id) behält seine Nummer,
 * strukturierte Abhängigkeits-Verweise werden mitgezogen, Nicht-Kollisionen bleiben unverändert.
 * Nutzt einen eigenen Container, um bis {@code V18} zu migrieren, Kollisionen zu seeden und dann
 * {@code V19} laufen zu lassen.
 */
class CardNumberProjectScopedMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  static {
    POSTGRES.start();
  }

  @Test
  void renumbersOnlyCollisionsAndFixesDependencies() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "18");
    Seed seed = seedCollidingData(jdbc);
    migrateTo(dataSource, "19");

    // Projekt P1: Behalter (Board 1, zuerst erstellt) behalten ihre Nummern; die Karten von Board 2
    // sind projektweit weitergezählt (max war 2 -> 3, dann 4, geordnet nach alter Nummer).
    assertThat(number(jdbc, seed.a1)).isEqualTo(1);
    assertThat(number(jdbc, seed.a2)).isEqualTo(2);
    assertThat(number(jdbc, seed.b1)).isEqualTo(3);
    assertThat(number(jdbc, seed.b2)).isEqualTo(4);
    // Nicht-Kollision im Einzelboard-Projekt P2 bleibt unverändert.
    assertThat(number(jdbc, seed.p2card)).isEqualTo(1);

    // Abhängigkeits-Verweise: A2 -> #1 zeigt auf A1 (Behalter, unverändert) -> bleibt 1.
    assertThat(dependency(jdbc, seed.a2)).isEqualTo(1);
    // b2 -> #1 zeigte board-lokal auf b1 (Board 2), b1 wurde 1 -> 3 umnummeriert -> Verweis wird 3.
    assertThat(dependency(jdbc, seed.b2)).isEqualTo(3);

    // Der neue Constraint ist projektweit: zwei Karten desselben Projekts mit gleicher Nummer
    // (hier über zwei Boards) sind jetzt unmöglich.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE card SET number = 2 WHERE id = " + seed.b2)) // b2 (4) auf 2 = wie a2
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** Migriert P1 mit zwei Boards (kollidierende #1/#2) + P2 mit einem Board (#1). */
  private static Seed seedCollidingData(JdbcTemplate jdbc) {
    long user =
        id(
            jdbc,
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('n@example.com', 'x', 'N') RETURNING id");
    long p1 =
        id(
            jdbc,
            "INSERT INTO project (name, owner_user_id) VALUES ('P1', " + user + ") RETURNING id");
    long p2 =
        id(
            jdbc,
            "INSERT INTO project (name, owner_user_id) VALUES ('P2', " + user + ") RETURNING id");

    long board1 =
        id(jdbc, "INSERT INTO board (project_id, name) VALUES (" + p1 + ", 'B1') RETURNING id");
    long col1 =
        id(
            jdbc,
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + board1
                + ", 'Backlog', 0) RETURNING id");
    long board2 =
        id(jdbc, "INSERT INTO board (project_id, name) VALUES (" + p1 + ", 'B2') RETURNING id");
    long col2 =
        id(
            jdbc,
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + board2
                + ", 'Backlog', 0) RETURNING id");
    long board3 =
        id(jdbc, "INSERT INTO board (project_id, name) VALUES (" + p2 + ", 'B3') RETURNING id");
    long col3 =
        id(
            jdbc,
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + board3
                + ", 'Backlog', 0) RETURNING id");

    Seed s = new Seed();
    // project_id wird vom V18-Trigger aus board_id gefüllt.
    s.a1 = card(jdbc, board1, col1, 1, "A1", 0);
    s.a2 = card(jdbc, board1, col1, 2, "A2", 1);
    s.b1 = card(jdbc, board2, col2, 1, "B1card", 0);
    s.b2 = card(jdbc, board2, col2, 2, "B2card", 1);
    s.p2card = card(jdbc, board3, col3, 1, "P2card", 0);

    // A2 hängt (board-lokal auf B1) von #1 = A1 ab; B2card hängt (board-lokal auf B2) von #1 =
    // B1card ab.
    jdbc.update(
        "INSERT INTO card_dependency (card_id, depends_on_card_number) VALUES (" + s.a2 + ", 1)");
    jdbc.update(
        "INSERT INTO card_dependency (card_id, depends_on_card_number) VALUES (" + s.b2 + ", 1)");
    return s;
  }

  private static long card(
      JdbcTemplate jdbc, long boardId, long columnId, int number, String title, int position) {
    return id(
        jdbc,
        "INSERT INTO card (board_id, column_id, number, title, position_in_column) VALUES ("
            + boardId
            + ", "
            + columnId
            + ", "
            + number
            + ", '"
            + title
            + "', "
            + position
            + ") RETURNING id");
  }

  private static int number(JdbcTemplate jdbc, long cardId) {
    Integer n = jdbc.queryForObject("SELECT number FROM card WHERE id = " + cardId, Integer.class);
    return n == null ? -1 : n;
  }

  private static int dependency(JdbcTemplate jdbc, long cardId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT depends_on_card_number FROM card_dependency WHERE card_id = " + cardId,
            Integer.class);
    return n == null ? -1 : n;
  }

  private static void migrateTo(DriverManagerDataSource dataSource, String version) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(version)
        .load()
        .migrate();
  }

  private static long id(JdbcTemplate jdbc, String sql) {
    Long generated = jdbc.queryForObject(sql, Long.class);
    return generated == null ? 0L : generated;
  }

  private static final class Seed {
    private long a1;
    private long a2;
    private long b1;
    private long b2;
    private long p2card;
  }
}
