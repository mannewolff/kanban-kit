package org.mwolff.manban.benachrichtigung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.benachrichtigung.domain.AnlassArt;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V69__benachrichtigung.sql} (Issue #2073, Plan #2070 E4, E14): Die Datenbank
 * hält „höchstens eine Mail“ fest, und mit Karte, Projekt, Person, Lauf und Label-Ereignis
 * verschwinden die abhängigen Zeilen.
 *
 * <p>Eigener Container, um gezielt bis {@code V69} zu migrieren.
 */
class BenachrichtigungSchemaIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.15");

  private static final String ZUSTAND_OFFEN = "OFFEN";

  private static JdbcTemplate jdbc;

  private long owner;
  private long person;
  private long projekt;
  private long karte;
  private long lauf;
  private long labelEreignis;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void migriereBisV69() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target("69")
        .load()
        .migrate();
  }

  @BeforeEach
  void legeBestandAn() {
    jdbc.execute("TRUNCATE app_user, project CASCADE");
    owner = konto("owner@example.com");
    person = konto("person@example.com");
    projekt = id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", owner);
    long board = id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", projekt);
    long spalte =
        id(
            "INSERT INTO board_column (board_id, name, position) VALUES (?, 'Ready', 0)"
                + " RETURNING id",
            board);
    karte =
        id(
            "INSERT INTO card (board_id, column_id, number, title, position_in_column)"
                + " VALUES (?, ?, 1, 'K', 0) RETURNING id",
            board,
            spalte);
    lauf =
        id(
            "INSERT INTO night_run (project_id, started_at, mode, duration_ms, processed_count,"
                + " skipped_count, unparsed_count, created_at)"
                + " VALUES (?, now(), 'IMPLEMENTATION', 1000, 1, 0, 0, now()) RETURNING id",
            projekt);
    labelEreignis =
        id(
            "INSERT INTO card_label_event (card_id, label_name, created_at)"
                + " VALUES (?, 'kit:klaeren', now()) RETURNING id",
            karte);
  }

  @Test
  void einZweiterAnlassMitGleichemLaufUndGleicherArtWirdAbgewiesen() {
    anlassZumLauf(AnlassArt.LAUF_WARTET.name());

    assertThatThrownBy(() -> anlassZumLauf(AnlassArt.LAUF_WARTET.name()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void eineAndereArtZumSelbenLaufIstEinEigenerAnlass() {
    anlassZumLauf(AnlassArt.LAUF_WARTET.name());
    anlassZumLauf(AnlassArt.STOERUNG.name());

    assertThat(anzahl("benachrichtigung_anlass")).isEqualTo(2);
  }

  @Test
  void einZweiterAnlassZumGleichenLabelEreignisWirdAbgewiesen() {
    anlassZumLabel();

    assertThatThrownBy(this::anlassZumLabel).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void eineZweiteZustellungAnDieselbePersonWirdAbgewiesen() {
    long anlass = anlassZumLauf(AnlassArt.UEBERSICHT.name());
    zustellung(anlass, person, ZUSTAND_OFFEN);

    assertThatThrownBy(() -> zustellung(anlass, person, ZUSTAND_OFFEN))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void jedeArtDerDomaeneWirdAngenommen() {
    for (AnlassArt art : AnlassArt.values()) {
      anlassZumLauf(art.name());
    }

    assertThat(anzahl("benachrichtigung_anlass")).isEqualTo(AnlassArt.values().length);
  }

  @Test
  void eineUnbekannteArtWirdAbgewiesen() {
    assertThatThrownBy(() -> anlassZumLauf("VIELLEICHT"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void dieVierZustaendeWerdenAngenommenEinUnbekannterNicht() {
    long anlass = anlassZumLauf(AnlassArt.STOERUNG.name());
    zustellung(anlass, owner, ZUSTAND_OFFEN);
    zustellung(anlass, person, "EINGEPLANT");
    zustellung(anlass, konto("c@example.com"), "ENTFALLEN");
    zustellung(anlass, konto("d@example.com"), "ZUGESTELLT");
    long unbekannt = konto("e@example.com");

    assertThatThrownBy(() -> zustellung(anlass, unbekannt, "VERLOREN"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void derSchalterJePersonIstJeProjektEinmalig() {
    schalterDerPerson();

    assertThatThrownBy(this::schalterDerPerson).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void mitDerKarteVerschwindenAnlassUndZustellung() {
    zustellung(anlassZurKarte(), person, ZUSTAND_OFFEN);

    jdbc.update("DELETE FROM card WHERE id = ?", karte);

    assertThat(anzahl("benachrichtigung_anlass")).isZero();
    assertThat(anzahl("benachrichtigung_zustellung")).isZero();
  }

  @Test
  void mitDemProjektVerschwindenSchalterAnlassUndZustellung() {
    schalterDesProjekts();
    schalterDerPerson();
    zustellung(anlassZumLauf(AnlassArt.STOERUNG.name()), person, ZUSTAND_OFFEN);

    jdbc.update("DELETE FROM project WHERE id = ?", projekt);

    assertThat(anzahl("benachrichtigung_projekt")).isZero();
    assertThat(anzahl("benachrichtigung_person")).isZero();
    assertThat(anzahl("benachrichtigung_anlass")).isZero();
    assertThat(anzahl("benachrichtigung_zustellung")).isZero();
  }

  @Test
  void mitDerPersonVerschwindenSchalterRuhezeitUndIhreZustellungDerAnlassBleibt() {
    schalterDerPerson();
    jdbc.update(
        "INSERT INTO benachrichtigung_ruhezeit (user_id, beginn, ende, zeitzone)"
            + " VALUES (?, '22:00', '07:00', 'Europe/Berlin')",
        person);
    long anlass = anlassZumLauf(AnlassArt.STOERUNG.name());
    zustellung(anlass, person, ZUSTAND_OFFEN);
    zustellung(anlass, owner, ZUSTAND_OFFEN);

    jdbc.update("DELETE FROM app_user WHERE id = ?", person);

    assertThat(anzahl("benachrichtigung_person")).isZero();
    assertThat(anzahl("benachrichtigung_ruhezeit")).isZero();
    assertThat(anzahl("benachrichtigung_zustellung")).isEqualTo(1);
    assertThat(anzahl("benachrichtigung_anlass")).isEqualTo(1);
  }

  @Test
  void mitDemLaufVerschwindenAnlassUndZustellung() {
    zustellung(anlassZumLauf(AnlassArt.LAUF_WARTET.name()), person, ZUSTAND_OFFEN);

    jdbc.update("DELETE FROM night_run WHERE id = ?", lauf);

    assertThat(anzahl("benachrichtigung_anlass")).isZero();
    assertThat(anzahl("benachrichtigung_zustellung")).isZero();
  }

  @Test
  void mitDemLabelEreignisVerschwindenAnlassUndZustellung() {
    zustellung(anlassZumLabel(), person, ZUSTAND_OFFEN);

    jdbc.update("DELETE FROM card_label_event WHERE id = ?", labelEreignis);

    assertThat(anzahl("benachrichtigung_anlass")).isZero();
    assertThat(anzahl("benachrichtigung_zustellung")).isZero();
  }

  private long konto(String email) {
    return id("INSERT INTO app_user (email, display_name) VALUES (?, 'N') RETURNING id", email);
  }

  private long anlassZumLauf(String art) {
    return id(
        "INSERT INTO benachrichtigung_anlass (art, project_id, lauf_id, eingetreten_at,"
            + " angelegt_at) VALUES (?, ?, ?, now(), now()) RETURNING id",
        art,
        projekt,
        lauf);
  }

  private long anlassZumLabel() {
    return id(
        "INSERT INTO benachrichtigung_anlass (art, project_id, label_event_id, card_id,"
            + " eingetreten_at, angelegt_at) VALUES ('KARTE_FRAGE', ?, ?, ?, now(), now())"
            + " RETURNING id",
        projekt,
        labelEreignis,
        karte);
  }

  private long anlassZurKarte() {
    return id(
        "INSERT INTO benachrichtigung_anlass (art, project_id, card_id, eingetreten_at,"
            + " angelegt_at) VALUES ('KARTE_GESCHUETZT', ?, ?, now(), now()) RETURNING id",
        projekt,
        karte);
  }

  private void zustellung(long anlass, long empfaenger, String zustand) {
    jdbc.update(
        "INSERT INTO benachrichtigung_zustellung (anlass_id, user_id, faellig_ab, verschoben,"
            + " zustand) VALUES (?, ?, now(), false, ?)",
        anlass,
        empfaenger,
        zustand);
  }

  private void schalterDesProjekts() {
    jdbc.update(
        "INSERT INTO benachrichtigung_projekt (project_id, eingeschaltet_seit)"
            + " VALUES (?, now())",
        projekt);
  }

  private void schalterDerPerson() {
    jdbc.update(
        "INSERT INTO benachrichtigung_person (user_id, project_id, eingeschaltet_seit)"
            + " VALUES (?, ?, now())",
        person,
        projekt);
  }

  private int anzahl(String tabelle) {
    Integer anzahl = jdbc.queryForObject("SELECT count(*) FROM " + tabelle, Integer.class);
    return anzahl == null ? 0 : anzahl;
  }

  private long id(String sql, Object... argumente) {
    Long generated = jdbc.queryForObject(sql, Long.class, argumente);
    return generated == null ? 0L : generated;
  }
}
