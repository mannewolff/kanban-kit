package org.mwolff.manban.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Belegt gegen echtes PostgreSQL, dass der {@link GlobalExceptionHandler} die beiden
 * Race-Constraints der Karten ({@code uq_card_number}, {@code uq_card_active_position}) als 409
 * beantwortet und Integritätsverstöße ohne Wiederholungsaussicht (NOT NULL, Fremdschlüssel) weiter
 * als 500 (Issue #496).
 *
 * <p>Der Test provoziert die Kollisionen deterministisch über direkte Inserts statt über
 * konkurrierende Requests: Gegenstand des Issues ist das Fehler-Mapping, nicht die Beseitigung des
 * Rennens (das ist Issue #499). Entscheidend ist, dass die Exception mitsamt ihrer echten
 * Ursachenkette aus Treiber und Persistenzschicht stammt — genau daraus liest der Handler den
 * SQLState.
 */
// Annotationen bewusst identisch zu den übrigen ITs: nur bei gleicher Kontext-Konfiguration greift
// das Spring-Context-Caching. Ein abweichender Kontext startet einen zweiten HikariCP-Pool und
// sprengt das Verbindungslimit des geteilten Postgres-Containers ("too many clients already").
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ConstraintViolationProblemIT extends AbstractIntegrationTest {

  @Autowired private GlobalExceptionHandler handler;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AppUserRepository users;

  private long boardId;
  private long columnId;

  /** Ein Board mit einer Spalte und einer Karte (Nummer 1, Position 0) als Kollisionspartner. */
  @BeforeEach
  void seedBoardWithOneCard() {
    long userId =
        id(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('konflikt@example.com', 'x', 'K') RETURNING id");
    long projectId =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    boardId =
        id("INSERT INTO board (project_id, name) VALUES (" + projectId + ", 'B') RETURNING id");
    columnId =
        id(
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + boardId
                + ", 'Backlog', 0) RETURNING id");
    insertCard(1, "Erste Karte", 0);
  }

  @Test
  void duplicateCardNumberYieldsConflict() {
    // When: zweite Karte mit derselben Projektnummer (uq_card_number), freie Position
    ProblemDetail problem = handler.handleDataIntegrityViolation(insertFailing(1, "Duplikat", 1));

    // Then
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.CONFLICT_DETAIL);
  }

  @Test
  void duplicateActivePositionYieldsConflict() {
    // When: zweite Karte auf derselben aktiven Position (uq_card_active_position), freie Nummer
    ProblemDetail problem = handler.handleDataIntegrityViolation(insertFailing(2, "Duplikat", 0));

    // Then
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.CONFLICT_DETAIL);
  }

  @Test
  void uniqueViolationOnTheJpaPathIsRecognizedToo() {
    // Given: derselbe Fehler über Hibernate statt über JdbcTemplate — die Ursachenkette ist eine
    // andere (ORM-Wrapper zwischen Spring-Exception und SQLException). Der Kartenanlage-Pfad in
    // Produktion läuft über JPA, dieser Nachweis gehört also dazu.
    var thrown =
        catchThrowableOfType(
            DataIntegrityViolationException.class,
            () ->
                users.save(
                    new AppUser(
                        null, "konflikt@example.com", "y", "Doppelt", true, PlatformRole.USER)));
    assertThat(thrown).isNotNull();

    // When
    ProblemDetail problem = handler.handleDataIntegrityViolation(thrown);

    // Then
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.CONFLICT_DETAIL);
  }

  @Test
  void conflictDetailLeaksNoConstraintName() {
    // When
    ProblemDetail problem = handler.handleDataIntegrityViolation(insertFailing(1, "Duplikat", 1));

    // Then: keine Constraint-Namen, keine Treibermeldung nach außen
    assertThat(problem.getDetail()).doesNotContain("uq_card_number", "constraint", "card");
  }

  @Test
  void notNullViolationStaysInternalError() {
    // Given: Insert ohne title (NOT NULL) — ein Programmfehler, kein wiederholbarer Konflikt
    var thrown =
        catchThrowableOfType(
            DataIntegrityViolationException.class,
            () ->
                jdbc.update(
                    "INSERT INTO card (board_id, column_id, number, position_in_column)"
                        + " VALUES (?, ?, ?, ?)",
                    boardId,
                    columnId,
                    3,
                    2));
    assertThat(thrown).isNotNull();

    // When
    ProblemDetail problem = handler.handleDataIntegrityViolation(thrown);

    // Then
    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.INTERNAL_ERROR_DETAIL);
  }

  @Test
  void foreignKeyViolationStaysInternalError() {
    // Given: Verweis auf eine nicht existierende Spalte
    var thrown =
        catchThrowableOfType(
            DataIntegrityViolationException.class,
            () ->
                jdbc.update(
                    "INSERT INTO card (board_id, column_id, number, title, position_in_column)"
                        + " VALUES (?, ?, ?, ?, ?)",
                    boardId,
                    columnId + 9999,
                    4,
                    "Karte ohne Spalte",
                    3));
    assertThat(thrown).isNotNull();

    // When
    ProblemDetail problem = handler.handleDataIntegrityViolation(thrown);

    // Then
    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.INTERNAL_ERROR_DETAIL);
  }

  /** Führt den kollidierenden Insert aus und liefert die dabei geworfene Exception. */
  private DataIntegrityViolationException insertFailing(int number, String title, int position) {
    var thrown =
        catchThrowableOfType(
            DataIntegrityViolationException.class, () -> insertCard(number, title, position));
    assertThat(thrown).isNotNull();
    return thrown;
  }

  private void insertCard(int number, String title, int position) {
    jdbc.update(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column)"
            + " VALUES (?, ?, ?, ?, ?)",
        boardId,
        columnId,
        number,
        title,
        position);
  }

  private long id(String sql) {
    Long generated = jdbc.queryForObject(sql, Long.class);
    return generated == null ? 0L : generated;
  }
}
