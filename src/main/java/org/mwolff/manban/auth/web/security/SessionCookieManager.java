package org.mwolff.manban.auth.web.security;

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
 *
 * <p>Daneben das Cookie {@value #VORSCHAU_COOKIE_NAME} der Vorschau „Ansicht als XY“ (Issue #1919,
 * Plan #1914 E2) mit denselben Attributen. Es trägt kein {@code Max-Age} und lebt höchstens so
 * lange wie der Browser: Wann die Vorschau endet, entscheidet allein der Server.
 */
@Component
public class SessionCookieManager {

  public static final String COOKIE_NAME = "manban_session";

  /** Name des Cookies der Vorschau „Ansicht als XY“. */
  public static final String VORSCHAU_COOKIE_NAME = "manban_vorschau";

  private static final String TRENNER = ".";

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

  /** Das Vorschau-Cookie mit Kennung und Geheimnis. */
  public ResponseCookie createVorschau(String kennung, String geheimnis) {
    return base(VORSCHAU_COOKIE_NAME, kennung + TRENNER + geheimnis).build();
  }

  /** Löscht das Vorschau-Cookie. */
  public ResponseCookie clearVorschau() {
    return base(VORSCHAU_COOKIE_NAME, "").maxAge(0).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return base(COOKIE_NAME, value);
  }

  private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Strict")
        .path("/");
  }

  public Optional<String> readToken(HttpServletRequest request) {
    return read(request, COOKIE_NAME);
  }

  /** Kennung und Geheimnis aus dem Vorschau-Cookie; leer, wenn es fehlt oder verformt ist. */
  public Optional<VorschauCookie> readVorschau(HttpServletRequest request) {
    return read(request, VORSCHAU_COOKIE_NAME)
        .map(wert -> wert.split("\\.", -1))
        .filter(teile -> teile.length == 2 && !teile[0].isEmpty() && !teile[1].isEmpty())
        .map(teile -> new VorschauCookie(teile[0], teile[1]));
  }

  private static Optional<String> read(HttpServletRequest request, String name) {
    if (request.getCookies() == null) {
      return Optional.empty();
    }
    return Arrays.stream(request.getCookies())
        .filter(c -> name.equals(c.getName()))
        .map(jakarta.servlet.http.Cookie::getValue)
        .filter(v -> v != null && !v.isBlank())
        .findFirst();
  }
}
