package org.mwolff.manban.auth.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.SessionCheck;
import org.mwolff.manban.auth.application.SessionTokens;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Liest das Session-Cookie, verifiziert das signierte Token und setzt bei Gültigkeit die
 * Authentifizierung (Principal = userId, Authority {@code AUTH_SESSION}). Die Authority
 * unterscheidet Cookie- von PAT-Auth, damit sensible Endpunkte (Token-Verwaltung) nur per Cookie
 * erreichbar sind. Ungültige/fehlende Tokens lassen den Kontext leer.
 *
 * <p><strong>Gleitende Sitzung (Plan #1801, A10, E1):</strong> Liegt die Restlaufzeit eines
 * gültigen Tokens um mehr als einen Tag unter der vollen Sitzungsdauer, stellt der Filter ein neues
 * Token aus und schreibt es als Cookie zurück. Jeder mit dem Cookie gestellte Aufruf zählt als
 * Nutzung, auch eine Hintergrundabfrage; die Schwelle von einem Tag hält die Set-Cookie-Kopfzeilen
 * selten. Ein abgelaufenes, fremdes oder durch eine neue Sitzungs-Generation entwertetes Token
 * prüft der Port als ungültig — es wird nie erneuert.
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

  public static final String AUTHORITY = "AUTH_SESSION";

  private static final Duration RENEWAL_SLACK = Duration.ofDays(1);

  private final SessionCookieManager cookies;
  private final SessionTokens tokens;
  private final Duration renewalThreshold;
  private final Clock clock;

  public SessionAuthenticationFilter(
      SessionCookieManager cookies, SessionTokens tokens, AuthProperties properties, Clock clock) {
    this.cookies = cookies;
    this.tokens = tokens;
    this.renewalThreshold = properties.sessionTtl().minus(RENEWAL_SLACK);
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      cookies
          .readToken(request)
          .flatMap(tokens::verify)
          .ifPresent(
              check -> {
                var authentication =
                    new UsernamePasswordAuthenticationToken(
                        check.userId(), null, List.of(new SimpleGrantedAuthority(AUTHORITY)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                renewIfAged(check, response);
              });
    }
    filterChain.doFilter(request, response);
  }

  private void renewIfAged(SessionCheck check, HttpServletResponse response) {
    if (check.expiresAt().isBefore(clock.instant().plus(renewalThreshold))) {
      String renewed = tokens.issue(check.userId());
      response.addHeader(HttpHeaders.SET_COOKIE, cookies.create(renewed).toString());
    }
  }
}
