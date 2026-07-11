package org.mwolff.manban.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AuthProperties;

/**
 * Deterministischer Ablauf-Test der signierten Session-Tokens: ein bei {@code t} ausgestelltes
 * Token gilt bei {@code t + ttl} noch, ist bei {@code t + ttl + 1ms} aber abgelaufen.
 */
class SignedSessionTokensTest {

  private static final Instant ISSUED_AT = Instant.parse("2026-01-02T03:04:05Z");
  private static final Duration TTL = Duration.ofHours(1);

  private static AuthProperties properties() {
    return new AuthProperties(null, null, "stable-test-secret", TTL, null, null);
  }

  private static SignedSessionTokens at(Instant instant) {
    return new SignedSessionTokens(properties(), Clock.fixed(instant, ZoneOffset.UTC));
  }

  @Test
  void verify_tokenExpiredJustAfterTtl_returnsEmpty() {
    // Given
    String token = at(ISSUED_AT).issue(42L);

    // When
    OptionalLong result = at(ISSUED_AT.plus(TTL).plusMillis(1)).verify(token);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void verify_tokenAtExpiryBoundary_stillValid() {
    // Given
    String token = at(ISSUED_AT).issue(42L);

    // When
    OptionalLong result = at(ISSUED_AT.plus(TTL)).verify(token);

    // Then
    assertThat(result).hasValue(42L);
  }
}
