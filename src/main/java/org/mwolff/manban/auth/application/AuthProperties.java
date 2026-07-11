package org.mwolff.manban.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Auth-Konfiguration.
 *
 * @param baseUrl öffentliche Basis-URL für Links in E-Mails (z. B. Verifikation)
 * @param verificationTtl Gültigkeitsdauer eines E-Mail-Verifikations-Tokens
 * @param sessionSecret HMAC-Geheimnis zum Signieren des Session-Cookies. In Produktion zwingend
 *     über die Umgebung setzen (stabil über Neustarts/Instanzen).
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
      sessionSecret = "dev-only-insecure-secret-change-me";
    }
    if (sessionTtl == null) {
      sessionTtl = Duration.ofDays(7);
    }
    if (cookieSecure == null) {
      cookieSecure = Boolean.TRUE;
    }
  }
}
