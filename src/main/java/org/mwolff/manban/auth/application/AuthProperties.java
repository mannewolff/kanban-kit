package org.mwolff.manban.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Auth-Konfiguration.
 *
 * @param baseUrl öffentliche Basis-URL für Links in E-Mails (z. B. Verifikation)
 * @param verificationTtl Gültigkeitsdauer eines E-Mail-Verifikations-Tokens
 * @param sessionSecret HMAC-Geheimnis zum Signieren des Session-Cookies. In Produktion zwingend
 *     über die Umgebung setzen (stabil über Neustarts/Instanzen). Der Rückfall auf {@link
 *     #INSECURE_DEFAULT_SESSION_SECRET} ist seit Issue #890 nur noch im ausdrücklich
 *     eingeschalteten Entwicklungs- oder Testbetrieb ({@code manban.dev-mode=true}) zulässig; sonst
 *     verweigert die Anwendung den Start.
 * @param sessionTtl Gültigkeitsdauer eines Session-Cookies
 * @param cookieSecure ob das Session-Cookie das Secure-Flag trägt (Default true; hinter Caddy-TLS
 *     korrekt. Nur für lokalen Klartext-HTTP-Dev auf false setzen.)
 */
@ConfigurationProperties(prefix = "manban.auth")
public record AuthProperties(
    String baseUrl,
    Duration verificationTtl,
    String sessionSecret,
    Duration sessionTtl,
    Boolean cookieSecure,
    Duration resetTtl) {

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
    if (verificationTtl == null) {
      verificationTtl = Duration.ofHours(24);
    }
    if (resetTtl == null) {
      resetTtl = Duration.ofHours(1);
    }
    if (sessionSecret == null || sessionSecret.isBlank()) {
      sessionSecret = INSECURE_DEFAULT_SESSION_SECRET;
    }
    if (sessionTtl == null) {
      sessionTtl = Duration.ofDays(7);
    }
    if (cookieSecure == null) {
      cookieSecure = Boolean.TRUE;
    }
  }
}
