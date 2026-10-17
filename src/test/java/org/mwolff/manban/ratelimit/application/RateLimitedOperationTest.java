package org.mwolff.manban.ratelimit.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Die begrenzten Vorgänge samt Pfad und Zählregel (Issue #897, Plan #892 E4/E5; Code-Anmeldung
 * Issue #1807, Plan #1801 A7).
 *
 * <p>Der Pfad steht am Vorgang und nicht am Filter, damit die Registrierung des Filters (späteres
 * Paket) dieselbe Liste benutzt wie die Zuordnung eines Requests — zwei Listen wären zwei
 * Wahrheiten.
 */
class RateLimitedOperationTest {

  @Test
  void everyOperationCarriesItsPath() {
    // Given / When / Then
    assertThat(RateLimitedOperation.REGISTER.path()).isEqualTo("/api/auth/register");
    assertThat(RateLimitedOperation.CODE_REQUEST.path()).isEqualTo("/api/auth/code/request");
    assertThat(RateLimitedOperation.CODE_VERIFY.path()).isEqualTo("/api/auth/code/verify");
    assertThat(RateLimitedOperation.CODE_LINK.path()).isEqualTo("/api/auth/code/link");
  }

  @Test
  void theRegistrationCountsEveryCall() {
    // Given / When / Then — E5: bei der Registrierung zählt jeder Aufruf, auch der erfolgreiche.
    assertThat(RateLimitedOperation.REGISTER.countsOnlyFailures()).isFalse();
  }

  @Test
  void passwordLoginAndResetRequestAreGone() {
    // Given / When / Then — Plan #1801 A7: Mit Passwort-Anmeldung und Reset entfallen ihre
    // Vorgänge.
    assertThat(RateLimitedOperation.values())
        .extracting(RateLimitedOperation::name)
        .containsExactly("REGISTER", "CODE_REQUEST", "CODE_VERIFY", "CODE_LINK");
  }

  @Test
  void theCodeRequestCountsEveryCall_codeEntryAndLinkCountOnlyFailures() {
    // Given / When / Then — Plan #1801 A7: Die Anforderung verschickt Mails, dort zählt wie bei der
    // Registrierung jeder Aufruf; Eingabe und Link sind Rateversuche, dort zählt nur der
    // abgewiesene.
    assertThat(RateLimitedOperation.CODE_REQUEST.countsOnlyFailures()).isFalse();
    assertThat(RateLimitedOperation.CODE_VERIFY.countsOnlyFailures()).isTrue();
    assertThat(RateLimitedOperation.CODE_LINK.countsOnlyFailures()).isTrue();
  }
}
