package org.mwolff.manban.auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.AuthProperties;
import org.mwolff.manban.auth.application.SessionGenerations;
import org.mwolff.manban.auth.application.SessionTokens;
import org.mwolff.manban.common.ExcludeFromJacocoGeneratedReport;
import org.springframework.stereotype.Component;

/**
 * HMAC-Adapter für den Port {@link SessionTokens}: serverseitig prüfbares Session-Token {@code
 * base64url(payload).base64url(hmac)} mit {@code payload =
 * "<userId>:<expiryEpochMillis>:<generation>"}, signiert per HMAC-SHA256.
 *
 * <p>Kein Server-Session-Store nötig; der Signaturschlüssel muss über Neustarts/Instanzen stabil
 * sein (siehe AuthProperties).
 *
 * <p><strong>Nicht mehr rein zustandslos prüfbar (Issue #885):</strong> Zur Signatur und zum Ablauf
 * tritt ein bewusster Serverbezug — die Sitzungs-Generation des Kontos aus {@link
 * SessionGenerations}. Sie ist der Hebel, mit dem sich bestehende Anmeldesitzungen vorzeitig
 * beenden lassen: Das Token trägt die Generation seiner Ausstellung, und ein Hochzählen am Konto
 * entwertet damit jedes zuvor ausgestellte Token. Die Gegenprüfung sitzt hier und nicht in einem
 * eigenen Filter (Plan #883, E2), damit es genau eine Stelle gibt, an der ein Session-Token für
 * gültig erklärt wird. Der Preis ist ein Primärschlüssel-Lookup je Prüfung.
 *
 * <p>Tokens im früheren Zwei-Feld-Format sind ungültig (Plan #883, E3): Es gibt keinen Toleranzpfad
 * „fehlendes Feld gilt als Generation 0". Beim Ausrollen müssen sich angemeldete Nutzer deshalb
 * einmalig neu anmelden.
 */
// PMD.AvoidCatchingGenericException: (1) beim Verifizieren wird jede RuntimeException eines
// manipulierten Tokens bewusst als "ungültig" (leeres Ergebnis) behandelt, ohne Details zu leaken;
// (2) die HMAC-Berechnung bündelt die geprüften JCA-Ausnahmen (NoSuchAlgorithm/InvalidKey) für den
// garantiert vorhandenen HmacSHA256-Provider zu IllegalStateException. Beides ist Krypto-Plumbing.
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Component
public class SignedSessionTokens implements SessionTokens {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private static final int PAYLOAD_FIELDS = 3;

  private final byte[] secret;
  private final java.time.Duration ttl;
  private final Clock clock;
  private final SessionGenerations generations;

  public SignedSessionTokens(
      AuthProperties properties, Clock clock, SessionGenerations generations) {
    this.secret = properties.sessionSecret().getBytes(StandardCharsets.UTF_8);
    this.ttl = properties.sessionTtl();
    this.clock = clock;
    this.generations = generations;
  }

  /**
   * Signiertes Token für den Benutzer, gültig für die konfigurierte TTL und für die aktuelle
   * Sitzungs-Generation des Kontos.
   *
   * @throws IllegalStateException wenn es das Konto nicht gibt — der Aufrufer hat den Benutzer
   *     gerade erfolgreich angemeldet, das darf nicht vorkommen.
   */
  @Override
  public String issue(long userId) {
    long generation =
        generations
            .current(userId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Kein Konto zur soeben angemeldeten userId: " + userId));
    long expiry = clock.instant().plus(ttl).toEpochMilli();
    String payload = userId + ":" + expiry + ":" + generation;
    String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return encodedPayload + "." + ENCODER.encodeToString(hmac(encodedPayload));
  }

  /**
   * Verifiziert Signatur, Ablauf und Sitzungs-Generation; liefert die userId oder leer bei
   * Ungültigkeit. Der Parameter ist bewusst {@code @Nullable}: der Verifizierer toleriert {@code
   * null} defensiv (leeres Ergebnis statt NPE), auch wenn der aktuelle Aufrufer stets non-null
   * übergibt.
   */
  @Override
  public OptionalLong verify(@Nullable String token) {
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
    } catch (IllegalArgumentException _) {
      return OptionalLong.empty();
    }
    if (!MessageDigest.isEqual(expected, provided)) {
      return OptionalLong.empty();
    }

    try {
      String payload = new String(DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
      String[] fields = payload.split(":", -1);
      if (fields.length != PAYLOAD_FIELDS) {
        return OptionalLong.empty();
      }
      long userId = Long.parseLong(fields[0]);
      long expiry = Long.parseLong(fields[1]);
      long generation = Long.parseLong(fields[2]);
      if (clock.instant().toEpochMilli() > expiry) {
        return OptionalLong.empty();
      }
      // Gleichheit über OptionalLong statt über isEmpty() + Vergleich: Ein unbekanntes Konto hat
      // keine Generation und ist damit ungleich jeder Tokenangabe — der unsichere Zweig "leer gilt
      // als gültig" (Plan #883, E9) entsteht so gar nicht erst.
      if (!generations.current(userId).equals(OptionalLong.of(generation))) {
        return OptionalLong.empty();
      }
      return OptionalLong.of(userId);
    } catch (RuntimeException _) {
      return OptionalLong.empty();
    }
  }

  // §5.4/§5.2: Der catch bündelt die *geprüften* JCA-Ausnahmen (NoSuchAlgorithmException aus
  // Mac.getInstance, InvalidKeyException aus init) für den vom JCA garantiert vorhandenen
  // HmacSHA256-Provider. Der Zweig ist nachweislich nicht erreichbar (der Schlüssel ist über die
  // AuthProperties nie leer, sonst würde bereits der SecretKeySpec-Konstruktor werfen), aber zum
  // Kompilieren der Checked Exceptions zwingend. Die Fachlogik (Signatur/Ablauf) ist über die
  // issue/verify-Round-Trip-Tests voll abgedeckt; deshalb Coverage-Ausnahme statt Entfernung.
  @ExcludeFromJacocoGeneratedReport
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
