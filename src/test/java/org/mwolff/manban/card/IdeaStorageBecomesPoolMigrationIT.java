package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert die Migration {@code V21__idea_storage_becomes_pool.sql}: eine Karte, die vor der
 * Migration im unsichtbaren Ideen-Speicher-Zwischenzustand steckt ({@code idea_stored = true} mit
 * gesetztem {@code board_id}), wird board-los, notiert das bisherige Board als {@code
 * target_board_id} und behält Nummer und Position unverändert. Bereits board-lose Pool-Ideen und
 * reguläre Karten bleiben unberührt. Nutzt einen eigenen Container, um bis {@code V20} zu
 * migrieren, den Bestand zu seeden und dann {@code V21} laufen zu lassen.
 */
class IdeaStorageBecomesPoolMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  static {
    POSTGRES.start();
  }

  @Test
  void freesGhostCardsFromTheirBoardAndKeepsNumberAndPosition() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "20");
    Seed seed = seedData(jdbc);
    migrateTo(dataSource, "21");

    // Die Geisterkarte ist board-los, das alte Board ist als Zielboard-Hinweis notiert, Nummer und
    // Position sind unverändert.
    assertThat(boardId(jdbc, seed.ghost)).isNull();
    assertThat(columnId(jdbc, seed.ghost)).isNull();
    assertThat(targetBoardId(jdbc, seed.ghost)).isEqualTo(seed.board);
    assertThat(number(jdbc, seed.ghost)).isEqualTo(3);
    assertThat(position(jdbc, seed.ghost)).isEqualTo(1);

    // Eine reguläre, board-gebundene Karte bleibt unangetastet.
    assertThat(boardId(jdbc, seed.regular)).isEqualTo(seed.board);
    assertThat(targetBoardId(jdbc, seed.regular)).isNull();

    // Eine bereits board-lose Pool-Idee bleibt unangetastet (kein Board zum Notieren vorhanden).
    assertThat(boardId(jdbc, seed.pooled)).isNull();
    assertThat(targetBoardId(jdbc, seed.pooled)).isNull();
  }

  /** Seedet eine Geisterkarte (idea_stored + board_id), eine reguläre Karte und eine Pool-Idee. */
  private static Seed seedData(JdbcTemplate jdbc) {
    long user =
        id(
            jdbc,
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('m@example.com', 'x', 'M') RETURNING id");
    long project =
        id(
            jdbc,
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + user + ") RETURNING id");
    long board =
        id(jdbc, "INSERT INTO board (project_id, name) VALUES (" + project + ", 'B') RETURNING id");
    long column =
        id(
            jdbc,
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + board
                + ", 'Backlog', 0) RETURNING id");

    Seed s = new Seed();
    s.board = board;
    s.regular = card(jdbc, board, column, 1, "Regulär", 0, false);
    s.ghost = card(jdbc, board, column, 3, "Geister", 1, true);
    s.pooled =
        id(
            jdbc,
            "INSERT INTO card (project_id, number, title, position_in_column, idea_stored) "
                + "VALUES ("
                + project
                + ", 2, 'Pool-Idee', 0, true) RETURNING id");
    return s;
  }

  private static long card(
      JdbcTemplate jdbc,
      long boardId,
      long columnId,
      int number,
      String title,
      int position,
      boolean ideaStored) {
    return id(
        jdbc,
        "INSERT INTO card (board_id, column_id, number, title, position_in_column, idea_stored) "
            + "VALUES ("
            + boardId
            + ", "
            + columnId
            + ", "
            + number
            + ", '"
            + title
            + "', "
            + position
            + ", "
            + ideaStored
            + ") RETURNING id");
  }

  private static Long boardId(JdbcTemplate jdbc, long cardId) {
    return jdbc.queryForObject("SELECT board_id FROM card WHERE id = " + cardId, Long.class);
  }

  private static Long columnId(JdbcTemplate jdbc, long cardId) {
    return jdbc.queryForObject("SELECT column_id FROM card WHERE id = " + cardId, Long.class);
  }

  private static Long targetBoardId(JdbcTemplate jdbc, long cardId) {
    return jdbc.queryForObject("SELECT target_board_id FROM card WHERE id = " + cardId, Long.class);
  }

  private static int number(JdbcTemplate jdbc, long cardId) {
    Integer n = jdbc.queryForObject("SELECT number FROM card WHERE id = " + cardId, Integer.class);
    return n == null ? -1 : n;
  }

  private static int position(JdbcTemplate jdbc, long cardId) {
    Integer p =
        jdbc.queryForObject(
            "SELECT position_in_column FROM card WHERE id = " + cardId, Integer.class);
    return p == null ? -1 : p;
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
    private long board;
    private long regular;
    private long ghost;
    private long pooled;
  }
}
