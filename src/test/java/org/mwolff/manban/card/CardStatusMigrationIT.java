package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V46__card_status.sql} (Issue #1298, Plan #1294 E19): Die Spalte {@code status}
 * entsteht samt Check-Bedingung, und der Backfill füllt sie in einem Zug — Arbeitspakete bekommen
 * den kanonischen Status ihrer Prozessspalte, in eigenen Spalten {@code BACKLOG}; Dokumentarten und
 * Vorhaben bleiben {@code NULL}.
 *
 * <p>Vorher/Nachher-Aufbau: erst bis {@code V45} migrieren, Bestand säen, dann {@code V46} anwenden
 * — der Backfill ist nur an Daten zu prüfen, die vor ihm da waren.
 */
class CardStatusMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.15");

  private static JdbcTemplate jdbc;
  private static final Map<String, Long> KARTEN = new HashMap<>();

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void bestandBisV45AnlegenUndDannNachV46Migrieren() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "45");
    seedBestand();
    migrateTo(dataSource, "46");
  }

  @Test
  void arbeitspaketInProzessspalteTraegtDerenKanonischenStatus() {
    assertThat(status("backlog")).isEqualTo("BACKLOG");
    assertThat(status("ready")).isEqualTo("READY");
    assertThat(status("inProgress")).isEqualTo("IN_PROGRESS");
    assertThat(status("inReview")).isEqualTo("IN_REVIEW");
    assertThat(status("done")).isEqualTo("DONE");
  }

  @Test
  void arbeitspaketInEigenerSpalteTraegtBacklog() {
    assertThat(status("anstehend")).isEqualTo("BACKLOG");
    assertThat(status("doneArchiv"))
        .as("„Done (Archiv)“ ist keine Prozessspalte der kanonischen Regel")
        .isEqualTo("BACKLOG");
  }

  @Test
  void menschUndTaskGeltenAlsArbeitspaket() {
    assertThat(status("mensch")).isEqualTo("READY");
    assertThat(status("task")).isEqualTo("IN_REVIEW");
  }

  @Test
  void dokumentartenUndVorhabenBleibenOhneStatus() {
    assertThat(status("idee")).isNull();
    assertThat(status("fachlich")).isNull();
    assertThat(status("plan")).isNull();
    assertThat(status("planKlein")).isNull();
    assertThat(status("vorhaben")).isNull();
  }

  /**
   * Beleg für die Regex aus E19: {@code \s*} fängt auch den Tabulator, {@code btrim} täte es nicht.
   */
  @Test
  void titelMitFuehrendemTabulatorVorPlanBleibtOhneStatus() {
    assertThat(status("planTab")).isNull();
  }

  @Test
  void spalteIstNullbar() {
    assertThat(
            jdbc.queryForObject(
                "SELECT is_nullable FROM information_schema.columns"
                    + " WHERE table_schema = current_schema()"
                    + " AND table_name = 'card' AND column_name = 'status'",
                String.class))
        .isEqualTo("YES");
  }

  @Test
  void checkBedingungWeistUnbekanntenStatusAb() {
    long id = KARTEN.get("backlog");
    assertThatThrownBy(() -> jdbc.update("UPDATE card SET status = 'WARTEND' WHERE id = ?", id))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("chk_card_status");
  }

  private static void seedBestand() {
    long user =
        id(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('m@example.com', 'x', 'M') RETURNING id");
    long project =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", user);
    long board = id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", project);

    long backlog = spalte(board, "Backlog", 0);
    long ready = spalte(board, "Ready", 1);
    long inProgress = spalte(board, "In Progress", 2);
    long inReview = spalte(board, "in-review", 3);
    long done = spalte(board, "  DONE  ", 4);
    long anstehend = spalte(board, "Anstehend", 5);
    long doneArchiv = spalte(board, "Done (Archiv)", 6);

    Seed seed = new Seed(project, board);
    seed.karte("backlog", backlog, "Paket im Backlog", "CARD");
    seed.karte("ready", ready, "Paket in Ready", "CARD");
    seed.karte("inProgress", inProgress, "Paket in Arbeit", "CARD");
    seed.karte("inReview", inReview, "Paket im Review", "CARD");
    seed.karte("done", done, "Paket erledigt", "CARD");
    seed.karte("anstehend", anstehend, "Paket in eigener Spalte", "CARD");
    seed.karte("doneArchiv", doneArchiv, "Paket im Archiv", "CARD");
    seed.karte("mensch", ready, "[Mensch] Zugang anlegen", "CARD");
    seed.karte("task", inReview, "[Task] Kleinigkeit", "CARD");
    seed.karte("idee", backlog, "[Idee] roh", "CARD");
    seed.karte("fachlich", ready, "[Fachlich] Story", "CARD");
    seed.karte("plan", inProgress, "[Plan] Weg", "CARD");
    seed.karte("planKlein", done, "  [plan] klein", "CARD");
    seed.karte("planTab", anstehend, "\t[Plan] mit Tabulator", "CARD");
    seed.karte("vorhaben", backlog, "Vorhaben ohne Präfix", "EPIC");
  }

  /** Legt Karten mit fortlaufender Nummer und Position an. */
  private static final class Seed {
    private final long project;
    private final long board;
    private int number;

    Seed(long project, long board) {
      this.project = project;
      this.board = board;
    }

    void karte(String schluessel, long column, String title, String type) {
      number++;
      KARTEN.put(
          schluessel,
          id(
              "INSERT INTO card (project_id, board_id, column_id, number, title,"
                  + " position_in_column, type) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
              project,
              board,
              column,
              number,
              title,
              number,
              type));
    }
  }

  private static long spalte(long board, String name, int position) {
    return id(
        "INSERT INTO board_column (board_id, name, position) VALUES (?, ?, ?) RETURNING id",
        board,
        name,
        position);
  }

  private static String status(String schluessel) {
    return jdbc.queryForObject(
        "SELECT status FROM card WHERE id = ?", String.class, KARTEN.get(schluessel));
  }

  private static void migrateTo(DriverManagerDataSource dataSource, String version) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(version)
        .load()
        .migrate();
  }

  private static long id(String sql, Object... args) {
    Long generated = jdbc.queryForObject(sql, Long.class, args);
    return generated == null ? 0L : generated;
  }
}
