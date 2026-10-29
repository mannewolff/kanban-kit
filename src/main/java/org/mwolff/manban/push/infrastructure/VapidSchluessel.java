package org.mwolff.manban.push.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import org.mwolff.manban.common.ExcludeFromJacocoGeneratedReport;

/**
 * Das VAPID-Schlüsselpaar des Servers (RFC 8292), gelesen aus den base64url-Werten, die {@code
 * scripts/vapid-schluessel.mjs} ausgibt: der öffentliche Schlüssel als unkomprimierter Punkt, der
 * private als Skalar (Issue #1996).
 *
 * <p>{@link #aus} prüft auch, dass beide Hälften zusammengehören: Ein vertauschter oder fremder
 * privater Schlüssel fiele sonst erst beim Push-Dienst auf, mit einem 403 für jede Nachricht.
 *
 * <p>{@code toString} nennt keinen Schlüsselwert.
 */
final class VapidSchluessel {

  /** ES256 mit der Signatur als {@code r || s} (je 32 Bytes), wie JWS sie verlangt (RFC 7518). */
  static final String ES256 = "SHA256withECDSAinP1363Format";

  private static final byte[] PROBE = "manban-vapid-probe".getBytes(StandardCharsets.US_ASCII);

  private final KeyPair paar;
  private final String oeffentlicherWert;

  private VapidSchluessel(KeyPair paar, String oeffentlicherWert) {
    this.paar = paar;
    this.oeffentlicherWert = oeffentlicherWert;
  }

  /**
   * Liest und prüft ein Paar.
   *
   * @throws IllegalArgumentException wenn ein Wert kein base64url, kein P-256-Schlüssel ist oder
   *     beide nicht zusammengehören — die Meldung nennt keinen Wert
   */
  static VapidSchluessel aus(String oeffentlich, String privat) {
    KeyPair paar;
    try {
      paar =
          P256.schluesselpaar(
              Base64.getUrlDecoder().decode(oeffentlich), Base64.getUrlDecoder().decode(privat));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("VAPID-Schlüssel nicht lesbar", e);
    }
    if (!passtZusammen(paar)) {
      throw new IllegalArgumentException("VAPID-Schlüssel gehören nicht zusammen");
    }
    return new VapidSchluessel(paar, oeffentlich);
  }

  KeyPair schluesselpaar() {
    return paar;
  }

  /** Der öffentliche Schlüssel als base64url, für {@code k=} in der Kopfzeile. */
  String oeffentlich() {
    return oeffentlicherWert;
  }

  @Override
  public String toString() {
    return "VapidSchluessel[***]";
  }

  /**
   * Signiert eine Probe mit dem privaten und prüft sie mit dem öffentlichen Schlüssel. Ein Punkt
   * außerhalb der Kurve oder ein Skalar außerhalb von [1, n−1] lässt schon {@code init*} scheitern.
   */
  private static boolean passtZusammen(KeyPair paar) {
    try {
      Signature signieren = signatur();
      signieren.initSign(paar.getPrivate());
      signieren.update(PROBE);
      byte[] probe = signieren.sign();
      Signature pruefen = signatur();
      pruefen.initVerify(paar.getPublic());
      pruefen.update(PROBE);
      return pruefen.verify(probe);
    } catch (InvalidKeyException | SignatureException e) {
      return false;
    }
  }

  // Coverage-Ausnahme (CLAUDE-java.md §5.4): Der catch-Zweig ist unerreichbar — ECDSA mit SHA-256
  // im P1363-Format gehört zu jeder JRE (SunEC). Den Erfolgszweig decken VapidSignaturTest und
  // PushSchluesselStartupCheckTest ab.
  @ExcludeFromJacocoGeneratedReport
  static Signature signatur() {
    try {
      return Signature.getInstance(ES256);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("ES256 nicht verfügbar", e);
    }
  }
}
