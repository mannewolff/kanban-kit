package org.mwolff.manban.ratelimit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.ratelimit.application.RateLimitProperties.Limits;
import org.mwolff.manban.ratelimit.domain.OriginAttempts;

/** Defaulting und Abbildung der Konfiguration der Zählbremse (Issue #897, Plan #892 E3/E7/E9). */
class RateLimitPropertiesTest {

  private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);

  @Test
  void defaults_applyWhenNothingIsConfigured() {
    // Given / When
    RateLimitProperties properties = new RateLimitProperties(null, null, null, null, null, null);

    // Then
    assertThat(properties)
        .extracting(
            RateLimitProperties::enabled,
            RateLimitProperties::trustedProxyCount,
            RateLimitProperties::maxTrackedOrigins)
        .containsExactly(true, 1, 100_000);
    assertThat(properties.login()).isEqualTo(new Limits(10, DEFAULT_WINDOW, DEFAULT_WINDOW));
    assertThat(properties.register()).isEqualTo(new Limits(10, DEFAULT_WINDOW, DEFAULT_WINDOW));
    assertThat(properties.forgot()).isEqualTo(new Limits(10, DEFAULT_WINDOW, DEFAULT_WINDOW));
  }

  @Test
  void configuredValues_areKept() {
    // Given / When — trusted-proxy-count 0 ist der gültige Fall „App direkt erreichbar" (E3) und
    // darf nicht mit „nicht gesetzt" verwechselt werden.
    RateLimitProperties properties =
        new RateLimitProperties(
            false,
            0,
            250,
            new Limits(3, Duration.ofMinutes(1), Duration.ofMinutes(2)),
            new Limits(4, Duration.ofMinutes(3), Duration.ofMinutes(4)),
            new Limits(5, Duration.ofMinutes(5), Duration.ofMinutes(6)));

    // Then
    assertThat(properties)
        .extracting(
            RateLimitProperties::enabled,
            RateLimitProperties::trustedProxyCount,
            RateLimitProperties::maxTrackedOrigins)
        .containsExactly(false, 0, 250);
    assertThat(properties.login().maxAttempts()).isEqualTo(3);
    assertThat(properties.register().maxAttempts()).isEqualTo(4);
    assertThat(properties.forgot().maxAttempts()).isEqualTo(5);
  }

  @Test
  void negativeProxyCount_fallsBackToTheDefault() {
    // Given / When
    RateLimitProperties properties = new RateLimitProperties(true, -1, 250, null, null, null);

    // Then
    assertThat(properties.trustedProxyCount()).isEqualTo(1);
  }

  @Test
  void nonPositiveMaxTrackedOrigins_fallsBackToTheDefault() {
    // Given / When
    RateLimitProperties properties = new RateLimitProperties(true, 2, 0, null, null, null);

    // Then
    assertThat(properties.maxTrackedOrigins()).isEqualTo(100_000);
  }

  @Test
  void theSmallestUsefulNumbersAreAcceptedAndNotMistakenForUnset() {
    // Given / When — 1 liegt bei beiden Feldern genau auf der Grenze und ist ein gültiger Wert.
    RateLimitProperties properties =
        new RateLimitProperties(
            true, 1, 1, new Limits(1, Duration.ofMillis(1), Duration.ofMillis(1)), null, null);

    // Then
    assertThat(properties.maxTrackedOrigins()).isEqualTo(1);
    assertThat(properties.login())
        .isEqualTo(new Limits(1, Duration.ofMillis(1), Duration.ofMillis(1)));
  }

  @Test
  void halfConfiguredBlock_fallsBackFieldByField() {
    // Given / When — Spring bindet nur die gesetzten Felder; die übrigen kommen als null herein,
    // ein Block ist also nicht „ganz oder gar nicht" konfiguriert.
    Limits limits = new Limits(null, null, null);

    // Then
    assertThat(limits).isEqualTo(new Limits(10, DEFAULT_WINDOW, DEFAULT_WINDOW));
  }

  @Test
  void nonPositiveMaxAttempts_fallsBackToTheDefault() {
    // Given / When
    Limits limits = new Limits(0, Duration.ofMinutes(1), Duration.ofMinutes(2));

    // Then
    assertThat(limits.maxAttempts()).isEqualTo(10);
  }

  @Test
  void zeroDurations_fallBackToTheDefaults() {
    // Given / When
    Limits limits = new Limits(3, Duration.ZERO, Duration.ofMinutes(2));
    Limits other = new Limits(3, Duration.ofMinutes(1), Duration.ZERO);

    // Then
    assertThat(limits.window()).isEqualTo(DEFAULT_WINDOW);
    assertThat(limits.blockDuration()).isEqualTo(Duration.ofMinutes(2));
    assertThat(other.window()).isEqualTo(Duration.ofMinutes(1));
    assertThat(other.blockDuration()).isEqualTo(DEFAULT_WINDOW);
  }

  @Test
  void negativeDurations_fallBackToTheDefaults() {
    // Given / When — Gegenprobe zum vorigen Test: hier greift der Negativ-Zweig beider Prüfungen.
    Limits limits = new Limits(3, Duration.ofSeconds(-1), Duration.ofSeconds(-1));

    // Then
    assertThat(limits)
        .extracting(Limits::window, Limits::blockDuration)
        .containsExactly(DEFAULT_WINDOW, DEFAULT_WINDOW);
  }

  @Test
  void limitsFor_mapsEveryOperationToItsOwnBlock() {
    // Given
    RateLimitProperties properties =
        new RateLimitProperties(
            true,
            1,
            10,
            new Limits(3, Duration.ofMinutes(1), Duration.ofMinutes(2)),
            new Limits(4, Duration.ofMinutes(3), Duration.ofMinutes(4)),
            new Limits(5, Duration.ofMinutes(5), Duration.ofMinutes(6)));

    // When / Then
    assertThat(properties.limitsFor(RateLimitedOperation.LOGIN))
        .isEqualTo(new OriginAttempts.Limits(3, Duration.ofMinutes(1), Duration.ofMinutes(2)));
    assertThat(properties.limitsFor(RateLimitedOperation.REGISTER))
        .isEqualTo(new OriginAttempts.Limits(4, Duration.ofMinutes(3), Duration.ofMinutes(4)));
    assertThat(properties.limitsFor(RateLimitedOperation.FORGOT))
        .isEqualTo(new OriginAttempts.Limits(5, Duration.ofMinutes(5), Duration.ofMinutes(6)));
  }
}
