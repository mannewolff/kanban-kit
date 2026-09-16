package org.mwolff.manban.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.SessionGenerations;

/**
 * Deterministischer Ablauf-Test der signierten Session-Tokens: ein bei {@code t} ausgestelltes
 * Token gilt bei {@code t + ttl} noch, ist bei {@code t + ttl + 1ms} aber abgelaufen. Zusätzlich
 * werden alle Ungültigkeits-Pfade der Verifikation abgedeckt — einschließlich der Sitzungs-
 * Generation (Issue #885): Ein Token gilt nur, solange das Konto noch in der Generation steht, in
 * der das Token ausgestellt wurde.
 */
class SignedSessionTokensTest {

  private static final Instant ISSUED_AT = Instant.parse("2026-01-02T03:04:05Z");
  private static final Duration TTL = Duration.ofHours(1);
  private static final String SECRET = "stable-test-secret";
  private static final long USER_ID = 42L;
  private static final long GENERATION = 7L;

  private final SessionGenerations generations = mock(SessionGenerations.class);

  @BeforeEach
  void accountStandsInCurrentGeneration() {
    when(generations.current(anyLong())).thenReturn(OptionalLong.of(GENERATION));
  }

  private static AuthProperties properties() {
    return new AuthProperties(null, null, SECRET, TTL, null, null);
  }

  private SignedSessionTokens at(Instant instant) {
    return new SignedSessionTokens(properties(), Clock.fixed(instant, ZoneOffset.UTC), generations);
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
    String token = at(ISSUED_AT).issue(USER_ID);

    // When
    OptionalLong result = at(ISSUED_AT).verify(token);

    // Then
    assertThat(result).hasValue(USER_ID);
  }

  @Test
  void verify_tokenExpiredJustAfterTtl_returnsEmpty() {
    // Given
    String token = at(ISSUED_AT).issue(USER_ID);

    // When
    OptionalLong result = at(ISSUED_AT.plus(TTL).plusMillis(1)).verify(token);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void verify_tokenAtExpiryBoundary_stillValid() {
    // Given
    String token = at(ISSUED_AT).issue(USER_ID);

    // When
    OptionalLong result = at(ISSUED_AT.plus(TTL)).verify(token);

    // Then
    assertThat(result).hasValue(USER_ID);
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
    String token = signedToken("not-a-number:123:" + GENERATION);

    // When
    OptionalLong result = at(ISSUED_AT).verify(token);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void verify_tokenOfOlderGeneration_returnsEmpty() {
    // Given: Token der Generation 7, Signatur und Ablauf stimmen …
    String token = at(ISSUED_AT).issue(USER_ID);
    // … das Konto ist inzwischen eine Generation weiter
    when(generations.current(USER_ID)).thenReturn(OptionalLong.of(GENERATION + 1));

    // When
    OptionalLong result = at(ISSUED_AT).verify(token);

    // Then
    assertThat(result).isEmpty();
  }

  /**
   * Belegt, dass {@code issue} die <em>aktuelle</em> Generation einbettet und keine Konstante: ein
   * bei Generation 9 ausgestelltes Token gilt für ein Konto in Generation 7 nicht, für dasselbe
   * Konto in Generation 9 aber schon.
   */
  @Test
  void issue_embedsCurrentGeneration_roundTrip() {
    // Given
    when(generations.current(USER_ID)).thenReturn(OptionalLong.of(9L));
    String token = at(ISSUED_AT).issue(USER_ID);

    // When / Then: Konto eine andere Generation → ungültig
    when(generations.current(USER_ID)).thenReturn(OptionalLong.of(GENERATION));
    assertThat(at(ISSUED_AT).verify(token)).isEmpty();

    // When / Then: Konto in der Generation der Ausstellung → gültig
    when(generations.current(USER_ID)).thenReturn(OptionalLong.of(9L));
    assertThat(at(ISSUED_AT).verify(token)).hasValue(USER_ID);
  }

  /**
   * Die Nutzlast hat genau drei Felder. Geprüft werden beide Abweichungen: das bisherige Zwei-Feld-
   * Format ohne Generation (Plan #883, E3 — kein Toleranzpfad) und eine Nutzlast mit Zusatzfeld.
   * Beide sind korrekt signiert und unverfallen, gelten aber trotzdem nicht.
   */
  @ParameterizedTest
  @ValueSource(strings = {"%d:%d", "%d:%d:" + GENERATION + ":extra"})
  void verify_payloadWithoutExactlyThreeFields_returnsEmpty(String template) throws Exception {
    // Given
    String token = signedToken(template.formatted(USER_ID, ISSUED_AT.plus(TTL).toEpochMilli()));

    // When
    OptionalLong result = at(ISSUED_AT).verify(token);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void verify_unknownUser_returnsEmpty() {
    // Given: gültiges Token, das Konto existiert bei der Prüfung aber nicht mehr (Plan #883, E9)
    String token = at(ISSUED_AT).issue(USER_ID);
    when(generations.current(USER_ID)).thenReturn(OptionalLong.empty());

    // When
    OptionalLong result = at(ISSUED_AT).verify(token);

    // Then
    assertThat(result).isEmpty();
  }

  @Test
  void issue_unknownUser_throwsIllegalStateException() {
    // Given: der Aufrufer hat den Benutzer gerade angemeldet — ein fehlendes Konto ist ein Defekt
    when(generations.current(USER_ID)).thenReturn(OptionalLong.empty());
    SignedSessionTokens tokens = at(ISSUED_AT);

    // When / Then
    assertThatThrownBy(() -> tokens.issue(USER_ID)).isInstanceOf(IllegalStateException.class);
  }
}
