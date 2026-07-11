package org.mwolff.manban.auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.mwolff.manban.auth.application.AuthProperties;
import org.springframework.stereotype.Component;

/**
 * Zustandsloses, serverseitig prüfbares Session-Token: {@code base64url(payload).base64url(hmac)}
 * mit {@code payload = "<userId>:<expiryEpochMillis>"}, signiert per HMAC-SHA256.
 *
 * <p>Kein Server-Session-Store nötig; die Gültigkeit ergibt sich aus Signatur + Ablauf. Der
 * Signaturschlüssel muss über Neustarts/Instanzen stabil sein (siehe AuthProperties).
 */
// PMD.AvoidCatchingGenericException: (1) beim Verifizieren wird jede RuntimeException eines
// manipulierten Tokens bewusst als "ungültig" (leeres Ergebnis) behandelt, ohne Details zu leaken;
// (2) die HMAC-Berechnung bündelt die geprüften JCA-Ausnahmen (NoSuchAlgorithm/InvalidKey) für den
// garantiert vorhandenen HmacSHA256-Provider zu IllegalStateException. Beides ist Krypto-Plumbing.
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Component
public class SignedSessionTokens {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private final byte[] secret;
  private final java.time.Duration ttl;
  private final Clock clock;

  public SignedSessionTokens(AuthProperties properties, Clock clock) {
    this.secret = properties.sessionSecret().getBytes(StandardCharsets.UTF_8);
    this.ttl = properties.sessionTtl();
    this.clock = clock;
  }

  /** Signiertes Token für den Benutzer, gültig für die konfigurierte TTL. */
  public String issue(long userId) {
    long expiry = clock.instant().plus(ttl).toEpochMilli();
    String payload = userId + ":" + expiry;
    String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return encodedPayload + "." + ENCODER.encodeToString(hmac(encodedPayload));
  }

  /** Verifiziert Signatur und Ablauf; liefert die userId oder leer bei Ungültigkeit. */
  public OptionalLong verify(String token) {
    if (token == null) {
      return OptionalLong.empty();
    }
    int dot = token.indexOf('.');
    if (dot < 0) {
      return OptionalLong.empty();
    }
    String encodedPayload = token.substring(0, dot);
    String providedSignature = token.substring(dot + 1);

    byte[] expected = hmac(encodedPayload);
    byte[] provided;
    try {
      provided = DECODER.decode(providedSignature);
    } catch (IllegalArgumentException e) {
      return OptionalLong.empty();
    }
    if (!MessageDigest.isEqual(expected, provided)) {
      return OptionalLong.empty();
    }

    try {
      String payload = new String(DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
      int colon = payload.indexOf(':');
      long userId = Long.parseLong(payload.substring(0, colon));
      long expiry = Long.parseLong(payload.substring(colon + 1));
      if (clock.instant().toEpochMilli() > expiry) {
        return OptionalLong.empty();
      }
      return OptionalLong.of(userId);
    } catch (RuntimeException e) {
      return OptionalLong.empty();
    }
  }

  private byte[] hmac(String data) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("HMAC-Berechnung fehlgeschlagen", e);
    }
  }
}
