package org.mwolff.manban.push.infrastructure;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import org.mwolff.manban.common.ExcludeFromJacocoGeneratedReport;

/**
 * Schlüssel der Kurve P-256 in der Form, die Web Push überträgt: der öffentliche Schlüssel als
 * unkomprimierter Punkt ({@code 0x04 || x || y}, 65 Bytes, SEC 1 §2.3.3), der private als Skalar
 * von 32 Bytes (Issue #1996, Plan #1989 E15 — allein mit {@code java.security}).
 *
 * <p>Geprüft wird hier nur die Form. Ob ein Punkt auf der Kurve liegt, prüft das JDK beim
 * Schlüsselaustausch ({@link WebPushVerschluesselung}), ob ein Skalar im Bereich liegt und zum
 * Punkt passt, beim Signieren ({@link VapidSchluessel}) — die {@code KeyFactory} nimmt beides
 * ungeprüft an.
 */
final class P256 {

  /** Länge eines unkomprimierten Punkts: Kennbyte plus zwei Koordinaten zu je 32 Bytes. */
  static final int PUNKT_LAENGE = 65;

  private static final int KOORDINATEN_LAENGE = 32;
  private static final byte UNKOMPRIMIERT = 0x04;
  private static final String KURVEN_KENNUNG = "secp256r1";
  private static final ECParameterSpec KURVE = kurveLaden();

  private P256() {}

  /**
   * Liest einen unkomprimierten Punkt.
   *
   * @throws IllegalArgumentException bei falscher Länge oder falschem Kennbyte
   */
  static ECPublicKey oeffentlicherSchluessel(byte[] punkt) {
    if (punkt.length != PUNKT_LAENGE || punkt[0] != UNKOMPRIMIERT) {
      throw new IllegalArgumentException("Kein unkomprimierter P-256-Punkt (65 Bytes, 0x04)");
    }
    BigInteger x = new BigInteger(1, Arrays.copyOfRange(punkt, 1, 1 + KOORDINATEN_LAENGE));
    BigInteger y =
        new BigInteger(1, Arrays.copyOfRange(punkt, 1 + KOORDINATEN_LAENGE, PUNKT_LAENGE));
    return oeffentlich(new ECPublicKeySpec(new ECPoint(x, y), KURVE));
  }

  /**
   * Baut ein Schlüsselpaar aus Punkt und Skalar.
   *
   * @throws IllegalArgumentException bei falscher Länge eines Teils
   */
  static KeyPair schluesselpaar(byte[] punkt, byte[] skalar) {
    if (skalar.length != KOORDINATEN_LAENGE) {
      throw new IllegalArgumentException("Kein P-256-Skalar (32 Bytes)");
    }
    return new KeyPair(
        oeffentlicherSchluessel(punkt),
        privat(new ECPrivateKeySpec(new BigInteger(1, skalar), KURVE)));
  }

  /** Schreibt einen öffentlichen Schlüssel als unkomprimierten Punkt. */
  static byte[] punkt(ECPublicKey schluessel) {
    byte[] punkt = new byte[PUNKT_LAENGE];
    punkt[0] = UNKOMPRIMIERT;
    koordinateSchreiben(schluessel.getW().getAffineX(), punkt, 1);
    koordinateSchreiben(schluessel.getW().getAffineY(), punkt, 1 + KOORDINATEN_LAENGE);
    return punkt;
  }

  /** Die Kurve für einen Schlüsselgenerator. */
  static ECGenParameterSpec kurvenname() {
    return new ECGenParameterSpec(KURVEN_KENNUNG);
  }

  /**
   * Schreibt eine Koordinate rechtsbündig auf genau 32 Bytes: {@code toByteArray} liefert ein
   * führendes Vorzeichenbyte (33 Bytes) oder bei kleinen Werten weniger als 32.
   */
  private static void koordinateSchreiben(BigInteger wert, byte[] ziel, int ab) {
    byte[] roh = wert.toByteArray();
    int laenge = Math.min(roh.length, KOORDINATEN_LAENGE);
    System.arraycopy(roh, roh.length - laenge, ziel, ab + KOORDINATEN_LAENGE - laenge, laenge);
  }

  // Coverage-Ausnahme (CLAUDE-java.md §5.4) für die drei folgenden Methoden: Ihr catch-Zweig ist
  // unerreichbar. "EC" und secp256r1 gehören zu jeder JRE (SunEC), und die KeyFactory erzeugt aus
  // einer Spezifikation mit den Parametern dieser Kurve jeden Schlüssel, ohne zu prüfen — Kurve und
  // Bereich prüft erst der Schlüsselaustausch bzw. die Signatur, deren Fehler die Aufrufer
  // abbilden.
  // Die Erfolgszweige decken WebPushVerschluesselungTest, VapidSignaturTest und
  // PushSchluesselStartupCheckTest ab.
  @ExcludeFromJacocoGeneratedReport
  private static ECPublicKey oeffentlich(ECPublicKeySpec spec) {
    try {
      return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(spec);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("EC-Schlüssel nicht erzeugbar", e);
    }
  }

  @ExcludeFromJacocoGeneratedReport
  private static PrivateKey privat(ECPrivateKeySpec spec) {
    try {
      return KeyFactory.getInstance("EC").generatePrivate(spec);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("EC-Schlüssel nicht erzeugbar", e);
    }
  }

  @ExcludeFromJacocoGeneratedReport
  private static ECParameterSpec kurveLaden() {
    try {
      AlgorithmParameters parameter = AlgorithmParameters.getInstance("EC");
      parameter.init(kurvenname());
      return parameter.getParameterSpec(ECParameterSpec.class);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Das JDK kennt die Kurve P-256 nicht", e);
    }
  }
}
