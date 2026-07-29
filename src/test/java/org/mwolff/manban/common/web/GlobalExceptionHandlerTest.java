package org.mwolff.manban.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.kanbancompat.application.TokenNotBoundException;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Tests des globalen Fehler-Handlers (Issue #0081): Statuscodes bleiben wie annotiert, Bodies sind
 * RFC-9457 Problem Details, Validierungsfehler tragen eine {@code fieldErrors}-Extension, und
 * unerwartete Fehler geben keine internen Details preis.
 */
class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
    mvc =
        MockMvcBuilders.standaloneSetup(new ProbeController()).setControllerAdvice(handler).build();
  }

  // --- Generischer Annotation-Lookup (@ResponseStatus der Domänenexceptions) -----------------

  @Test
  void domainExceptionKeepsAnnotatedStatusAsProblemJson() throws Exception {
    mvc.perform(get("/probe/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Not Found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("Karte nicht gefunden"));
  }

  @Test
  void forbiddenDomainExceptionMapsTo403WithMessageAsDetail() {
    // When
    ProblemDetail problem = handler.handleException(new ProjectAccessDeniedException());

    // Then
    assertThat(problem.getStatus()).isEqualTo(403);
    assertThat(problem.getTitle()).isEqualTo("Forbidden");
    assertThat(problem.getDetail()).isEqualTo("Keine Berechtigung für diese Projekt-Aktion");
  }

  @Test
  void annotationReasonBecomesDetailWhenExceptionHasNoMessage() {
    // When (TokenNotBoundException trägt reason statt Message)
    ProblemDetail problem = handler.handleException(new TokenNotBoundException());

    // Then
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail())
        .isEqualTo(
            "Token ist an kein Board gebunden."
                + " Bitte ein board-gebundenes Kanban-Token verwenden.");
  }

  @Test
  void annotatedExceptionWithoutMessageFallsBackToReasonPhrase() {
    // When
    ProblemDetail problem = handler.handleException(new AnnotatedWithoutMessageException());

    // Then
    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getDetail()).isEqualTo("Bad Request");
  }

  // --- Unerwartete Fehler: 500 ohne interne Details ------------------------------------------

  @Test
  void unexpectedExceptionMapsTo500WithGenericDetail() throws Exception {
    mvc.perform(get("/probe/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Internal Server Error"))
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.INTERNAL_ERROR_DETAIL));
  }

  @Test
  void unexpectedExceptionDoesNotLeakExceptionMessage() {
    // When
    ProblemDetail problem = handler.handleException(new IllegalStateException("geheimer Zustand"));

    // Then
    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).doesNotContain("geheimer Zustand");
  }

  // --- Constraint-Verletzungen: Unique → 409, alles andere → 500 (Issue #496) ------------------

  @Test
  void uniqueViolationMapsTo409AsProblemJson() throws Exception {
    mvc.perform(get("/probe/unique-violation"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.CONFLICT_DETAIL));
  }

  @Test
  void uniqueViolationDeepInTheCauseChainIsStillRecognized() {
    // Given: Kette wie auf dem JPA-Pfad (Spring → ORM-Wrapper → SQLException)
    var exception =
        new DataIntegrityViolationException(
            "could not execute statement",
            new IllegalStateException(
                "constraint [uq_card_number]", new SQLException("duplicate key", "23505")));

    // When
    ProblemDetail problem = handler.handleDataIntegrityViolation(exception);

    // Then
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.CONFLICT_DETAIL);
  }

  @Test
  void uniqueViolationDetailLeaksNoConstraintName() {
    // When
    ProblemDetail problem =
        handler.handleDataIntegrityViolation(
            new DataIntegrityViolationException(
                "insert into card …",
                new SQLException(
                    "duplicate key value violates unique constraint \"uq_card_number\"", "23505")));

    // Then
    assertThat(problem.getDetail()).doesNotContain("uq_card_number", "constraint");
  }

  @Test
  void notNullViolationStaysAt500() {
    // When: 23502 = not_null_violation — ein Programmfehler, kein wiederholbarer Konflikt
    ProblemDetail problem =
        handler.handleDataIntegrityViolation(
            new DataIntegrityViolationException(
                "null value in column", new SQLException("null value", "23502")));

    // Then
    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.INTERNAL_ERROR_DETAIL);
  }

  @Test
  void integrityViolationWithoutSqlExceptionStaysAt500() {
    // When
    ProblemDetail problem =
        handler.handleDataIntegrityViolation(new DataIntegrityViolationException("ohne Ursache"));

    // Then
    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.INTERNAL_ERROR_DETAIL);
  }

  // --- Bean-Validation: 400 + fieldErrors-Extension -------------------------------------------

  @Test
  void beanValidationErrorMapsTo400WithFieldErrors() throws Exception {
    mvc.perform(post("/probe/validated").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.VALIDATION_DETAIL))
        .andExpect(jsonPath("$.fieldErrors.title").isNotEmpty());
  }

  @Test
  void fieldErrorsKeepFirstMessagePerFieldAndFallBackWithoutMessage() throws Exception {
    // Given: doppelter Feldfehler (erste Meldung gewinnt) + Feldfehler ohne Default-Message
    var binding = new BeanPropertyBindingResult(new Object(), "request");
    binding.addError(new FieldError("request", "title", "darf nicht leer sein"));
    binding.addError(new FieldError("request", "title", "zweite Meldung"));
    binding.addError(new FieldError("request", "name", null, false, null, null, null));
    var exception =
        new MethodArgumentNotValidException(
            new MethodParameter(Object.class.getMethod("equals", Object.class), 0), binding);

    // When
    ResponseEntity<Object> response =
        handler.handleMethodArgumentNotValid(
            exception,
            new HttpHeaders(),
            HttpStatus.BAD_REQUEST,
            new ServletWebRequest(new MockHttpServletRequest()));

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    ProblemDetail problem = (ProblemDetail) response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getDetail()).isEqualTo(GlobalExceptionHandler.VALIDATION_DETAIL);
    assertThat(problem.getProperties())
        .containsEntry(
            "fieldErrors",
            Map.of(
                "title",
                "darf nicht leer sein",
                "name",
                GlobalExceptionHandler.FIELD_ERROR_FALLBACK));
  }

  // --- Test-Fixtures ---------------------------------------------------------------------------

  /** Annotierte Exception ohne Message und ohne reason (Fallback auf die Status-Phrase). */
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  static class AnnotatedWithoutMessageException extends RuntimeException {}

  /** Minimaler Controller, um den Advice über den echten MVC-Pfad auszulösen. */
  @RestController
  static class ProbeController {

    @GetMapping("/probe/not-found")
    String notFound() {
      throw new CardNotFoundException();
    }

    @GetMapping("/probe/unexpected")
    String unexpected() {
      throw new IllegalStateException("geheim: interner Zustand");
    }

    @GetMapping("/probe/unique-violation")
    String uniqueViolation() {
      throw new DataIntegrityViolationException(
          "insert into card …",
          new SQLException("duplicate key value violates unique constraint", "23505"));
    }

    @PostMapping("/probe/validated")
    String validated(@Valid @RequestBody ProbeRequest request) {
      return request.title();
    }

    record ProbeRequest(@NotBlank String title) {}
  }
}
