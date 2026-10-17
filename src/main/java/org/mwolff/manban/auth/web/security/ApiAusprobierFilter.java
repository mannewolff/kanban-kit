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
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.auth.application.SessionCheck;
import org.mwolff.manban.auth.application.SessionTokens;
import org.mwolff.manban.common.i18n.Texte;
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
 *
 * <p>Die Abweisung erscheint in der Sprache nach Zugangsart (Issue #1640, Plan #1638 A5/E2): Sie
 * trifft eine Session, also in der eingestellten Sprache.
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

  /** Schlüssel der Abweisung an Nutzer ohne Plattform-Admin-Recht. */
  static final String NUR_PLATTFORM_ADMINS = "auth.api-ausprobieren.nur-plattform-admins";

  private final SessionCookieManager cookies;
  private final SessionTokens tokens;
  private final PlatformAdminChecker admins;
  private final Texte texte;

  public ApiAusprobierFilter(
      SessionCookieManager cookies,
      SessionTokens tokens,
      PlatformAdminChecker admins,
      Texte texte) {
    this.cookies = cookies;
    this.tokens = tokens;
    this.admins = admins;
    this.texte = texte;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (request.getHeader(HEADER) == null) {
      filterChain.doFilter(request, response);
      return;
    }
    Optional<SessionCheck> session = cookies.readToken(request).flatMap(tokens::verify);
    if (session.isEmpty()) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    if (!admins.isActivePlatformAdmin(session.orElseThrow().userId())) {
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

  private void rejectAsForbidden(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    // Literal nach dem Vorbild RateLimitFilter.rejectAsTooManyRequests, ohne Serialisierung: Der
    // Text stammt aus den Meldungsdateien und enthält weder Anführungszeichen noch Backslash.
    // Gewöhnliches Literal statt Konstante: Error Prone (InlineFormatString) duldet eine einmal
    // benutzte Formatvorlage nicht als Konstante.
    response
        .getWriter()
        .write(
            "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"%s\"}"
                .formatted(texte.text(texte.spracheDerAnfrage(), NUR_PLATTFORM_ADMINS)));
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
