package org.mwolff.manban.auth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.OptionalLong;
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
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

  public static final String AUTHORITY = "AUTH_SESSION";

  private final SessionCookieManager cookies;
  private final SignedSessionTokens tokens;

  public SessionAuthenticationFilter(SessionCookieManager cookies, SignedSessionTokens tokens) {
    this.cookies = cookies;
    this.tokens = tokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      cookies
          .readToken(request)
          .ifPresent(
              token -> {
                OptionalLong userId = tokens.verify(token);
                if (userId.isPresent()) {
                  var authentication =
                      new UsernamePasswordAuthenticationToken(
                          userId.getAsLong(), null, List.of(new SimpleGrantedAuthority(AUTHORITY)));
                  SecurityContextHolder.getContext().setAuthentication(authentication);
                }
              });
    }
    filterChain.doFilter(request, response);
  }
}
