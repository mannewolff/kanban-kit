package org.mwolff.manban.fremdboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V75__fremdboard.sql} (Issue #2156, Plan #2152): Die Migration läuft auf einer
 * leeren und auf einer bestehenden Datenbank, legt Verbindung und Ereignissperre an und ergänzt die
 * drei Spalten, ohne den Bestand anzufassen.
 *
 * <p>Eigener Container statt {@link org.mwolff.manban.AbstractIntegrationTest}: Der Bestand muss
 * vor {@code V75} stehen, und deren Reset leerte ihn vor jeder Methode. Die beiden Fälle laufen je
 * in einer eigenen Datenbank desselben Containers.
 */
class FremdboardMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.15");

  private static JdbcTemplate leer;
  private static JdbcTemplate bestand;
  private static long bestandsKarte;
  private static long bestandsProjekt;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void migriereLeerUndBestand() {
    JdbcTemplate wurzel = new JdbcTemplate(datenquelle(POSTGRES.getDatabaseName()));
    wurzel.execute("CREATE DATABASE fremdboard_leer");
    wurzel.execute("CREATE DATABASE fremdboard_bestand");

    DriverManagerDataSource leereQuelle = datenquelle("fremdboard_leer");
    leer = new JdbcTemplate(leereQuelle);
    migriere(leereQuelle, "75");

    DriverManagerDataSource bestandsQuelle = datenquelle("fremdboard_bestand");
    bestand = new JdbcTemplate(bestandsQuelle);
    migriere(bestandsQuelle, "74");
    seedeBestand();
    migriere(bestandsQuelle, "75");
  }

  @Test
  void aufLeererDatenbankStehenBeideTabellenUndDieDreiSpalten() {
    assertThat(spalten(leer, "fremdboard_verbindung"))
        .containsExactlyInAnyOrder(
            "project_id",
            "art",
            "basis_url",
            "projekt_pfad",
            "zugang_verschluesselt",
            "kit_konto",
            "statuslabels",
            "board_id",
            "angebunden_am",
            "letzter_abgleich_am",
            "letzter_vollabgleich_am",
            "letzter_fehler");
    assertThat(spalten(leer, "fremdboard_ereignis"))
        .containsExactlyInAnyOrder("project_id", "extern_id", "art");
    assertThat(nullbar(leer, "card", "extern_url")).isEqualTo("YES");
    assertThat(nullbar(leer, "card_label_event", "extern_akteur")).isEqualTo("YES");
    assertThat(nullbar(leer, "card_activity", "extern_akteur")).isEqualTo("YES");
    assertThat(datentyp(leer, "fremdboard_verbindung", "statuslabels")).isEqualTo("jsonb");
  }

  @Test
  void aufBestehenderDatenbankBleibtDerBestandUnveraendertUndDieNeuenSpaltenLeer() {
    assertThat(
            bestand.queryForObject(
                "SELECT title FROM card WHERE id = ?", String.class, bestandsKarte))
        .isEqualTo("Bestand");
    assertThat(
            bestand.queryForObject(
                "SELECT count(*) FROM card WHERE extern_url IS NULL", Integer.class))
        .isEqualTo(1);
    assertThat(
            bestand.queryForObject(
                "SELECT count(*) FROM card_activity WHERE extern_akteur IS NULL", Integer.class))
        .isEqualTo(1);
    assertThat(
            bestand.queryForObject(
                "SELECT count(*) FROM card_label_event WHERE extern_akteur IS NULL", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void eineVerbindungJeProjektUndJedesEreignisNurEinmal() {
    long[] eigenes = projektMitBoard("Eindeutig");
    verbinde(eigenes[0], eigenes[1], "GITLAB");
    assertThatThrownBy(() -> verbinde(eigenes[0], eigenes[1], "GITLAB"))
        .isInstanceOf(DataIntegrityViolationException.class);

    ereignis(eigenes[0], "label", "17");
    ereignis(eigenes[0], "state", "17");
    assertThatThrownBy(() -> ereignis(eigenes[0], "label", "17"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void dieArtIstAufBekannteSystemeBeschraenkt() {
    long[] eigenes = projektMitBoard("Art");
    assertThatThrownBy(() -> verbinde(eigenes[0], eigenes[1], "JIRA"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void jedesBoardTraegtHoechstensEineVerbindung() {
    long[] erstes = projektMitBoard("Erstes");
    long[] zweites = projektMitBoard("Zweites");
    verbinde(erstes[0], erstes[1], "GITLAB");
    assertThatThrownBy(() -> verbinde(zweites[0], erstes[1], "GITLAB"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void dasSpiegelBoardBleibtSolangeDieVerbindungStehtUndGehtMitDemProjekt() {
    long[] projekt = projektMitBoard("Geloescht");
    verbinde(projekt[0], projekt[1], "GITLAB");
    ereignis(projekt[0], "label", "1");

    assertThatThrownBy(() -> bestand.update("DELETE FROM board WHERE id = ?", projekt[1]))
        .isInstanceOf(DataIntegrityViolationException.class);

    bestand.update("DELETE FROM project WHERE id = ?", projekt[0]);
    assertThat(
            bestand.queryForObject(
                "SELECT count(*) FROM fremdboard_verbindung WHERE project_id = ?",
                Integer.class,
                projekt[0]))
        .isZero();
    assertThat(
            bestand.queryForObject(
                "SELECT count(*) FROM fremdboard_ereignis WHERE project_id = ?",
                Integer.class,
                projekt[0]))
        .isZero();
  }

  private static void verbinde(long projekt, long board, String art) {
    bestand.update(
        "INSERT INTO fremdboard_verbindung (project_id, art, basis_url, projekt_pfad,"
            + " zugang_verschluesselt, statuslabels, board_id, angebunden_am)"
            + " VALUES (?, ?, 'https://gitlab.com', 'gruppe/projekt', 'geheim',"
            + " '{\"ready\":\"Ready\"}'::jsonb, ?, now())",
        projekt,
        art,
        board);
  }

  private static void ereignis(long projekt, String art, String externId) {
    bestand.update(
        "INSERT INTO fremdboard_ereignis (project_id, extern_id, art) VALUES (?, ?, ?)",
        projekt,
        externId,
        art);
  }

  /** Ein weiteres Projekt mit Board im Bestand; liefert Projekt- und Board-Id. */
  private static long[] projektMitBoard(String name) {
    long besitzer = id("SELECT owner_user_id FROM project WHERE id = ?", bestandsProjekt);
    long projekt =
        id("INSERT INTO project (name, owner_user_id) VALUES (?, ?) RETURNING id", name, besitzer);
    long board = id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", projekt);
    return new long[] {projekt, board};
  }

  private static void seedeBestand() {
    long nutzer =
        id(
            "INSERT INTO app_user (email, display_name) VALUES ('b@example.com', 'B')"
                + " RETURNING id");
    bestandsProjekt =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", nutzer);
    long bestandsBoard =
        id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", bestandsProjekt);
    long spalte =
        id(
            "INSERT INTO board_column (board_id, name, position) VALUES (?, 'Backlog', 0)"
                + " RETURNING id",
            bestandsBoard);
    bestandsKarte =
        id(
            "INSERT INTO card (project_id, board_id, column_id, number, title,"
                + " position_in_column) VALUES (?, ?, ?, 1, 'Bestand', 0) RETURNING id",
            bestandsProjekt,
            bestandsBoard,
            spalte);
    bestand.update(
        "INSERT INTO card_activity (card_id, type, detail, created_at)"
            + " VALUES (?, 'CREATED', 'angelegt', now())",
        bestandsKarte);
    bestand.update(
        "INSERT INTO card_label_event (card_id, label_name, created_at)"
            + " VALUES (?, 'kit:night', now())",
        bestandsKarte);
  }

  private static List<String> spalten(JdbcTemplate jdbc, String tabelle) {
    return jdbc.queryForList(
        "SELECT column_name FROM information_schema.columns"
            + " WHERE table_schema = current_schema() AND table_name = ?",
        String.class,
        tabelle);
  }

  private static String nullbar(JdbcTemplate jdbc, String tabelle, String spalte) {
    return jdbc.queryForObject(
        "SELECT is_nullable FROM information_schema.columns"
            + " WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
        String.class,
        tabelle,
        spalte);
  }

  private static String datentyp(JdbcTemplate jdbc, String tabelle, String spalte) {
    return jdbc.queryForObject(
        "SELECT data_type FROM information_schema.columns"
            + " WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
        String.class,
        tabelle,
        spalte);
  }

  private static long id(String sql, Object... argumente) {
    Long erzeugt = bestand.queryForObject(sql, Long.class, argumente);
    return erzeugt == null ? 0L : erzeugt;
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

  private static void migriere(DriverManagerDataSource quelle, String version) {
    Flyway.configure()
        .dataSource(quelle)
        .locations("classpath:db/migration")
        .target(version)
        .load()
        .migrate();
  }
}
