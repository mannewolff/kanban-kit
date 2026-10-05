package org.mwolff.manban.auth.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.auth.application.SessionTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lässt Aufrufe, die aus der API-Übersicht heraus ausprobiert werden, nur für aktive
 * Plattform-Admins durch (Issue #1366, #1438).
 *
 * <p>Die Übersicht kennzeichnet einen ausprobierten Aufruf mit dem Header {@value #HEADER}. Jedes
 * Vorkommen zählt, der Wert ist gleichgültig — auch {@code 0}. Ohne den Header geht die Anfrage
 * unverändert weiter; für gewöhnliche Aufrufe ändert dieser Filter nichts.
 *
 * <ul>
 *   <li>Ohne gültige Session antwortet der Filter 401 — wie der übrige API-Zugang.
 *   <li>Ist der angemeldete Nutzer kein aktiver Plattform-Admin, antwortet er 403 als
 *       ProblemDetail. Gesperrte Konten prüft er selbst, denn er läuft vor dem {@link
 *       DisabledUserGuardFilter}.
 *   <li>Trägt die Anfrage zusätzlich ein Projekt-Token ({@value #PROJEKT_TOKEN_HEADER}), gilt das
 *       Token und nicht die Session: Das Session-Cookie wird aus der weitergereichten Anfrage
 *       entfernt, damit der Aufruf mit genau den Rechten des Tokens läuft.
 * </ul>
 *
 * <p><strong>Grenze:</strong> Das Kennzeichen grenzt allein die Funktion „Ausprobieren“ ab. Die
 * Rechte an den Daten prüfen weiterhin die Endpunkte selbst — ein Admin darf ausprobiert nicht mehr
 * als sonst auch. Wer ohne Kennzeichen aufruft, unterliegt ohnehin seinen eigenen Rechten.
 */
@Component
public class ApiAusprobierFilter extends OncePerRequestFilter {

  /** Kennzeichen eines aus der API-Übersicht ausprobierten Aufrufs. */
  public static final String HEADER = "X-Api-Ausprobieren";

  /**
   * Header des Projekt-Tokens. Gleicher Wert wie {@code PatAuthenticationFilter.HEADER}; als eigene
   * Konstante, weil {@code auth} das Modul {@code accesstoken} nicht kennen darf (Issue #438).
   */
  static final String PROJEKT_TOKEN_HEADER = "X-Kanban-Token";

  private final SessionCookieManager cookies;
  private final SessionTokens tokens;
  private final PlatformAdminChecker admins;

  public ApiAusprobierFilter(
      SessionCookieManager cookies, SessionTokens tokens, PlatformAdminChecker admins) {
    this.cookies = cookies;
    this.tokens = tokens;
    this.admins = admins;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (request.getHeader(HEADER) == null) {
      filterChain.doFilter(request, response);
      return;
    }
    OptionalLong userId =
        cookies.readToken(request).map(tokens::verify).orElse(OptionalLong.empty());
    if (userId.isEmpty()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    if (!admins.isActivePlatformAdmin(userId.getAsLong())) {
      rejectAsForbidden(response);
      return;
    }
    String projektToken = request.getHeader(PROJEKT_TOKEN_HEADER);
    if (projektToken != null && !projektToken.isBlank()) {
      filterChain.doFilter(new OhneSessionCookie(request), response);
      return;
    }
    filterChain.doFilter(request, response);
  }

  private static void rejectAsForbidden(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    // Konstantes Literal nach dem Vorbild RateLimitFilter.rejectAsTooManyRequests, ohne
    // Serialisierung: Der Text ändert sich nie.
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                + "\"detail\":\"Das Ausprobieren aus der API-Übersicht ist Plattform-Admins"
                + " vorbehalten.\"}");
  }

  /** Die Anfrage ohne das Session-Cookie — in {@code getCookies()} wie im Cookie-Header. */
  private static final class OhneSessionCookie extends HttpServletRequestWrapper {

    OhneSessionCookie(HttpServletRequest request) {
      super(request);
    }

    // Der Servlet-Vertrag von getCookies() verlangt null, wenn die Anfrage keine Cookies trägt —
    // ein leeres Array wiche davon ab.
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
    @Override
    public Cookie @Nullable [] getCookies() {
      Cookie[] all = super.getCookies();
      if (all == null) {
        return null;
      }
      Cookie[] rest =
          Arrays.stream(all)
              .filter(c -> !SessionCookieManager.COOKIE_NAME.equals(c.getName()))
              .toArray(Cookie[]::new);
      return rest.length == 0 ? null : rest;
    }

    @Override
    public @Nullable String getHeader(String name) {
      String value = super.getHeader(name);
      if (value == null || !HttpHeaders.COOKIE.equalsIgnoreCase(name)) {
        return value;
      }
      return withoutSessionCookie(value);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      Enumeration<String> values = super.getHeaders(name);
      if (!HttpHeaders.COOKIE.equalsIgnoreCase(name)) {
        return values;
      }
      List<String> rest =
          Collections.list(values).stream()
              .map(OhneSessionCookie::withoutSessionCookie)
              .filter(Objects::nonNull)
              .toList();
      return Collections.enumeration(rest);
    }

    /**
     * Ein Cookie-Header ohne das Paar {@code manban_session=…}; {@code null}, wenn nichts bleibt.
     */
    private static @Nullable String withoutSessionCookie(String header) {
      String rest =
          Arrays.stream(header.split(";"))
              .map(String::strip)
              .filter(pair -> !SessionCookieManager.COOKIE_NAME.equals(nameOf(pair)))
              .filter(pair -> !pair.isEmpty())
              .collect(Collectors.joining("; "));
      return rest.isEmpty() ? null : rest;
    }

    private static String nameOf(String pair) {
      return pair.split("=", 2)[0].strip();
    }
  }
}
