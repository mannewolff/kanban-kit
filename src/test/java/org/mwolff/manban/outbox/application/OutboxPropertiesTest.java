package org.mwolff.manban.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Defaulting der Outbox-Konfiguration (Issue #501). */
class OutboxPropertiesTest {

  @Test
  void defaults_applyWhenNothingIsConfigured() {
    // Given / When
    OutboxProperties properties = new OutboxProperties(null, null, null, null, null, null, null);

    // Then
    assertThat(properties)
        .extracting(
            OutboxProperties::enabled,
            OutboxProperties::pollIntervalMs,
            OutboxProperties::batchSize,
            OutboxProperties::maxAttempts,
            OutboxProperties::retryBaseDelay,
            OutboxProperties::retryMaxDelay,
            OutboxProperties::completedRetentionDays)
        .containsExactly(true, 5000L, 50, 8, Duration.ofSeconds(10), Duration.ofHours(1), 7);
  }

  @Test
  void configuredValues_areKept() {
    // Given / When
    OutboxProperties properties =
        new OutboxProperties(false, 250L, 10, 3, Duration.ofSeconds(2), Duration.ofMinutes(5), 30);

    // Then
    assertThat(properties)
        .extracting(
            OutboxProperties::enabled,
            OutboxProperties::pollIntervalMs,
            OutboxProperties::batchSize,
            OutboxProperties::maxAttempts,
            OutboxProperties::retryBaseDelay,
            OutboxProperties::retryMaxDelay,
            OutboxProperties::completedRetentionDays)
        .containsExactly(false, 250L, 10, 3, Duration.ofSeconds(2), Duration.ofMinutes(5), 30);
  }

  @Test
  void nonPositiveNumbers_fallBackToTheDefaults() {
    // Given / When
    OutboxProperties properties =
        new OutboxProperties(true, 0L, 0, 0, Duration.ofSeconds(1), Duration.ofSeconds(2), -1);

    // Then
    assertThat(properties)
        .extracting(
            OutboxProperties::pollIntervalMs,
            OutboxProperties::batchSize,
            OutboxProperties::maxAttempts,
            OutboxProperties::completedRetentionDays)
        .containsExactly(5000L, 50, 8, 7);
  }

  @Test
  void nonPositiveDurations_fallBackToTheDefaults() {
    // Given / When
    OutboxProperties properties =
        new OutboxProperties(true, 1L, 1, 1, Duration.ZERO, Duration.ofSeconds(-1), 1);

    // Then
    assertThat(properties)
        .extracting(OutboxProperties::retryBaseDelay, OutboxProperties::retryMaxDelay)
        .containsExactly(Duration.ofSeconds(10), Duration.ofHours(1));
  }

  @Test
  void bothDurationsAreCheckedForZeroAndForNegativeValues() {
    // Given / When — Gegenprobe zum vorigen Test: hier ist die Basis negativ und das Maximum
    // null-lang, sodass auch der jeweils andere Zweig der Prüfung greift.
    OutboxProperties properties =
        new OutboxProperties(true, 1L, 1, 1, Duration.ofSeconds(-1), Duration.ZERO, 1);

    // Then
    assertThat(properties)
        .extracting(OutboxProperties::retryBaseDelay, OutboxProperties::retryMaxDelay)
        .containsExactly(Duration.ofSeconds(10), Duration.ofHours(1));
  }

  @Test
  void theSmallestUsefulNumbersAreAcceptedAndNotMistakenForUnset() {
    // Given / When — 1 liegt genau auf der Grenze und ist ein gültiger Wert.
    OutboxProperties properties =
        new OutboxProperties(true, 1L, 1, 1, Duration.ofMillis(1), Duration.ofMillis(1), 1);

    // Then
    assertThat(properties)
        .extracting(
            OutboxProperties::pollIntervalMs,
            OutboxProperties::batchSize,
            OutboxProperties::maxAttempts,
            OutboxProperties::completedRetentionDays,
            OutboxProperties::retryBaseDelay,
            OutboxProperties::retryMaxDelay)
        .containsExactly(1L, 1, 1, 1, Duration.ofMillis(1), Duration.ofMillis(1));
  }
}
