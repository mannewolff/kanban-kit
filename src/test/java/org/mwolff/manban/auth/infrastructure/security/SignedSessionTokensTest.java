package org.mwolff.manban.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.auth.application.AuthProperties;

/**
 * Deterministischer Ablauf-Test der signierten Session-Tokens: ein bei {@code t} ausgestelltes
 * Token gilt bei {@code t + ttl} noch, ist bei {@code t + ttl + 1ms} aber abgelaufen. Zusätzlich
 * werden alle Ungültigkeits-Pfade der Verifikation abgedeckt.
 */
class SignedSessionTokensTest {

  private static final Instant ISSUED_AT = Instant.parse("2026-01-02T03:04:05Z");
  private static final Duration TTL = Duration.ofHours(1);
  private static final String SECRET = "stable-test-secret";

  private static AuthProperties properties() {
    return new AuthProperties(null, null, SECRET, TTL, null, null);
  }

  private static SignedSessionTokens at(Instant instant) {
    return new SignedSessionTokens(properties(), Clock.fixed(instant, ZoneOffset.UTC));
  }

  /** Baut ein Token mit gültiger Signatur über eine beliebige (ggf. defekte) Nutzlast. */
  private static String signedToken(String rawPayload) throws Exception {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    String encodedPayload = encoder.encodeToString(rawPayload.getBytes(StandardCharsets.UTF_8));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] signature = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
    return encodedPayload + "." + encoder.encodeToString(signature);
  }

  @Test
  void verify_validToken_returnsUserId() {
    // Given
    String token = at(ISSUED_AT).issue(42L);

    // When
    OptionalLong result = at(ISSUED_AT).verify(token);

    // Then
    assertThat(result).hasValue(42L);
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

  /**
   * Vier strukturell identische Ungültigkeits-Fälle (null, kein Trennzeichen, Signatur nicht
   * Base64, Signatur-Mismatch) parametrisiert statt als separate Tests (Sonar S5976).
   */
  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"no-dot-here", "payload.$$$not-base64$$$", "payload.AAAA"})
  void verify_invalidToken_returnsEmpty(String token) {
    // When
    OptionalLong result = at(ISSUED_AT).verify(token);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void verify_validSignatureButMalformedPayload_returnsEmpty() throws Exception {
    // Given: korrekte Signatur, aber Nutzlast ohne parsebare userId
    String token = signedToken("not-a-number:123");

    // When
    OptionalLong result = at(ISSUED_AT).verify(token);

    // Then
    assertThat(result).isEmpty();
  }
}
