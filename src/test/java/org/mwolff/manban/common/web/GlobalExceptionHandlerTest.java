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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.common.FieldScopedException;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.Texte;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.mwolff.manban.config.SpracheLocaleConfig;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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

  private static final String INTERN_DE = "Interner Fehler. Bitte später erneut versuchen.";
  private static final String INTERN_EN = "Internal error. Please try again later.";
  private static final String VALIDIERUNG_DE = "Validierung fehlgeschlagen";
  private static final String VALIDIERUNG_EN = "Validation failed";
  private static final String UNGUELTIG_DE = "Ungültiger Wert";
  private static final String UNGUELTIG_EN = "Invalid value";
  private static final String KONFLIKT_DE =
      "Die Anfrage kollidierte mit dem aktuellen Datenstand. Bitte erneut versuchen.";
  private static final String KONFLIKT_EN =
      "The request conflicted with the current state of the data. Please try again.";

  private GlobalExceptionHandler handler;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    eingestellt(Sprache.DE);
  }

  @AfterEach
  void tearDown() {
    SprachFixture.abmelden();
  }

  /** Handler und MockMvc bei eingestellter Sprache {@code sprache}. */
  private void eingestellt(Sprache sprache) {
    aufbauen(SprachFixture.mit(sprache));
  }

  private void aufbauen(Texte texte) {
    handler = new GlobalExceptionHandler(texte);
    mvc =
        MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(handler)
            .setLocaleResolver(new SpracheLocaleConfig().localeResolver(texte))
            .build();
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
    // When (die Fixture trägt reason statt Message)
    ProblemDetail problem = handler.handleException(new AnnotatedWithReasonException());

    // Then
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getDetail()).isEqualTo("Grund aus der Annotation");
  }

  @Test
  void annotatedExceptionWithoutMessageFallsBackToReasonPhrase() {
    // When
    ProblemDetail problem = handler.handleException(new AnnotatedWithoutMessageException());

    // Then
    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getDetail()).isEqualTo("Bad Request");
  }

  // --- Problem-Typ aus MitProblemTyp (Issue #1918, Plan #1914 E6) ----------------------------

  @Test
  void vorschauVerborgenErgibt403MitProblemTyp() throws Exception {
    mvc.perform(get("/probe/vorschau-verborgen"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("vorschau-verborgen"))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(
            jsonPath("$.detail")
                .value("Die Person sieht diesen Bereich, die Vorschau zeigt ihn aber nicht."));
  }

  @Test
  void exceptionOhneProblemTypBehaeltIhrBisherigesFormat() {
    // When
    ProblemDetail problem = handler.handleException(new ProjectAccessDeniedException());

    // Then
    assertThat(problem.getType()).hasToString("about:blank");
    assertThat(problem.getStatus()).isEqualTo(403);
    assertThat(problem.getDetail()).isEqualTo("Keine Berechtigung für diese Projekt-Aktion");
  }

  // --- Unerwartete Fehler: 500 ohne interne Details ------------------------------------------

  @Test
  void unexpectedExceptionMapsTo500WithGenericDetail() throws Exception {
    mvc.perform(get("/probe/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Internal Server Error"))
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.detail").value(INTERN_DE));
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
        .andExpect(jsonPath("$.detail").value(KONFLIKT_DE));
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
    assertThat(problem.getDetail()).isEqualTo(KONFLIKT_DE);
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
    assertThat(problem.getDetail()).isEqualTo(INTERN_DE);
  }

  @Test
  void integrityViolationWithoutSqlExceptionStaysAt500() {
    // When
    ProblemDetail problem =
        handler.handleDataIntegrityViolation(new DataIntegrityViolationException("ohne Ursache"));

    // Then
    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getDetail()).isEqualTo(INTERN_DE);
  }

  // --- Bean-Validation: 400 + fieldErrors-Extension -------------------------------------------

  @Test
  void beanValidationErrorMapsTo400WithFieldErrors() throws Exception {
    mvc.perform(post("/probe/validated").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value(VALIDIERUNG_DE))
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
    var response =
        handler.handleMethodArgumentNotValid(
            exception,
            new HttpHeaders(),
            HttpStatus.BAD_REQUEST,
            new ServletWebRequest(new MockHttpServletRequest()));

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    ProblemDetail problem = (ProblemDetail) response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getDetail()).isEqualTo(VALIDIERUNG_DE);
    assertThat(problem.getProperties())
        .containsEntry(
            "fieldErrors", Map.of("title", "darf nicht leer sein", "name", UNGUELTIG_DE));
  }

  // --- Sprache nach Zugangsart (Issue #1640, Plan #1638 A5) -----------------------------------

  @Test
  void englischFuerSession() {
    // Given
    eingestellt(Sprache.EN);
    SprachFixture.angemeldetMit("ROLE_USER");

    // When / Then
    assertThat(handler.handleException(new IllegalStateException("x")).getDetail())
        .isEqualTo(INTERN_EN);
    assertThat(handler.handleException(new UebersetzteKollision()).getDetail())
        .isEqualTo(KONFLIKT_EN);
  }

  @Test
  void englischFuerAnonym() throws Exception {
    eingestellt(Sprache.EN);

    mvc.perform(get("/probe/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value(INTERN_EN));
    mvc.perform(get("/probe/uebersetzt"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(KONFLIKT_EN));
  }

  @Test
  void deutschFuerTokenBeiEnglisch() {
    // Given
    eingestellt(Sprache.EN);
    SprachFixture.angemeldetMit("AUTH_PAT");

    // When / Then
    assertThat(handler.handleException(new UebersetzteKollision()).getDetail())
        .isEqualTo(KONFLIKT_DE);
    assertThat(handler.handleException(new IllegalStateException("x")).getDetail())
        .isEqualTo(INTERN_DE);
  }

  @Test
  void deutschFuerUngebundenesTokenBeiEnglisch() {
    // Given
    eingestellt(Sprache.EN);
    SprachFixture.angemeldetMit("AUTH_PAT_UNBOUND");

    // When / Then
    assertThat(handler.handleException(new UebersetzteKollision()).getDetail())
        .isEqualTo(KONFLIKT_DE);
    assertThat(
            handler
                .handleDataIntegrityViolation(
                    new DataIntegrityViolationException(
                        "dup", new SQLException("duplicate key", "23505")))
                .getDetail())
        .isEqualTo(KONFLIKT_DE);
  }

  @Test
  void uebersetzbareMeldungFuelltAuchDieFeldmeldung() {
    // Given
    eingestellt(Sprache.EN);

    // When
    ProblemDetail problem = handler.handleException(new UebersetzteFeldmeldung());

    // Then
    assertThat(problem.getDetail()).isEqualTo(UNGUELTIG_EN);
    assertThat(problem.getProperties()).containsEntry("fieldErrors", Map.of("title", UNGUELTIG_EN));
  }

  @Test
  void andereExceptionBehaeltIhreMeldungAuchBeiEnglisch() {
    eingestellt(Sprache.EN);

    assertThat(handler.handleException(new AnnotatedWithMessageException()).getDetail())
        .isEqualTo("Deutsche Meldung ohne Schlüssel");
  }

  @Test
  void konfliktUndIntegritaetsverstossAufEnglisch() {
    eingestellt(Sprache.EN);

    assertThat(
            handler
                .handleDataIntegrityViolation(
                    new DataIntegrityViolationException(
                        "dup", new SQLException("duplicate key", "23505")))
                .getDetail())
        .isEqualTo(KONFLIKT_EN);
    assertThat(
            handler
                .handleDataIntegrityViolation(new DataIntegrityViolationException("ohne Ursache"))
                .getDetail())
        .isEqualTo(INTERN_EN);
  }

  @Test
  void feldmeldungFolgtDerEinstellungStattAcceptLanguage() throws Exception {
    eingestellt(Sprache.EN);
    mvc.perform(
            post("/probe/validated")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "de-DE")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(VALIDIERUNG_EN))
        .andExpect(jsonPath("$.fieldErrors.title").value("must not be blank"));

    eingestellt(Sprache.DE);
    mvc.perform(
            post("/probe/validated")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(VALIDIERUNG_DE))
        .andExpect(jsonPath("$.fieldErrors.title").value("darf nicht leer sein"));
  }

  @Test
  void feldfehlerOhneMeldungFaelltAufEnglischenRueckfall() throws Exception {
    // Given
    eingestellt(Sprache.EN);
    var binding = new BeanPropertyBindingResult(new Object(), "request");
    binding.addError(new FieldError("request", "name", null, false, null, null, null));
    var exception =
        new MethodArgumentNotValidException(
            new MethodParameter(Object.class.getMethod("equals", Object.class), 0), binding);

    // When
    var response =
        handler.handleMethodArgumentNotValid(
            exception,
            new HttpHeaders(),
            HttpStatus.BAD_REQUEST,
            new ServletWebRequest(new MockHttpServletRequest()));

    // Then
    ProblemDetail problem = (ProblemDetail) response.getBody();
    assertThat(problem).isNotNull();
    assertThat(problem.getDetail()).isEqualTo(VALIDIERUNG_EN);
    assertThat(problem.getProperties()).containsEntry("fieldErrors", Map.of("name", UNGUELTIG_EN));
  }

  @Test
  void fehler500MitScheiternderSprachabfrageTraegtDeutschesDetail() throws Exception {
    // Given: Die Spracheinstellung ist nicht lesbar (Datenbank weg)
    aufbauen(SprachFixture.mitScheiternderQuelle());

    // When / Then
    mvc.perform(get("/probe/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value(INTERN_DE));
  }

  // --- Test-Fixtures ---------------------------------------------------------------------------

  /** Übersetzbare Domänenexception mit Statuscode, deutsch wie {@code messages_de}. */
  @ResponseStatus(HttpStatus.CONFLICT)
  static class UebersetzteKollision extends RuntimeException implements UebersetzbareMeldung {

    UebersetzteKollision() {
      super(KONFLIKT_DE);
    }

    @Override
    public String schluessel() {
      return "common.fehler.konflikt";
    }
  }

  /** Übersetzbare feldbezogene Domänenexception. */
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  static class UebersetzteFeldmeldung extends RuntimeException
      implements UebersetzbareMeldung, FieldScopedException {

    UebersetzteFeldmeldung() {
      super(UNGUELTIG_DE);
    }

    @Override
    public String schluessel() {
      return "common.fehler.ungueltiger-wert";
    }

    @Override
    public String field() {
      return "title";
    }
  }

  /** Annotierte Exception mit reason und ohne Message. */
  @ResponseStatus(value = HttpStatus.CONFLICT, reason = "Grund aus der Annotation")
  static class AnnotatedWithReasonException extends RuntimeException {}

  /** Annotierte Exception mit fester Message, aber ohne Meldungsschlüssel. */
  @ResponseStatus(HttpStatus.FORBIDDEN)
  static class AnnotatedWithMessageException extends RuntimeException {

    AnnotatedWithMessageException() {
      super("Deutsche Meldung ohne Schlüssel");
    }
  }

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

    @GetMapping("/probe/uebersetzt")
    String uebersetzt() {
      throw new UebersetzteKollision();
    }

    @GetMapping("/probe/vorschau-verborgen")
    String vorschauVerborgen() {
      // Voll qualifiziert: Ein weiterer Import risse die PMD-Grenze ExcessiveImports (40).
      throw new org.mwolff.manban.auth.application.VorschauVerborgenException();
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
