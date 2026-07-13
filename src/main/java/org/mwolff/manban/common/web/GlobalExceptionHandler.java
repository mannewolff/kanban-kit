package org.mwolff.manban.common.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Globaler Fehler-Handler (Issue #0081): mappt alle Fehler auf RFC-9457 Problem Details ({@code
 * application/problem+json}) und ist damit die einzige Stelle für Fehler-Mapping (CLAUDE-java.md
 * §6.3).
 *
 * <ul>
 *   <li>{@code @ResponseStatus}-annotierte Domänenexceptions behalten ihren Statuscode (generischer
 *       Annotation-Lookup statt Einzel-Handler je Exception); als {@code detail} dient die
 *       kuratierte Exception-Message bzw. der {@code reason} der Annotation.
 *   <li>Bean-Validation-Fehler ({@link MethodArgumentNotValidException}) ergeben 400 mit einer
 *       {@code fieldErrors}-Extension (Feld → Meldung).
 *   <li>Unerwartete Exceptions ergeben 500 mit generischem {@code detail} — keine Stacktraces oder
 *       internen Details nach außen (CLAUDE-security.md Grundprinzip 6); intern wird geloggt.
 *   <li>Framework-Fehler (405, 406, unlesbarer Body, …) behandelt die Basisklasse {@link
 *       ResponseEntityExceptionHandler} mit unveränderten Statuscodes ebenfalls als Problem
 *       Details.
 * </ul>
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Generisches {@code detail} für unerwartete Fehler — bewusst ohne interne Details. */
  static final String INTERNAL_ERROR_DETAIL = "Interner Fehler. Bitte später erneut versuchen.";

  /** {@code detail} für Bean-Validation-Fehler; Einzelheiten stehen in {@code fieldErrors}. */
  static final String VALIDATION_DETAIL = "Validierung fehlgeschlagen";

  /** Fallback-Meldung für Feldfehler ohne eigene Message. */
  static final String FIELD_ERROR_FALLBACK = "Ungültiger Wert";

  /**
   * Mappt {@code @ResponseStatus}-annotierte Domänenexceptions generisch auf ihren annotierten
   * Statuscode; alles Unannotierte wird als unerwarteter Fehler mit 500 beantwortet.
   */
  @ExceptionHandler(Exception.class)
  ProblemDetail handleException(Exception ex) {
    ResponseStatus responseStatus =
        AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
    if (responseStatus == null) {
      LOG.error("Unerwarteter Fehler", ex);
      return ProblemDetail.forStatusAndDetail(
          HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_DETAIL);
    }
    HttpStatus status = responseStatus.code();
    return ProblemDetail.forStatusAndDetail(status, detailOf(ex, responseStatus, status));
  }

  /** Bean-Validation-Fehler: 400 plus {@code fieldErrors}-Extension (Feld → Meldung). */
  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, VALIDATION_DETAIL);
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      String message = fieldError.getDefaultMessage();
      fieldErrors.putIfAbsent(
          fieldError.getField(), message == null ? FIELD_ERROR_FALLBACK : message);
    }
    problem.setProperty("fieldErrors", fieldErrors);
    return ResponseEntity.status(status).headers(headers).body(problem);
  }

  /** Kuratiertes {@code detail}: Annotation-{@code reason}, sonst Message, sonst Status-Phrase. */
  private static String detailOf(Exception ex, ResponseStatus responseStatus, HttpStatus status) {
    if (!responseStatus.reason().isEmpty()) {
      return responseStatus.reason();
    }
    @Nullable String message = ex.getMessage();
    return message == null ? status.getReasonPhrase() : message;
  }
}
