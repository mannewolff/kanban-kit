package org.mwolff.manban.accesstoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V80__app_zugang_je_person.sql} (Issue #2212, Plan #2206 E4): Je Person und
 * Board kann nur ein wirksamer Zugang der App bestehen; verschiedene Personen halten am selben
 * Board je einen.
 *
 * <p>Eigener Container, um bis {@code V79} zu migrieren, dort einen Bestand anzulegen und ihn über
 * die Migration zu heben.
 */
class AppZugangJePersonSchemaIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.15");

  private static final String INDEX_APP_PERSON = "uq_kanban_access_token_app_person";

  private static JdbcTemplate jdbc;
  private static long admin;
  private static long kunde;
  private static long projekt;
  private static long bestandsBoard;
  private static long bestandsZugang;

  private long board;

  static {
    POSTGRES.start();
  }

  @BeforeAll
  static void migriereMitBestand() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    migriereBis(dataSource, "79");
    admin = id("INSERT INTO app_user (email, display_name) VALUES ('a@example.com', 'A')");
    kunde = id("INSERT INTO app_user (email, display_name) VALUES ('k@example.com', 'K')");
    projekt = id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?)", admin);
    bestandsBoard = neuesBoard();
    bestandsZugang = zugang(bestandsBoard, admin);
    migriereBis(dataSource, "80");
  }

  @BeforeEach
  void legeBoardAn() {
    board = neuesBoard();
  }

  @Test
  void derBestandBleibtWirksam() {
    assertThat(
            jdbc.queryForObject(
                "SELECT revoked FROM kanban_access_token WHERE id = ?",
                Boolean.class,
                bestandsZugang))
        .isFalse();
  }

  @Test
  void zweiPersonenHaltenAmSelbenBoardJeEinenZugang() {
    zugang(board, admin);

    zugang(board, kunde);

    assertThat(wirksameAmBoard(board)).isEqualTo(2);
  }

  @Test
  void einZweiterWirksamerZugangDerselbenPersonVerletztDenIndex() {
    zugang(board, kunde);

    assertThatThrownBy(() -> zugang(board, kunde))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining(INDEX_APP_PERSON);
  }

  @Test
  void nachDemWiderrufKannDieselbePersonNeuAnlegen() {
    long erster = zugang(board, kunde);
    jdbc.update("UPDATE kanban_access_token SET revoked = true WHERE id = ?", erster);

    zugang(board, kunde);

    assertThat(wirksameAmBoard(board)).isEqualTo(1);
  }

  @Test
  void dieselbePersonHaeltAnZweiBoardsJeEinenZugang() {
    zugang(board, kunde);
    long anderes = neuesBoard();

    zugang(anderes, kunde);

    assertThat(wirksameAmBoard(anderes)).isEqualTo(1);
  }

  @Test
  void derAlteIndexJeBoardIstEntfallen() {
    Integer anzahl =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_kanban_access_token_app_board'",
            Integer.class);

    assertThat(anzahl).isZero();
  }

  private static void migriereBis(DataSource dataSource, String version) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(version)
        .load()
        .migrate();
  }

  private static long neuesBoard() {
    return id("INSERT INTO board (project_id, name) VALUES (?, 'B')", projekt);
  }

  private static long zugang(long boardId, long userId) {
    return id(
        "INSERT INTO kanban_access_token (user_id, project_id, board_id, name, token_hash, art)"
            + " VALUES (?, ?, ?, 'Z', ?, 'APP')",
        userId,
        projekt,
        boardId,
        UUID.randomUUID().toString());
  }

  private int wirksameAmBoard(long boardId) {
    Integer anzahl =
        jdbc.queryForObject(
            "SELECT count(*) FROM kanban_access_token"
                + " WHERE board_id = ? AND art = 'APP' AND revoked = false",
            Integer.class,
            boardId);
    return anzahl == null ? 0 : anzahl;
  }

  private static long id(String insert, Object... argumente) {
    Long generated = jdbc.queryForObject(insert + " RETURNING id", Long.class, argumente);
    return generated == null ? 0L : generated;
  }
}
