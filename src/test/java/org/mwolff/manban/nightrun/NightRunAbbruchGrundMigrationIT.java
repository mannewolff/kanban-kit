package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Timestamp;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.domain.NightRunLimits;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V42__night_run_abort_reason.sql} (Issue #1142) gegen einen Datenbestand, der
 * <b>vor</b> der Migration angelegt wurde — nach dem Muster von {@link
 * NachtlaufOhneArbeitMigrationIT}.
 *
 * <p>Die Migration gibt {@code night_run} die Spalte {@code abort_reason}. Es gibt <b>keinen</b>
 * Backfill (Plan #1139, E11): Aus einem gespeicherten Lauf laesst sich ein Abbruchgrund nicht
 * herleiten. Eigener Container, um gezielt bis {@code V41} zu migrieren.
 *
 * <p>Diese Klasse ist die <b>eine</b> Stelle, die die Spaltenlaenge prueft (Plan-Pruefung #1139,
 * HINWEIS 8). {@code NightRunErrorClassSyncTest} liest Spaltenlaengen allein aus {@code
 * V29__night_run.sql} und kann eine Spalte aus {@code V42} ohne Umbau nicht sehen.
 */
class NightRunAbbruchGrundMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  /** Ein Lauf von vor {@code V42}, der unabgeschlossen blieb — der Fall aus E11. */
  private static final Instant BESTAND_UNVOLLSTAENDIG = Instant.parse("2026-09-01T22:00:00Z");

  /** Ein Lauf von vor {@code V42}, der abgeschlossen wurde — die Gegenprobe. */
  private static final Instant BESTAND_VOLLSTAENDIG = Instant.parse("2026-09-02T22:00:00Z");

  private static JdbcTemplate jdbc;
  private static long projectId;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void bestandBisV41AnlegenUndDannNachV42Migrieren() {
    DriverManagerDataSource dataSource = datenquelle(POSTGRES.getDatabaseName());
    jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "41");
    projectId = seedBestand(jdbc);
    migrateTo(dataSource, "42");
  }

  /** Kein Backfill (E11): Auch der unabgeschlossene Bestandslauf traegt keinen Abbruchgrund. */
  @Test
  void bestandslaeufeTragenNachDerMigrationKeinenAbbruchgrund() {
    assertThat(
            jdbc.query(
                // Ausdruecklich auf die beiden Bestandszeilen eingegrenzt: Andere Tests dieser
                // Klasse legen eigene Laeufe an, und die Reihenfolge der Tests ist nicht
                // zugesichert.
                "SELECT started_at, complete, abort_reason FROM night_run"
                    + " WHERE project_id = ? AND started_at IN (?, ?) ORDER BY started_at",
                (rs, row) ->
                    tuple(
                        rs.getTimestamp("started_at").toInstant(),
                        rs.getBoolean("complete"),
                        rs.getString("abort_reason")),
                projectId,
                Timestamp.from(BESTAND_UNVOLLSTAENDIG),
                Timestamp.from(BESTAND_VOLLSTAENDIG)))
        .as("beide Bestandslaeufe ohne Abbruchgrund, auch der unabgeschlossene")
        .containsExactly(
            tuple(BESTAND_UNVOLLSTAENDIG, false, null), tuple(BESTAND_VOLLSTAENDIG, true, null));
  }

  /**
   * Die Laenge ist {@link NightRunLimits#EXCERPT_MAX} und nicht die 300 von {@code no_work_reason}
   * (E4): Ein Abbruchgrund fuehrt Dateilisten, kein Anzeigesatz.
   */
  @Test
  void dieSpalteIstNullbarOhneVorgabewertUndVierausendZeichenLang() {
    assertThat(
            jdbc.query(
                "SELECT data_type, is_nullable, column_default, character_maximum_length"
                    + " FROM information_schema.columns"
                    + " WHERE table_name = 'night_run' AND column_name = 'abort_reason'",
                (rs, row) ->
                    tuple(
                        rs.getString("data_type"),
                        rs.getString("is_nullable"),
                        rs.getString("column_default"),
                        rs.getInt("character_maximum_length"))))
        .as("Typ, Nullbarkeit, Vorgabewert und Laenge des Abbruchgrundes")
        .containsExactly(tuple("character varying", "YES", null, NightRunLimits.EXCERPT_MAX));
  }

  /** Die Laengengrenze ist eine Zusicherung der Datenbank, nicht nur eine der Anwendung. */
  @Test
  void einAbbruchgrundUeberDerGrenzeWirdAbgewiesen() {
    assertThatThrownBy(
            () ->
                laufMitAbbruchgrund(
                    Instant.parse("2026-09-05T22:00:00Z"),
                    "x".repeat(NightRunLimits.EXCERPT_MAX + 1)))
        .isInstanceOf(DataIntegrityViolationException.class);

    Instant anDerGrenze = Instant.parse("2026-09-06T22:00:00Z");
    laufMitAbbruchgrund(anDerGrenze, "x".repeat(NightRunLimits.EXCERPT_MAX));
    assertThat(
            jdbc.queryForObject(
                "SELECT length(abort_reason) FROM night_run WHERE started_at = ?",
                Integer.class,
                Timestamp.from(anDerGrenze)))
        .as("genau an der Grenze wird angenommen")
        .isEqualTo(NightRunLimits.EXCERPT_MAX);
  }

  @Test
  void flywayLaeuftAufEinerLeerenDatenbankBisV42Durch() {
    jdbc.execute("CREATE DATABASE leer_v42");
    DriverManagerDataSource leer = datenquelle("leer_v42");

    migrateTo(leer, "42");

    JdbcTemplate aufLeer = new JdbcTemplate(leer);
    assertThat(
            aufLeer.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success"
                    + " ORDER BY installed_rank DESC LIMIT 1",
                String.class))
        .as("zuletzt erfolgreich gefahrene Migration auf der leeren Datenbank")
        .isEqualTo("42");
  }

  private static void laufMitAbbruchgrund(Instant startedAt, String grund) {
    jdbc.update(
        "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
            + " skipped_count, unparsed_count, created_at, abort_reason)"
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
            + " skipped_count, unparsed_count, created_at, complete)"
            + " VALUES (?, ?, 'IMPLEMENTATION', 1000, 0, 0, 0, now(), false)",
        projekt,
        Timestamp.from(BESTAND_UNVOLLSTAENDIG));
    jdbc.update(
        "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
            + " skipped_count, unparsed_count, created_at, complete)"
            + " VALUES (?, ?, 'IMPLEMENTATION', 1000, 2, 0, 0, now(), true)",
        projekt,
        Timestamp.from(BESTAND_VOLLSTAENDIG));
    return projekt;
  }
}
