package org.mwolff.manban.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.mwolff.manban.auth.application.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Baut und liest das Session-Cookie. HttpOnly (XSS-sicher), {@code SameSite=Strict} (das Cookie
 * wird nie cross-site gesendet — die CSRF-Absicherung für den zustandslosen Cookie-Ansatz), Secure
 * konfigurierbar (hinter Caddy-TLS an).
 */
@Component
public class SessionCookieManager {

  public static final String COOKIE_NAME = "manban_session";

  private final boolean secure;
  private final Duration ttl;

  public SessionCookieManager(AuthProperties properties) {
    this.secure = properties.cookieSecure();
    this.ttl = properties.sessionTtl();
  }

  public ResponseCookie create(String token) {
    return base(token).maxAge(ttl).build();
  }

  public ResponseCookie clear() {
    return base("").maxAge(0).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path("/");
  }

  public Optional<String> readToken(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return Optional.empty();
    }
    return Arrays.stream(request.getCookies())
        .filter(c -> COOKIE_NAME.equals(c.getName()))
        .map(jakarta.servlet.http.Cookie::getValue)
        .filter(v -> v != null && !v.isBlank())
        .findFirst();
  }
}
