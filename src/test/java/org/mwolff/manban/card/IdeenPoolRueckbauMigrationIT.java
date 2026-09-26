package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V44__ideen_pool_rueckbau.sql} (Issue #1205): Der board-lose Kartenzustand wird
 * auf Datenbankebene wieder ausgeschlossen, nachdem die Anwendung den Pool nicht mehr kennt
 * (#1204). Tritt an die Stelle von {@code IdeaStorageBecomesPoolMigrationIT}, die den umgekehrten
 * Übergang nach {@code V21} geprüft hat — und damit einen Zustand, den {@code V44} ausschließt.
 *
 * <p>Geprüft werden vier Fälle (Plan-Entscheidungen E5, E6, E17):
 *
 * <ol>
 *   <li>die Form des Schemas nach {@code V44} — Spalten fort, Nullbarkeit zurückgenommen, Prüf- und
 *       Unique-Bedingungen im erwarteten Zustand,
 *   <li>der Bestandsnachweis (AK 13): Karten, Nummern, Spaltenzuordnung, {@code card_activity} und
 *       die Nachtlauf-Protokolle überleben die Migration unverändert,
 *   <li>der Riegel aus E5 bei einer board-losen Karte,
 *   <li>der Riegel aus E5 bei einer als Idee gekennzeichneten Karte.
 * </ol>
 *
 * <p>Die beiden Riegel-Fälle laufen je in einer eigenen Datenbank: Eine gescheiterte Migration
 * hinterlässt einen Fehlschlag in {@code flyway_schema_history}, und der träfe jeden weiteren Lauf
 * derselben Datenbank.
 */
class IdeenPoolRueckbauMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  /** Der Kern der Abbruchmeldung aus E5 — kurz genug, um Formulierungen offen zu lassen. */
  private static final String RIEGEL_MELDUNG = "board-los oder als Idee gekennzeichnet";

  /** Ein Nachtlauf-Protokoll aus dem Bestand: Es darf die Migration unverändert überleben. */
  private static final Instant BESTAND_NACHTLAUF = Instant.parse("2026-09-01T22:00:00Z");

  private static JdbcTemplate jdbc;
  private static long projectId;
  private static List<Tuple> kartenVorher;
  private static List<Tuple> aktivitaetenVorher;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void bestandBisV43AnlegenUndDannNachV44Migrieren() {
    DriverManagerDataSource dataSource = datenquelle(POSTGRES.getDatabaseName());
    jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "43");
    projectId = seedBestand(jdbc);
    kartenVorher = karten(jdbc);
    aktivitaetenVorher = aktivitaeten(jdbc);
    migrateTo(dataSource, "44");
  }

  /** E6: Die Pool-Spalten sind fort, die Board-Bindung ist wieder Pflicht. */
  @Test
  void nachDerMigrationTraegtDieKarteKeinePoolSpaltenMehrUndIstBoardGebunden() {
    assertThat(spalten("idea_stored", "target_board_id"))
        .as("die beiden Pool-Spalten sind fort")
        .isEmpty();

    assertThat(nullbarkeit("board_id", "column_id", "number"))
        .as("Board, Spalte und Nummer sind wieder Pflicht")
        .containsExactly(tuple("board_id", "NO"), tuple("column_id", "NO"), tuple("number", "NO"));

    assertThat(bedingung("ck_card_board_consistency"))
        .as("die Konsistenzbedingung aus V18 hat ohne board-lose Karten keinen Gegenstand mehr")
        .isNull();

    assertThat(bedingung("uq_card_active_position"))
        .as("die Unique-Bedingung auf der neu erzeugten generierten Spalte steht wieder")
        .isEqualTo("u");

    String erzeugung = generierungsAusdruck("active_position");
    assertThat(erzeugung).as("kein idea_stored-Term mehr").doesNotContain("idea_stored");
    assertThat(erzeugung)
        .as("die übrigen Terme aus V15 und V16 bleiben erhalten")
        .contains("archived")
        .contains("EPIC")
        .contains("deleted_at")
        .contains("position_in_column");
  }

  /**
   * AK 13 und E17: Die Migration ändert keine Bestandsdaten. Sie fasst {@code card_activity} nicht
   * an — auch die Zeilen {@code IDEA_STORED} und {@code PROMOTED} stehen danach noch da.
   */
  @Test
  void bestandsdatenUeberlebenDieMigrationUnveraendert() {
    assertThat(karten(jdbc))
        .as("Kartenzahl, Nummern, Spaltenzuordnung und Position unverändert")
        .isEqualTo(kartenVorher);

    assertThat(aktivitaeten(jdbc)).as("card_activity unangetastet").isEqualTo(aktivitaetenVorher);

    assertThat(jdbc.queryForList("SELECT type FROM card_activity ORDER BY id", String.class))
        .as("auch die Zeilen aus der Pool-Zeit stehen noch da")
        .contains("IDEA_STORED", "PROMOTED");

    assertThat(
            jdbc.query(
                "SELECT started_at, processed_count FROM night_run WHERE project_id = ?"
                    + " ORDER BY started_at",
                (rs, row) ->
                    tuple(rs.getTimestamp("started_at").toInstant(), rs.getInt("processed_count")),
                projectId))
        .as("die Nachtlauf-Protokolle bleiben unverändert")
        .containsExactly(tuple(BESTAND_NACHTLAUF, 2));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM board", Integer.class))
        .as("die Boards bleiben unverändert")
        .isEqualTo(1);
  }

  /** E5: Eine board-lose Karte bringt die Migration zum Abbruch — ohne die Daten anzutasten. */
  @Test
  void eineBoardLoseKarteLaesstDieMigrationScheiternUndBleibtUnveraendert() {
    Riegel riegel =
        riegelVorbereiten(
            "riegel_boardlos",
            (riegelJdbc, seed) ->
                riegelJdbc.update(
                    "INSERT INTO card (project_id, title, position_in_column)"
                        + " VALUES (?, 'Board-lose Idee', 0)",
                    seed));

    assertThatThrownBy(() -> migrateTo(riegel.dataSource, "44"))
        .as("der Riegel aus E5 löst aus")
        .isInstanceOf(FlywayException.class)
        .hasStackTraceContaining(RIEGEL_MELDUNG);

    assertThat(
            riegel.jdbc.query(
                "SELECT board_id, column_id, number, idea_stored FROM card"
                    + " WHERE title = 'Board-lose Idee'",
                (rs, row) ->
                    tuple(
                        rs.getObject("board_id"),
                        rs.getObject("column_id"),
                        rs.getObject("number"),
                        rs.getBoolean("idea_stored"))))
        .as("die board-lose Karte steht unverändert da — nichts auf ein Board verschoben")
        .containsExactly(tuple(null, null, null, false));

    assertThat(riegel.jdbc.queryForObject("SELECT count(*) FROM card", Integer.class))
        .as("nichts gelöscht — die beiden Bestandskarten stehen ebenso noch da")
        .isEqualTo(3);
  }

  /**
   * E5, zweiter Zweig: Auch eine board-gebundene Karte mit Ideen-Kennzeichen hält die Migration.
   */
  @Test
  void eineAlsIdeeGekennzeichneteKarteLaesstDieMigrationEbensoScheitern() {
    Riegel riegel =
        riegelVorbereiten(
            "riegel_idee",
            (riegelJdbc, seed) ->
                riegelJdbc.update("UPDATE card SET idea_stored = true WHERE number = 1"));

    assertThatThrownBy(() -> migrateTo(riegel.dataSource, "44"))
        .as("der Riegel aus E5 löst auch hier aus")
        .isInstanceOf(FlywayException.class)
        .hasStackTraceContaining(RIEGEL_MELDUNG);

    assertThat(
            riegel.jdbc.query(
                "SELECT number, idea_stored FROM card ORDER BY number",
                (rs, row) -> tuple(rs.getInt("number"), rs.getBoolean("idea_stored"))))
        .as("die Kennzeichnung bleibt stehen, die Migration hat nichts bereinigt")
        .containsExactly(tuple(1, true), tuple(2, false));
  }

  /** Legt eine frische Datenbank an, migriert bis {@code V43} und seedet den Riegel-Fall hinein. */
  private static Riegel riegelVorbereiten(String datenbank, RiegelSeed seed) {
    jdbc.execute("CREATE DATABASE " + datenbank);
    DriverManagerDataSource dataSource = datenquelle(datenbank);
    JdbcTemplate riegelJdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "43");
    seed.seed(riegelJdbc, seedBestand(riegelJdbc));

    return new Riegel(dataSource, riegelJdbc);
  }

  /**
   * Seedet einen Bestand, wie er in einer gewachsenen Datenbank steht: ein Projekt mit Board und
   * Spalte, zwei board-gebundene Karten, Aktivitätszeilen einschließlich der beiden Pool-Typen und
   * ein Nachtlauf-Protokoll. Gibt die Projekt-Id zurück.
   */
  private static long seedBestand(JdbcTemplate jdbc) {
    long user =
        id(
            jdbc,
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('m@example.com', 'x', 'M') RETURNING id");
    long project =
        id(jdbc, "INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", user);
    long board =
        id(jdbc, "INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", project);
    long column =
        id(
            jdbc,
            "INSERT INTO board_column (board_id, name, position) VALUES (?, 'Backlog', 0)"
                + " RETURNING id",
            board);

    long erste = karte(jdbc, board, column, 1, "Erste", 0);
    karte(jdbc, board, column, 2, "Zweite", 1);

    // Zwei Aktivitätstypen aus der Pool-Zeit: Sie bleiben als Historie stehen (E17).
    aktivitaet(jdbc, erste, "CREATED", "angelegt");
    aktivitaet(jdbc, erste, "IDEA_STORED", "in den Ideen-Speicher gelegt");
    aktivitaet(jdbc, erste, "PROMOTED", "aus dem Pool eingeplant");

    jdbc.update(
        "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
            + " skipped_count, unparsed_count, created_at, complete)"
            + " VALUES (?, ?, 'IMPLEMENTATION', 1000, 2, 0, 0, now(), true)",
        project,
        Timestamp.from(BESTAND_NACHTLAUF));

    return project;
  }

  private static long karte(
      JdbcTemplate jdbc, long board, long column, int number, String title, int position) {
    return id(
        jdbc,
        "INSERT INTO card (board_id, column_id, number, title, position_in_column)"
            + " VALUES (?, ?, ?, ?, ?) RETURNING id",
        board,
        column,
        number,
        title,
        position);
  }

  private static void aktivitaet(JdbcTemplate jdbc, long card, String type, String detail) {
    jdbc.update(
        "INSERT INTO card_activity (card_id, type, detail, created_at) VALUES (?, ?, ?, now())",
        card,
        type,
        detail);
  }

  private static List<Tuple> karten(JdbcTemplate jdbc) {
    return jdbc.query(
        "SELECT number, column_id, title, position_in_column FROM card ORDER BY number",
        (rs, row) ->
            tuple(
                rs.getInt("number"),
                rs.getLong("column_id"),
                rs.getString("title"),
                rs.getInt("position_in_column")));
  }

  private static List<Tuple> aktivitaeten(JdbcTemplate jdbc) {
    return jdbc.query(
        "SELECT type, detail FROM card_activity ORDER BY id",
        (rs, row) -> tuple(rs.getString("type"), rs.getString("detail")));
  }

  /** Die noch vorhandenen unter den genannten Spalten von {@code card}. */
  private static List<String> spalten(String... namen) {
    return jdbc.queryForList(
        "SELECT column_name FROM information_schema.columns"
            + " WHERE table_name = 'card' AND column_name = ANY (string_to_array(?, ','))",
        String.class,
        String.join(",", namen));
  }

  /** Nullbarkeit der genannten Spalten von {@code card}, in der Reihenfolge der Argumente. */
  private static List<Tuple> nullbarkeit(String... namen) {
    String liste = String.join(",", namen);
    return jdbc.query(
        "SELECT column_name, is_nullable FROM information_schema.columns"
            + " WHERE table_name = 'card' AND column_name = ANY (string_to_array(?, ','))"
            + " ORDER BY array_position(string_to_array(?, ','), column_name)",
        (rs, row) -> tuple(rs.getString("column_name"), rs.getString("is_nullable")),
        liste,
        liste);
  }

  /**
   * Der {@code contype} der Bedingung auf {@code card}, oder {@code null}, wenn es sie nicht gibt.
   */
  private static String bedingung(String name) {
    List<String> gefunden =
        jdbc.queryForList(
            "SELECT contype::text FROM pg_constraint WHERE conrelid = 'card'::regclass"
                + " AND conname = ?",
            String.class,
            name);
    return gefunden.isEmpty() ? null : gefunden.get(0);
  }

  private static String generierungsAusdruck(String spalte) {
    return jdbc.queryForObject(
        "SELECT generation_expression FROM information_schema.columns"
            + " WHERE table_name = 'card' AND column_name = ?",
        String.class,
        spalte);
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

  private static long id(JdbcTemplate jdbc, String sql, Object... args) {
    Long generated = jdbc.queryForObject(sql, Long.class, args);
    return generated == null ? 0L : generated;
  }

  /** Eine eigene Datenbank für einen Riegel-Fall, bis {@code V43} migriert und geseedet. */
  private record Riegel(DriverManagerDataSource dataSource, JdbcTemplate jdbc) {}

  @FunctionalInterface
  private interface RiegelSeed {
    void seed(JdbcTemplate jdbc, long projectId);
  }
}
