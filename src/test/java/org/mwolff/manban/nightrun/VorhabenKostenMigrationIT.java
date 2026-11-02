package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V68__vorhaben_kosten.sql} (Issue #2004, Plan #1990 E8 bis E10) gegen einen
 * Datenbestand, der <b>vor</b> der Migration angelegt wurde.
 *
 * <p>Die Migration legt {@code vorhaben_kosten} (eine Zeile je Vorhaben) und {@code
 * vorhaben_kosten_einfuehrung} (eine Zeile je Projekt) an. Bestehende Vorhaben bekommen keine Zeile
 * — die schreibt erst der Job. Eigener Container, um gezielt bis {@code V67} zu migrieren, bevor
 * der Bestand entsteht.
 */
class VorhabenKostenMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.15");

  private static JdbcTemplate jdbc;
  private static long projectId;
  private static long epicId;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void bestandBisV67AnlegenUndDannNachV68Migrieren() {
    DriverManagerDataSource dataSource = datenquelle(POSTGRES.getDatabaseName());
    jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "67");
    seedBestand();
    migrateTo(dataSource, "68");
  }

  @Test
  void bestehendeVorhabenHabenNochKeineZeile() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM vorhaben_kosten", Integer.class)).isZero();
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM vorhaben_kosten_einfuehrung", Integer.class))
        .isZero();
  }

  @Test
  void eineZeileNimmtVoraussagenAbschlussUndKostenkartenAuf() {
    long eigenes =
        id(
            "INSERT INTO card (board_id, column_id, number, title, position_in_column, type)"
                + " SELECT board_id, column_id, 2, 'Import', 1, 'EPIC' FROM card WHERE id = "
                + epicId
                + " RETURNING id");
    jdbc.update(
        "INSERT INTO vorhaben_kosten (epic_id, project_id, anforderung_stand, anforderung_art,"
            + " anforderung_typisch_usd, anforderung_festgehalten_at, pakete_stand,"
            + " pakete_festgehalten_at, abgeschlossen_at, tatsaechlich_usd, kosten_karten)"
            + " VALUES (?, ?, 'BEZIFFERT', 'ANFORDERUNG', 16.5, now(), 'KEINE', now(), now(),"
            + " 18.123456, '{1,2}')",
        eigenes,
        projectId);

    assertThat(
            jdbc.queryForObject(
                "SELECT tatsaechlich_unvollstaendig FROM vorhaben_kosten WHERE epic_id = ?",
                Boolean.class,
                eigenes))
        .as("Vorgabe: vollständig")
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT array_length(kosten_karten, 1) FROM vorhaben_kosten WHERE epic_id = ?",
                Integer.class,
                eigenes))
        .isEqualTo(2);

    jdbc.update("DELETE FROM card WHERE id = ?", eigenes);

    assertThat(jdbc.queryForObject("SELECT count(*) FROM vorhaben_kosten", Integer.class))
        .as("mit der Vorhaben-Karte entfällt die Zeile")
        .isZero();
  }

  @Test
  void einUnbekannterStandWirdAbgewiesen() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO vorhaben_kosten (epic_id, project_id, pakete_stand)"
                        + " VALUES (?, ?, 'VIELLEICHT')",
                    epicId,
                    projectId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void dieEinfuehrungIstJeProjektEinmalig() {
    jdbc.update(
        "INSERT INTO vorhaben_kosten_einfuehrung (project_id, eingefuehrt_at) VALUES (?, now())",
        projectId);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO vorhaben_kosten_einfuehrung (project_id, eingefuehrt_at)"
                        + " VALUES (?, now())",
                    projectId))
        .isInstanceOf(DataIntegrityViolationException.class);
    jdbc.update("DELETE FROM vorhaben_kosten_einfuehrung WHERE project_id = ?", projectId);
  }

  @Test
  void flywayLaeuftAufEinerLeerenDatenbankBisV68Durch() {
    jdbc.execute("CREATE DATABASE leer");
    DriverManagerDataSource leer = datenquelle("leer");

    migrateTo(leer, "68");

    JdbcTemplate aufLeer = new JdbcTemplate(leer);
    assertThat(
            aufLeer.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success"
                    + " ORDER BY installed_rank DESC LIMIT 1",
                String.class))
        .as("zuletzt erfolgreich gefahrene Migration auf der leeren Datenbank")
        .isEqualTo("68");
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
            "INSERT INTO app_user (email, display_name) VALUES ('m@example.com', 'M')"
                + " RETURNING id");
    projectId =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    long boardId =
        id("INSERT INTO board (project_id, name) VALUES (" + projectId + ", 'B') RETURNING id");
    long spalte =
        id(
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + boardId
                + ", 'Ready', 0) RETURNING id");
    epicId =
        id(
            "INSERT INTO card (board_id, column_id, number, title, position_in_column, type)"
                + " VALUES ("
                + boardId
                + ", "
                + spalte
                + ", 1, 'Export', 0, 'EPIC') RETURNING id");
  }

  private static long id(String sql) {
    Long generated = jdbc.queryForObject(sql, Long.class);
    return generated == null ? 0L : generated;
  }
}
