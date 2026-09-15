package org.mwolff.manban.ratelimit.application;

import java.time.Duration;
import org.mwolff.manban.ratelimit.domain.OriginAttempts;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration der Zählbremse (Plan #892, E3/E7/E9). Wrapper-Typen statt Primitiven, damit „nicht
 * gesetzt" ({@code null}) vom Default unterscheidbar bleibt — {@code trusted-proxy-count: 0} ist
 * der gültige Fall „App direkt erreichbar" und nicht dasselbe wie „nichts konfiguriert".
 *
 * <p><strong>Modulintern</strong> (E16): Konfiguration ist kein Vertragsbestandteil des Moduls. Wer
 * von außen wissen will, ob die Bremse läuft, fragt {@link RateLimiter#isEnabled()}; die
 * ArchUnit-Whitelist lässt diesen Typ außerhalb von {@code ratelimit} nicht zu.
 *
 * @param enabled ob die Bremse greift; abgeschaltet wird nichts gezählt und nichts abgewiesen
 * @param trustedProxyCount Zahl der vertrauenswürdigen Proxies vor der Anwendung (E3)
 * @param maxTrackedOrigins Obergrenze der gleichzeitig verfolgten Herkünfte (E7)
 * @param login Grenzwerte der Anmeldung
 * @param register Grenzwerte der Registrierung
 * @param forgot Grenzwerte der Reset-Anforderung
 */
@ConfigurationProperties(prefix = "manban.ratelimit")
public record RateLimitProperties(
    Boolean enabled,
    Integer trustedProxyCount,
    Integer maxTrackedOrigins,
    Limits login,
    Limits register,
    Limits forgot) {

  /**
   * Grenzwerte eines Vorgangs. Drei gleich aufgebaute Blöcke statt eines gemeinsamen Wertesatzes
   * mit Überschreibungen (E9): AK 6 gibt den Vorgabewert, AK 4 verlangt getrennte Stellschrauben.
   *
   * @param maxAttempts Zahl der Versuche, ab der die Grenze ausgeschöpft ist
   * @param window Dauer des Zählfensters
   * @param blockDuration Dauer der Sperre ab dem ersten abgewiesenen Versuch
   */
  public record Limits(Integer maxAttempts, Duration window, Duration blockDuration) {

    private static final int DEFAULT_MAX_ATTEMPTS = 10;
    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(15);

    public Limits {
      if (maxAttempts == null || maxAttempts < 1) {
        maxAttempts = DEFAULT_MAX_ATTEMPTS;
      }
      if (window == null || window.isZero() || window.isNegative()) {
        window = DEFAULT_DURATION;
      }
      if (blockDuration == null || blockDuration.isZero() || blockDuration.isNegative()) {
        blockDuration = DEFAULT_DURATION;
      }
    }

    /** Der Block, der gilt, wenn zu einem Vorgang nichts konfiguriert ist. */
    static Limits defaults() {
      return new Limits(DEFAULT_MAX_ATTEMPTS, DEFAULT_DURATION, DEFAULT_DURATION);
    }

    /** Übersetzt die Konfiguration in die Grenzwerte, die die Domäne kennt. */
    OriginAttempts.Limits toDomain() {
      return new OriginAttempts.Limits(maxAttempts, window, blockDuration);
    }
  }

  public RateLimitProperties {
    if (enabled == null) {
      enabled = Boolean.TRUE;
    }
    if (trustedProxyCount == null || trustedProxyCount < 0) {
      trustedProxyCount = 1;
    }
    if (maxTrackedOrigins == null || maxTrackedOrigins < 1) {
      maxTrackedOrigins = 100_000;
    }
    if (login == null) {
      login = Limits.defaults();
    }
    if (register == null) {
      register = Limits.defaults();
    }
    if (forgot == null) {
      forgot = Limits.defaults();
    }
  }

  /** Die Grenzwerte des Vorgangs in der Form, die die Domäne verarbeitet. */
  public OriginAttempts.Limits limitsFor(RateLimitedOperation operation) {
    return switch (operation) {
      case LOGIN -> login.toDomain();
      case REGISTER -> register.toDomain();
      case FORGOT -> forgot.toDomain();
    };
  }
}
