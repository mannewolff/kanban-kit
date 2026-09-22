package org.mwolff.manban.ratelimit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Vorgaben und Abbildung der Konfiguration der Durchsatzbremse (Issue #999, Plan #995 E3). */
class ThroughputPropertiesTest {

  @Test
  void defaults_applyWhenNothingIsConfigured() {
    ThroughputProperties properties = new ThroughputProperties(null, null, null, null);

    assertThat(properties)
        .extracting(
            ThroughputProperties::enabled,
            ThroughputProperties::perMinute,
            ThroughputProperties::concurrent,
            ThroughputProperties::maxTrackedPersons)
        .containsExactly(true, 60, 10, 100_000);
  }

  @Test
  void configuredValues_areKept() {
    ThroughputProperties properties = new ThroughputProperties(false, 30, 5, 250);

    assertThat(properties)
        .extracting(
            ThroughputProperties::enabled,
            ThroughputProperties::perMinute,
            ThroughputProperties::concurrent,
            ThroughputProperties::maxTrackedPersons)
        .containsExactly(false, 30, 5, 250);
  }

  @Test
  void valuesBelowOne_fallBackToTheDefaults() {
    ThroughputProperties properties = new ThroughputProperties(true, 0, 0, 0);

    assertThat(properties)
        .extracting(
            ThroughputProperties::perMinute,
            ThroughputProperties::concurrent,
            ThroughputProperties::maxTrackedPersons)
        .containsExactly(60, 10, 100_000);
  }

  @Test
  void valuesOfOne_areKept() {
    ThroughputProperties properties = new ThroughputProperties(true, 1, 1, 1);

    assertThat(properties)
        .extracting(
            ThroughputProperties::perMinute,
            ThroughputProperties::concurrent,
            ThroughputProperties::maxTrackedPersons)
        .containsExactly(1, 1, 1);
  }

  @Test
  void limits_translateTheMinuteRateIntoCostPerCall() {
    ThroughputProperties properties = new ThroughputProperties(true, 30, 5, 250);

    assertThat(properties.limits().costPerCall()).isEqualTo(Duration.ofSeconds(2));
    assertThat(properties.limits().capacity()).isEqualTo(30);
    assertThat(properties.limits().maxConcurrent()).isEqualTo(5);
  }
}
