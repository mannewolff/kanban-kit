package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V37__nachtlauf_budgets_und_stufen.sql} (Issue #1112, Plan #1110) gegen einen
 * Datenbestand, der <b>vor</b> der Migration angelegt wurde.
 *
 * <p>Die Migration gibt {@code night_run} die fuenf Budget-Spalten und die beiden Herkunftsspalten,
 * {@code night_run} und {@code night_run_item} je Modellzeit und Zuege und legt die Tabelle {@code
 * night_run_item_stage} an. Es gibt <b>keinen</b> Backfill (AK 5, Plan #1110 E16): Bestandszeilen
 * tragen danach ueberall {@code NULL}, und zu ihnen gibt es keine Stufe — gegen leere Tabellen
 * bewiese ein gruener Lauf davon nichts. Eigener Container, um gezielt bis {@code V36} zu
 * migrieren.
 */
class NachtlaufBudgetsUndStufenMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  /** Der Lauf, den es schon vor {@code V37} gab — er belegt das Ausbleiben des Backfills. */
  private static final Instant BESTAND = Instant.parse("2026-09-01T22:00:00Z");

  /** Die neuen Spalten an {@code night_run}, in der Reihenfolge der Migration. */
  private static final List<String> NEUE_LAUF_SPALTEN =
      List.of(
          "budget_plan_min",
          "budget_review_min",
          "budget_pakete_min",
          "budget_abdeckung_min",
          "budget_kosten_usd",
          "budget_origin",
          "budget_default_fields",
          "model_duration_ms",
          "turns");

  private static JdbcTemplate jdbc;
  private static long projectId;
  private static long bestandsPaketId;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void bestandBisV36AnlegenUndDannNachV37Migrieren() {
    DriverManagerDataSource dataSource = datenquelle(POSTGRES.getDatabaseName());
    jdbc = new JdbcTemplate(dataSource);

    migrateTo(dataSource, "36");
    seedBestand();
    migrateTo(dataSource, "37");
  }

  @Test
  void derBestandslaufTraegtInAllenNeuenSpaltenNull() {
    assertThat(
            jdbc.queryForMap(
                "SELECT "
                    + String.join(", ", NEUE_LAUF_SPALTEN)
                    + " FROM night_run"
                    + " WHERE started_at = ?",
                Timestamp.from(BESTAND)))
        .as("kein Backfill an den neun neuen Lauf-Spalten")
        .containsOnlyKeys(NEUE_LAUF_SPALTEN)
        .allSatisfy((spalte, wert) -> assertThat(wert).as(spalte).isNull());
  }

  @Test
  void dasBestandspaketTraegtModellzeitUndZuegeAlsNull() {
    assertThat(
            jdbc.queryForMap(
                "SELECT model_duration_ms, turns FROM night_run_item WHERE id = ?",
                bestandsPaketId))
        .as("kein Backfill an den beiden neuen Paket-Spalten")
        .containsEntry("model_duration_ms", null)
        .containsEntry("turns", null);
  }

  @Test
  void zumBestandspaketGibtEsKeineStufe() {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM night_run_item_stage WHERE night_run_item_id = ?",
                Long.class,
                bestandsPaketId))
        .as("night_run_item_stage bleibt fuer Bestandszeilen leer")
        .isZero();
  }

  @Test
  void eineUnbekannteHerkunftDerBudgetsWirdAbgewiesen() {
    assertThatThrownBy(() -> lauf(Instant.parse("2026-09-03T10:00:00Z"), "GERATEN"))
        .as("CHECK auf night_run.budget_origin")
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_night_run_budget_origin");
  }

  @Test
  void beideBekanntenHerkuenfteWerdenAngenommen() {
    lauf(Instant.parse("2026-09-04T10:00:00Z"), "CONFIGURED");
    lauf(Instant.parse("2026-09-04T11:00:00Z"), "DEFAULTED");

    assertThat(
            jdbc.queryForList(
                "SELECT budget_origin FROM night_run WHERE budget_origin IS NOT NULL"
                    + " ORDER BY budget_origin",
                String.class))
        .containsExactly("CONFIGURED", "DEFAULTED");
  }

  @Test
  void eineUnbekannteStufeWirdAbgewiesen() {
    assertThatThrownBy(
            () -> stufe(paket(Instant.parse("2026-09-07T10:00:00Z"), 4713), "ABDECKUNGEN"))
        .as("CHECK auf night_run_item_stage.stage")
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_night_run_item_stage_stage");
  }

  /** Ein eigenes Paket, damit der Bestandsfall oben seine Leere behaelt, egal in welcher Folge. */
  @Test
  void jedeStufeKommtJePaketHoechstensEinmalVor() {
    long paketId = paket(Instant.parse("2026-09-06T10:00:00Z"), 4712);
    stufe(paketId, "PLAN");

    assertThatThrownBy(() -> stufe(paketId, "PLAN"))
        .as("eindeutiger Schluessel (night_run_item_id, stage)")
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_night_run_item_stage");
  }

  /**
   * Die Stufe faellt mit ihrem Arbeitspaket. Anders als das Paket selbst, das die Verdraengung
   * seines Laufs verwaist ueberdauert (V33), waere eine Stufe ohne ihren Vorgang keine Aussage.
   */
  @Test
  void dasLoeschenDesArbeitspaketsNimmtSeineStufenMit() {
    long paketId = paket(Instant.parse("2026-09-05T10:00:00Z"), 4711);
    stufe(paketId, "REVIEW");

    jdbc.update("DELETE FROM night_run_item WHERE id = ?", paketId);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM night_run_item_stage WHERE night_run_item_id = ?",
                Long.class,
                paketId))
        .isZero();
  }

  @Test
  void derZugriffswegAufDieStufenEinesPaketsStehtAlsIndex() {
    assertThat(
            jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = ?",
                String.class,
                "idx_night_run_item_stage_item"))
        .as("Nachladen der Stufen zu einer Menge von Paketen ohne Vollscan")
        .contains("(night_run_item_id)");
  }

  @Test
  void flywayLaeuftAufEinerLeerenDatenbankBisV37Durch() {
    jdbc.execute("CREATE DATABASE leer");
    DriverManagerDataSource leer = datenquelle("leer");

    migrateTo(leer, "37");

    JdbcTemplate aufLeer = new JdbcTemplate(leer);
    assertThat(
            aufLeer.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success"
                    + " ORDER BY installed_rank DESC LIMIT 1",
                String.class))
        .as("zuletzt erfolgreich gefahrene Migration auf der leeren Datenbank")
        .isEqualTo("37");
    assertThat(
            aufLeer.queryForList(
                "SELECT column_name FROM information_schema.columns"
                    + " WHERE table_name = 'night_run_item_stage' ORDER BY ordinal_position",
                String.class))
        .as("die Stufentabelle steht auch ohne Bestandsdaten")
        .containsExactly(
            "id",
            "night_run_item_id",
            "stage",
            "duration_ms",
            "cost_usd",
            "input_tokens",
            "output_tokens",
            "cached_input_tokens",
            "model_duration_ms",
            "turns");
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
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('m@example.com', 'x', 'M') RETURNING id");
    projectId =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    Long laufId =
        jdbc.queryForObject(
            "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
                + " skipped_count, unparsed_count, created_at)"
                + " VALUES (?, ?, 'CHAIN', 1000, 1, 0, 0, now()) RETURNING id",
            Long.class,
            projectId,
            Timestamp.from(BESTAND));
    Long paketId =
        jdbc.queryForObject(
            "INSERT INTO night_run_item (night_run_id, card_number, title, state, project_id,"
                + " started_at, mode)"
                + " VALUES (?, 1112, 'Bestand', 'GREEN', ?, ?, 'CHAIN') RETURNING id",
            Long.class,
            laufId,
            projectId,
            Timestamp.from(BESTAND));
    bestandsPaketId = paketId == null ? 0L : paketId;
  }

  private static void lauf(Instant start, String budgetOrigin) {
    jdbc.update(
        "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
            + " skipped_count, unparsed_count, created_at, budget_origin)"
            + " VALUES (?, ?, 'CHAIN', 1000, 1, 0, 0, now(), ?)",
        projectId,
        Timestamp.from(start),
        budgetOrigin);
  }

  private static long paket(Instant start, int cardNumber) {
    Long paketId =
        jdbc.queryForObject(
            "INSERT INTO night_run_item (card_number, title, state, project_id, started_at, mode)"
                + " VALUES (?, 'Vorgang', 'GREEN', ?, ?, 'CHAIN') RETURNING id",
            Long.class,
            cardNumber,
            projectId,
            Timestamp.from(start));
    return paketId == null ? 0L : paketId;
  }

  private static void stufe(long nightRunItemId, String stage) {
    jdbc.update(
        "INSERT INTO night_run_item_stage (night_run_item_id, stage) VALUES (?, ?)",
        nightRunItemId,
        stage);
  }

  private static long id(String sql) {
    Long generated = jdbc.queryForObject(sql, Long.class);
    return generated == null ? 0L : generated;
  }
}
