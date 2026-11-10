package org.mwolff.manban.accesstoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.domain.AccessTokenArt;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Verifiziert {@code V70__zugang_der_app.sql} (Issue #2102, Plan #2100 E1, E6): Je Board kann nur
 * ein wirksamer Zugang der App bestehen, und die Art bindet ihn an ein Board.
 *
 * <p>Eigener Container, um bis {@code V69} zu migrieren, dort einen Bestand anzulegen und ihn über
 * die Migration zu heben.
 */
class AppZugangSchemaIT {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.15");

  private static final String INDEX_APP_BOARD = "uq_kanban_access_token_app_board";
  private static final String PRUEFUNG_APP_BOARD = "ck_kanban_access_token_app_board";
  private static final String PRUEFUNG_ART = "ck_kanban_access_token_art";

  private static JdbcTemplate jdbc;
  private static long besitzer;
  private static long projekt;
  private static long bestandsToken;

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
    migriereBis(dataSource, "69");
    besitzer = id("INSERT INTO app_user (email, display_name) VALUES ('a@example.com', 'N')");
    projekt = id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?)", besitzer);
    bestandsToken =
        id(
            "INSERT INTO kanban_access_token (user_id, name, token_hash) VALUES (?, 'Alt', 'alt')",
            besitzer);
    migriereBis(dataSource, "70");
  }

  @BeforeEach
  void legeBoardAn() {
    board = neuesBoard();
  }

  @Test
  void bestehendeZeilenTragenStandard() {
    assertThat(
            jdbc.queryForObject(
                "SELECT art FROM kanban_access_token WHERE id = ?", String.class, bestandsToken))
        .isEqualTo(AccessTokenArt.STANDARD.name());
  }

  @Test
  void einZweiterWirksamerAppZugangAmSelbenBoardVerletztDenIndex() {
    zugang(board, AccessTokenArt.APP.name());

    assertThatThrownBy(() -> zugang(board, AccessTokenArt.APP.name()))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining(INDEX_APP_BOARD);
  }

  @Test
  void nachDemWiderrufDesErstenIstEinNeuerAppZugangMoeglich() {
    long erster = zugang(board, AccessTokenArt.APP.name());
    jdbc.update("UPDATE kanban_access_token SET revoked = true WHERE id = ?", erster);

    zugang(board, AccessTokenArt.APP.name());

    assertThat(anzahlAmBoard(board)).isEqualTo(2);
  }

  @Test
  void appZugaengeVerschiedenerBoardsBehindernSichNicht() {
    zugang(board, AccessTokenArt.APP.name());
    long anderes = neuesBoard();

    zugang(anderes, AccessTokenArt.APP.name());

    assertThat(anzahlAmBoard(anderes)).isEqualTo(1);
  }

  @Test
  void einAppZugangNebenGewoehnlichenTokensAmSelbenBoardIstMoeglich() {
    zugang(board, AccessTokenArt.STANDARD.name());

    zugang(board, AccessTokenArt.APP.name());

    assertThat(anzahlAmBoard(board)).isEqualTo(2);
  }

  @Test
  void mehrereGewoehnlicheTokensAmSelbenBoardBleibenMoeglich() {
    zugang(board, AccessTokenArt.STANDARD.name());

    zugang(board, AccessTokenArt.STANDARD.name());

    assertThat(anzahlAmBoard(board)).isEqualTo(2);
  }

  @Test
  void appZugangOhneBoardVerletztDiePruefbedingung() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO kanban_access_token (user_id, name, token_hash, art)"
                        + " VALUES (?, 'App', ?, 'APP')",
                    besitzer,
                    UUID.randomUUID().toString()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(PRUEFUNG_APP_BOARD);
  }

  @Test
  void gewoehnlichesTokenOhneBoardBleibtMoeglich() {
    jdbc.update(
        "INSERT INTO kanban_access_token (user_id, name, token_hash) VALUES (?, 'Frei', ?)",
        besitzer,
        "frei");

    assertThat(
            jdbc.queryForObject(
                "SELECT art FROM kanban_access_token WHERE token_hash = 'frei'", String.class))
        .isEqualTo(AccessTokenArt.STANDARD.name());
  }

  @Test
  void eineUnbekannteArtVerletztDiePruefbedingung() {
    assertThatThrownBy(() -> zugang(board, "FREMD"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(PRUEFUNG_ART);
  }

  private static void migriereBis(DataSource dataSource, String version) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(version)
        .load()
        .migrate();
  }

  private long neuesBoard() {
    return id("INSERT INTO board (project_id, name) VALUES (?, 'B')", projekt);
  }

  private long zugang(long boardId, String art) {
    return id(
        "INSERT INTO kanban_access_token (user_id, project_id, board_id, name, token_hash, art)"
            + " VALUES (?, ?, ?, 'Z', ?, ?)",
        besitzer,
        projekt,
        boardId,
        UUID.randomUUID().toString(),
        art);
  }

  private int anzahlAmBoard(long boardId) {
    Integer anzahl =
        jdbc.queryForObject(
            "SELECT count(*) FROM kanban_access_token WHERE board_id = ?", Integer.class, boardId);
    return anzahl == null ? 0 : anzahl;
  }

  private static long id(String insert, Object... argumente) {
    Long generated = jdbc.queryForObject(insert + " RETURNING id", Long.class, argumente);
    return generated == null ? 0L : generated;
  }
}
