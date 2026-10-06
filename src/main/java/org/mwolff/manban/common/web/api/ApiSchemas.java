package org.mwolff.manban.common.web.api;

/**
 * Verweise auf gemeinsame Schemas der API-Beschreibung (Issue #1402, Plan #1400). Das Fehlerformat
 * ist einmal beschrieben; Controller verweisen in ihren Fehlerantworten nur darauf.
 */
public final class ApiSchemas {

  /** Name des gemeinsamen Fehlerschemas (RFC 9457 Problem Details mit {@code fieldErrors}). */
  public static final String PROBLEM_DETAIL = "ProblemDetail";

  /** Verweis auf das Fehlerschema, etwa für {@code @Schema(ref = …)}. */
  public static final String PROBLEM_DETAIL_REF = "#/components/schemas/" + PROBLEM_DETAIL;

  /** Medientyp der Fehlerantworten. */
  public static final String PROBLEM_JSON = "application/problem+json";

  private ApiSchemas() {}
}
