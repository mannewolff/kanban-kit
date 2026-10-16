package org.mwolff.manban.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Auth-Konfiguration.
 *
 * @param baseUrl öffentliche Basis-URL für Links in E-Mails (z. B. Anmeldelink)
 * @param sessionSecret HMAC-Geheimnis zum Signieren des Session-Cookies. In Produktion zwingend
 *     über die Umgebung setzen (stabil über Neustarts/Instanzen). Der Rückfall auf {@link
 *     #INSECURE_DEFAULT_SESSION_SECRET} ist seit Issue #890 nur noch im ausdrücklich
 *     eingeschalteten Entwicklungs- oder Testbetrieb ({@code manban.dev-mode=true}) zulässig; sonst
 *     verweigert die Anwendung den Start.
 * @param sessionTtl volle Gültigkeitsdauer eines Session-Cookies (Vorgabe 30 Tage); die Sitzung
 *     verlängert sich bei Nutzung gleitend (Plan #1801, A10)
 * @param cookieSecure ob das Session-Cookie das Secure-Flag trägt (Default true; hinter Caddy-TLS
 *     korrekt. Nur für lokalen Klartext-HTTP-Dev auf false setzen.)
 * @param loginCode Grenzen der Code-Anmeldung (Plan #1801, A6)
 */
@ConfigurationProperties(prefix = "manban.auth")
public record AuthProperties(
    String baseUrl,
    String sessionSecret,
    Duration sessionTtl,
    Boolean cookieSecure,
    LoginCode loginCode) {

  /**
   * Der mitgelieferte Standard-Signaturschlüssel. Er steht im öffentlichen Repository und ist
   * deshalb kein Geheimnis: Mit ihm signierte Session-Cookies kann jeder fälschen. Als Konstante
   * statt als Literal, damit die Startprüfung ({@code
   * auth.infrastructure.security.SessionSecretStartupCheck}) genau diesen Wert erkennt und das
   * Literal im Java-Quellcode nur noch an dieser einen Stelle steht (Issue #890).
   */
  public static final String INSECURE_DEFAULT_SESSION_SECRET = "dev-only-insecure-secret-change-me";

  public AuthProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "http://localhost:8080";
    }
    if (sessionSecret == null || sessionSecret.isBlank()) {
      sessionSecret = INSECURE_DEFAULT_SESSION_SECRET;
    }
    if (sessionTtl == null) {
      sessionTtl = Duration.ofDays(30);
    }
    if (cookieSecure == null) {
      cookieSecure = Boolean.TRUE;
    }
    if (loginCode == null) {
      loginCode = LoginCode.DEFAULTS;
    }
  }

  /**
   * Grenzen der Code-Anmeldung (Plan #1801, A6), Präfix {@code manban.auth.login-code}.
   *
   * @param ttl Gültigkeitsdauer eines Codes samt Link (Vorgabe 10 Minuten)
   * @param maxAttempts falsche Eingaben, nach denen ein Code verfällt (Vorgabe 5)
   * @param maxRequestsPerHour angenommene Anforderungen je Adresse in einer gleitenden Stunde
   *     (Vorgabe 5)
   */
  public record LoginCode(Duration ttl, Integer maxAttempts, Integer maxRequestsPerHour) {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int DEFAULT_MAX_REQUESTS_PER_HOUR = 5;

    /** Alle Vorgaben — für einen fehlenden Block {@code manban.auth.login-code}. */
    static final LoginCode DEFAULTS =
        new LoginCode(DEFAULT_TTL, DEFAULT_MAX_ATTEMPTS, DEFAULT_MAX_REQUESTS_PER_HOUR);

    public LoginCode {
      if (ttl == null) {
        ttl = DEFAULT_TTL;
      }
      if (maxAttempts == null) {
        maxAttempts = DEFAULT_MAX_ATTEMPTS;
      }
      if (maxRequestsPerHour == null) {
        maxRequestsPerHour = DEFAULT_MAX_REQUESTS_PER_HOUR;
      }
    }
  }
}
