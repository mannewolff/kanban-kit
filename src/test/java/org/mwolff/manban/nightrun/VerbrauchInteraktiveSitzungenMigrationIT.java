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
 * Verifiziert {@code V34__verbrauch_interaktive_sitzungen.sql} (Issue #1009) gegen einen
 * Datenbestand, der <b>vor</b> der Migration angelegt wurde.
 *
 * <p>Die Migration traegt die Gattung ({@code kind}) in {@code night_run} und {@code
 * night_run_item} ein, oeffnet {@code ck_night_run_mode} fuer {@code INTERACTIVE}, erweitert die
 * beiden Teil-Indizes aus {@code V33} um die Gattung und gibt {@code project} den Erfassungsbeginn.
 * Es gibt <b>keinen</b> Backfill: Bestandszeilen sind Nachtlaeufe und leben vom Vorgabewert — gegen
 * leere Tabellen bewiese ein gruener Lauf davon nichts. Eigener Container, um gezielt bis {@code
 * V33} zu migrieren.
 */
class VerbrauchInteraktiveSitzungenMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  /** Der Lauf, den es schon vor {@code V34} gab — er belegt den Vorgabewert der Gattung. */
  private static final Instant BESTAND = Instant.parse("2026-09-01T22:00:00Z");

  private static JdbcTemplate jdbc;
  private static long projectId;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void bestandBisV33AnlegenUndDannNachV34Migrieren() {
    DriverManagerDataSource dataSource = datenquelle(POSTGRES.getDatabaseName());
    jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "33");
    projectId = seedBestand(jdbc);
    migrateTo(dataSource, "34");
  }

  @Test
  void bestandszeilenTragenNachDerMigrationDieGattungNight() {
    assertThat(
            jdbc.queryForList(
                "SELECT kind FROM night_run WHERE started_at = ?",
                String.class,
                Timestamp.from(BESTAND)))
        .as("Vorgabewert der Gattung auf dem Lauf-Kopf")
        .containsExactly("NIGHT");

    assertThat(
            jdbc.queryForList(
                "SELECT kind FROM night_run_item WHERE started_at = ? ORDER BY card_number",
                String.class,
                Timestamp.from(BESTAND)))
        .as("Vorgabewert der Gattung auf den Arbeitspaketen")
        .containsExactly("NIGHT", "NIGHT");
  }

  @Test
  void eineUnbekannteGattungWirdInBeidenTabellenAbgewiesen() {
    assertThatThrownBy(
            () -> lauf(Instant.parse("2026-09-03T10:00:00Z"), "IMPLEMENTATION", "SONSTIGES"))
        .as("CHECK auf night_run.kind")
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_night_run_kind");

    assertThatThrownBy(() -> paket(Instant.parse("2026-09-03T10:00:00Z"), 91, "SONSTIGES"))
        .as("CHECK auf night_run_item.kind")
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_night_run_item_kind");
  }

  @Test
  void derModusInteractiveWirdAngenommenUndEinUnbekannterWeiterhinAbgewiesen() {
    Instant sitzung = Instant.parse("2026-09-04T09:00:00Z");

    lauf(sitzung, "INTERACTIVE", "INTERACTIVE");

    assertThat(
            jdbc.queryForList(
                "SELECT mode FROM night_run WHERE started_at = ?",
                String.class,
                Timestamp.from(sitzung)))
        .as("ck_night_run_mode laesst die Sitzungs-Laufart zu")
        .containsExactly("INTERACTIVE");

    assertThatThrownBy(() -> lauf(Instant.parse("2026-09-04T11:00:00Z"), "SONSTIGES", "NIGHT"))
        .as("ck_night_run_mode weist einen unbekannten Modus weiterhin ab")
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_night_run_mode");
  }

  @Test
  void beideTeilIndizesFuehrenDieGattung() {
    assertThat(indexdef("idx_night_run_item_orphan"))
        .as("Kappung je Gattung ohne Vollscan")
        .contains("(project_id, kind, started_at)")
        .contains("WHERE (night_run_id IS NULL)");

    assertThat(indexdef("idx_night_run_item_card"))
        .as("Anlaeufe einer Karte samt Gattung ohne Griff in die Tabelle")
        .contains("(project_id, card_number, started_at, kind)");
  }

  @Test
  void projectTraegtDenErfassungsbeginnAlsNullbaresZeitfeldOhneVorgabewert() {
    assertThat(
            jdbc.query(
                "SELECT data_type, is_nullable, column_default FROM information_schema.columns"
                    + " WHERE table_name = 'project' AND column_name = 'interactive_usage_since'",
                (rs, row) ->
                    tuple(
                        rs.getString("data_type"),
                        rs.getString("is_nullable"),
                        rs.getString("column_default"))))
        .as("Typ, Nullbarkeit und Vorgabewert des Erfassungsbeginns")
        .containsExactly(tuple("timestamp with time zone", "YES", null));

    assertThat(
            jdbc.queryForObject(
                "SELECT interactive_usage_since FROM project WHERE id = ?",
                Timestamp.class,
                projectId))
        .as("Bestandsprojekt hat keinen Erfassungsbeginn")
        .isNull();
  }

  @Test
  void flywayLaeuftAufEinerLeerenDatenbankBisV34Durch() {
    jdbc.execute("CREATE DATABASE leer");
    DriverManagerDataSource leer = datenquelle("leer");

    migrateTo(leer, "34");

    JdbcTemplate aufLeer = new JdbcTemplate(leer);
    assertThat(
            aufLeer.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success"
                    + " ORDER BY installed_rank DESC LIMIT 1",
                String.class))
        .as("zuletzt erfolgreich gefahrene Migration auf der leeren Datenbank")
        .isEqualTo("34");
    assertThat(
            aufLeer.queryForList(
                "SELECT table_name || '.' || column_name FROM information_schema.columns"
                    + " WHERE (table_name, column_name) IN"
                    + " (('night_run', 'kind'), ('night_run_item', 'kind'),"
                    + " ('project', 'interactive_usage_since'))"
                    + " ORDER BY 1",
                String.class))
        .as("die drei neuen Spalten stehen auch ohne Bestandsdaten")
        .containsExactly(
            "night_run.kind", "night_run_item.kind", "project.interactive_usage_since");
  }

  private static String indexdef(String indexname) {
    return jdbc.queryForObject(
        "SELECT indexdef FROM pg_indexes WHERE indexname = ?", String.class, indexname);
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
    long userId =
        id(
            jdbc,
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('m@example.com', 'x', 'M') RETURNING id");
    long projekt =
        id(
            jdbc,
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    Long laufId =
        jdbc.queryForObject(
            "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
                + " skipped_count, unparsed_count, created_at)"
                + " VALUES (?, ?, 'IMPLEMENTATION', 1000, 2, 0, 0, now()) RETURNING id",
            Long.class,
            projekt,
            Timestamp.from(BESTAND));
    for (int cardNumber : new int[] {1, 2}) {
      jdbc.update(
          "INSERT INTO night_run_item (night_run_id, card_number, title, state, project_id,"
              + " started_at, mode)"
              + " VALUES (?, ?, 'Bestand', 'GREEN', ?, ?, 'IMPLEMENTATION')",
          laufId,
          cardNumber,
          projekt,
          Timestamp.from(BESTAND));
    }
    return projekt;
  }

  private static void lauf(Instant start, String mode, String kind) {
    jdbc.update(
        "INSERT INTO night_run (project_id, started_at, mode, kind, duration_ms, processed_count,"
            + " skipped_count, unparsed_count, created_at)"
            + " VALUES (?, ?, ?, ?, 1000, 1, 0, 0, now())",
        projectId,
        Timestamp.from(start),
        mode,
        kind);
  }

  private static void paket(Instant start, int cardNumber, String kind) {
    jdbc.update(
        "INSERT INTO night_run_item (card_number, title, state, project_id, started_at, mode, kind)"
            + " VALUES (?, 'Sitzung', 'GREEN', ?, ?, 'IMPLEMENTATION', ?)",
        cardNumber,
        projectId,
        Timestamp.from(start),
        kind);
  }

  private static long id(JdbcTemplate jdbc, String sql) {
    Long generated = jdbc.queryForObject(sql, Long.class);
    return generated == null ? 0L : generated;
  }
}
