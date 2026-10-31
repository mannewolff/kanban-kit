package org.mwolff.manban.push.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.time.Duration;
import org.mwolff.manban.common.ExcludeFromJacocoGeneratedReport;

/**
 * Stellt eine Nachricht an einen Push-Dienst zu (RFC 8030 §5): verschlüsselt nach RFC 8291,
 * signiert nach RFC 8292, mit {@code Urgency: high}, weil eine Nachricht über einen gescheiterten
 * oder hängenden Lauf nicht warten soll, bis das Handy aufwacht (Issue #1996).
 *
 * <p>Salt und Schlüsselpaar des Servers entstehen je Nachricht neu aus {@link SecureRandom}. Der
 * HTTP-Status geht unverändert an den Aufrufer: Was 404 oder 410 für das Abo bedeutet, entscheidet
 * der Meldejob, nicht der Versand.
 */
final class WebPushSender {

  /** Höchste Wartezeit auf die Antwort des Push-Dienstes. */
  static final Duration ZEITGRENZE = Duration.ofSeconds(10);

  private static final int SALT_LAENGE = 16;

  private final HttpClient httpClient;
  private final VapidSignatur vapidSignatur;
  private final SecureRandom zufall;

  WebPushSender(HttpClient httpClient, VapidSignatur vapidSignatur, SecureRandom zufall) {
    this.httpClient = httpClient;
    this.vapidSignatur = vapidSignatur;
    this.zufall = zufall;
  }

  /**
   * Sendet {@code klartext} an den Endpunkt eines Abos.
   *
   * @param ttl wie lange der Push-Dienst die Nachricht für ein nicht erreichbares Gerät aufhebt;
   *     auf ganze Sekunden abgerundet, negativ zählt als 0
   * @return der HTTP-Status des Push-Dienstes
   * @throws IOException wenn der Push-Dienst nicht antwortet
   * @throws InterruptedException wenn der Versand unterbrochen wird
   */
  int senden(URI endpunkt, byte[] p256dh, byte[] auth, byte[] klartext, Duration ttl)
      throws IOException, InterruptedException {
    byte[] salt = new byte[SALT_LAENGE];
    zufall.nextBytes(salt);
    byte[] rumpf =
        WebPushVerschluesselung.verschluesseln(klartext, p256dh, auth, salt, schluesselpaar());
    HttpRequest anfrage =
        HttpRequest.newBuilder(endpunkt)
            .timeout(ZEITGRENZE)
            .header("Authorization", vapidSignatur.kopfzeile(endpunkt))
            .header("Content-Encoding", "aes128gcm")
            .header("Content-Type", "application/octet-stream")
            .header("TTL", Long.toString(Math.max(0, ttl.toSeconds())))
            .header("Urgency", "high")
            .POST(HttpRequest.BodyPublishers.ofByteArray(rumpf))
            .build();
    return httpClient.send(anfrage, HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  // Coverage-Ausnahme (CLAUDE-java.md §5.4): Der catch-Zweig ist unerreichbar — der Generator für
  // "EC" und die Kurve secp256r1 gehören zu jeder JRE. Den Erfolgszweig deckt WebPushSenderTest ab.
  @ExcludeFromJacocoGeneratedReport
  private KeyPair schluesselpaar() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(P256.kurvenname(), zufall);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("P-256-Schlüsselpaar nicht erzeugbar", e);
    }
  }
}
