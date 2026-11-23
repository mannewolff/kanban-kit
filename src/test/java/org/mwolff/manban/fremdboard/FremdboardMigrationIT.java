package org.mwolff.manban.fremdboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V75__fremdboard.sql} (Issue #2156, Plan #2152): Die Migration läuft auf einer
 * leeren und auf einer bestehenden Datenbank, legt Verbindung und Ereignissperre an und ergänzt die
 * drei Spalten, ohne den Bestand anzufassen. Dazu {@code V82__fremdboard_stand_am.sql} (Issue
 * #2222, Plan #2221 E4): Die neue Spalte {@code stand_am} übernimmt {@code letzter_abgleich_am}
 * nur, wo schon ein Vollabgleich gelang.
 *
 * <p>Eigener Container statt {@link org.mwolff.manban.AbstractIntegrationTest}: Der Bestand muss
 * vor {@code V75} bzw. {@code V82} stehen, und deren Reset leerte ihn vor jeder Methode. Die drei
 * Fälle laufen je in einer eigenen Datenbank desselben Containers; die beiden ersten bleiben bei
 * {@code V75}, damit der Spaltentest genau deren Stand prüft.
 */
class FremdboardMigrationIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.15");

  private static JdbcTemplate leer;
  private static JdbcTemplate bestand;
  private static long bestandsKarte;
  private static long bestandsProjekt;
  private static JdbcTemplate stand;
  private static long mitVollabgleich;
  private static long nurAbgleich;
  private static long ohneAbgleich;

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

    wurzel.execute("CREATE DATABASE fremdboard_stand");
    DriverManagerDataSource standQuelle = datenquelle("fremdboard_stand");
    stand = new JdbcTemplate(standQuelle);
    migriere(standQuelle, "81");
    long besitzer =
        idIn(
            stand,
            "INSERT INTO app_user (email, display_name) VALUES ('s@example.com', 'S')"
                + " RETURNING id");
    mitVollabgleich =
        verbindungMitAbgleich("Voll", besitzer, "2026-11-20T10:00:00Z", "2026-11-20T09:00:00Z");
    nurAbgleich = verbindungMitAbgleich("Nur", besitzer, "2026-11-20T10:00:00Z", null);
    ohneAbgleich = verbindungMitAbgleich("Ohne", besitzer, null, null);
    migriere(standQuelle, "82");
  }

  @Test
  void v82_setztStandAmNurBeiEinerVerbindungMitVollabgleich_aufIhrenLetztenAbgleich() {
    assertThat(standAm(mitVollabgleich)).isEqualTo("2026-11-20T10:00:00Z");
    assertThat(standAm(nurAbgleich)).isNull();
    assertThat(standAm(ohneAbgleich)).isNull();
    assertThat(datentyp(stand, "fremdboard_verbindung", "stand_am"))
        .isEqualTo("timestamp with time zone");
    assertThat(nullbar(stand, "fremdboard_verbindung", "stand_am")).isEqualTo("YES");
  }

  private static @Nullable String standAm(long projekt) {
    return stand.queryForObject(
        "SELECT to_char(stand_am AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')"
            + " FROM fremdboard_verbindung WHERE project_id = ?",
        String.class,
        projekt);
  }

  /**
   * Legt in der Datenbank {@code stand} Projekt, Board und Verbindung an; liefert die Projekt-Id.
   */
  private static long verbindungMitAbgleich(
      String name, long besitzer, @Nullable String letzterAbgleich, @Nullable String letzterVoll) {
    long projekt =
        idIn(
            stand,
            "INSERT INTO project (name, owner_user_id) VALUES (?, ?) RETURNING id",
            name,
            besitzer);
    long board =
        idIn(stand, "INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", projekt);
    stand.update(
        "INSERT INTO fremdboard_verbindung (project_id, art, basis_url, projekt_pfad,"
            + " zugang_verschluesselt, statuslabels, board_id, angebunden_am,"
            + " letzter_abgleich_am, letzter_vollabgleich_am)"
            + " VALUES (?, 'GITLAB', 'https://gitlab.com', 'gruppe/projekt', 'geheim',"
            + " '{\"ready\":\"Ready\"}'::jsonb, ?, now(),"
            + " CAST(? AS timestamptz), CAST(? AS timestamptz))",
        projekt,
        board,
        letzterAbgleich,
        letzterVoll);
    return projekt;
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
    return idIn(bestand, sql, argumente);
  }

  private static long idIn(JdbcTemplate jdbc, String sql, Object... argumente) {
    Long erzeugt = jdbc.queryForObject(sql, Long.class, argumente);
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
