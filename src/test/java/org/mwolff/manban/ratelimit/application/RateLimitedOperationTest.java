package org.mwolff.manban.ratelimit.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Die drei begrenzten Vorgänge samt Pfad und Zählregel (Issue #897, Plan #892 E4/E5).
 *
 * <p>Der Pfad steht am Vorgang und nicht am Filter, damit die Registrierung des Filters (späteres
 * Paket) dieselbe Liste benutzt wie die Zuordnung eines Requests — zwei Listen wären zwei
 * Wahrheiten.
 */
class RateLimitedOperationTest {

  @Test
  void everyOperationCarriesItsPath() {
    // Given / When / Then
    assertThat(RateLimitedOperation.LOGIN.path()).isEqualTo("/api/auth/login");
    assertThat(RateLimitedOperation.REGISTER.path()).isEqualTo("/api/auth/register");
    assertThat(RateLimitedOperation.FORGOT.path()).isEqualTo("/api/auth/forgot");
  }

  @Test
  void onlyTheLoginCountsFailuresAlone() {
    // Given / When / Then — E4: bei der Anmeldung zählt nur der fehlgeschlagene Versuch; E5: bei
    // Registrierung und Reset-Anforderung zählt jeder, auch der erfolgreiche.
    assertThat(RateLimitedOperation.LOGIN.countsOnlyFailures()).isTrue();
    assertThat(RateLimitedOperation.REGISTER.countsOnlyFailures()).isFalse();
    assertThat(RateLimitedOperation.FORGOT.countsOnlyFailures()).isFalse();
  }
}
