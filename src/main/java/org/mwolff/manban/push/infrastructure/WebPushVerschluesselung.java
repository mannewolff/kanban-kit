package org.mwolff.manban.push.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.mwolff.manban.common.ExcludeFromJacocoGeneratedReport;

/**
 * Verschlüsselt eine Push-Nachricht nach RFC 8291 im Inhaltsformat {@code aes128gcm} (RFC 8188):
 * ECDH auf P-256 zwischen dem Schlüsselpaar des Servers und dem {@code p256dh}-Schlüssel des Abos,
 * HKDF-SHA-256 mit dem {@code auth}-Geheimnis, AES-128-GCM in einem einzigen Datensatz (Issue
 * #1996, Plan #1989 E15: nur {@code java.security} und {@code javax.crypto}, kein BouncyCastle).
 *
 * <p>Salt und Schlüsselpaar kommen von außen, damit der Beispielvektor aus RFC 8291, Anhang A, sich
 * Byte für Byte nachrechnen lässt. Produktiv erzeugt {@link WebPushSender} beide je Nachricht neu
 * mit {@code SecureRandom} — ein wiederverwendetes Paar aus Salt und Schlüssel gäbe Schlüssel und
 * Nonce von AES-GCM zweimal aus.
 */
final class WebPushVerschluesselung {

  /** Datensatzgröße im Kopf; RFC 8291 §4 schreibt 4096 vor. */
  private static final int DATENSATZ = 4096;

  private static final int SALT_LAENGE = 16;
  private static final int AUTH_LAENGE = 16;
  private static final int SCHLUESSEL_LAENGE = 16;
  private static final int NONCE_LAENGE = 12;
  private static final int TAG_BITS = 128;
  private static final int TAG_LAENGE = TAG_BITS / 8;
  private static final int IKM_LAENGE = 32;

  /** Salt, Datensatzgröße, Länge der Schlüsselkennung, Schlüsselkennung. */
  private static final int KOPF_LAENGE = SALT_LAENGE + 4 + 1 + P256.PUNKT_LAENGE;

  /** Begrenzer des letzten Datensatzes (RFC 8188 §2), ohne weiteres Auffüllen. */
  private static final byte LETZTER_DATENSATZ = 0x02;

  /**
   * Größter Klartext, dessen Nachricht in die 4096 Bytes passt, die Push-Dienste mindestens
   * annehmen müssen (RFC 8030 §7.2): 4096 − Kopf (86) − Begrenzer (1) − GCM-Prüfsumme (16).
   */
  static final int MAX_KLARTEXT = DATENSATZ - KOPF_LAENGE - 1 - TAG_LAENGE;

  private static final byte[] KEY_INFO = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] CEK_INFO =
      "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] NONCE_INFO =
      "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

  private WebPushVerschluesselung() {}

  /**
   * Verschlüsselt {@code klartext} für ein Abo.
   *
   * @param klartext höchstens {@link #MAX_KLARTEXT} Bytes
   * @param p256dh öffentlicher Schlüssel des Abos als unkomprimierter Punkt (65 Bytes)
   * @param auth Authentifizierungsgeheimnis des Abos (16 Bytes)
   * @param salt 16 zufällige Bytes, je Nachricht neu
   * @param serverSchluesselpaar P-256-Paar des Servers, je Nachricht neu
   * @return die ganze Nachricht: Kopf nach RFC 8188 §2.1 und der verschlüsselte Datensatz
   * @throws IllegalArgumentException bei falscher Länge oder einem Punkt außerhalb der Kurve
   */
  static byte[] verschluesseln(
      byte[] klartext, byte[] p256dh, byte[] auth, byte[] salt, KeyPair serverSchluesselpaar) {
    if (klartext.length > MAX_KLARTEXT) {
      throw new IllegalArgumentException("Klartext länger als " + MAX_KLARTEXT + " Bytes");
    }
    if (auth.length != AUTH_LAENGE || salt.length != SALT_LAENGE) {
      throw new IllegalArgumentException("auth und salt müssen je 16 Bytes lang sein");
    }
    ECPublicKey abo = P256.oeffentlicherSchluessel(p256dh);
    byte[] server = P256.punkt((ECPublicKey) serverSchluesselpaar.getPublic());
    byte[] gemeinsam = ecdh(serverSchluesselpaar, abo);

    // RFC 8291 §3.4: IKM aus dem ECDH-Geheimnis, gebunden an auth und beide öffentlichen Schlüssel.
    byte[] keyInfo = verbinden(KEY_INFO, p256dh, server);
    byte[] ikm = hkdf(auth, gemeinsam, keyInfo, IKM_LAENGE);
    // RFC 8188 §2.2/§2.3: Inhaltsschlüssel und Nonce aus IKM und Salt.
    byte[] cek = hkdf(salt, ikm, CEK_INFO, SCHLUESSEL_LAENGE);
    byte[] nonce = hkdf(salt, ikm, NONCE_INFO, NONCE_LAENGE);

    byte[] datensatz = new byte[klartext.length + 1];
    System.arraycopy(klartext, 0, datensatz, 0, klartext.length);
    datensatz[klartext.length] = LETZTER_DATENSATZ;
    byte[] chiffre = aesGcm(cek, nonce, datensatz);

    return ByteBuffer.allocate(KOPF_LAENGE + chiffre.length)
        .put(salt)
        .putInt(DATENSATZ)
        .put((byte) server.length)
        .put(server)
        .put(chiffre)
        .array();
  }

  /** ECDH; ein Punkt außerhalb der Kurve ist ein Fehler des Abos, nicht des Servers. */
  private static byte[] ecdh(KeyPair server, ECPublicKey abo) {
    KeyAgreement vereinbarung = keyAgreement();
    try {
      vereinbarung.init(server.getPrivate());
      vereinbarung.doPhase(abo, true);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException("Schlüssel des Abos ist kein Punkt auf P-256", e);
    }
    return vereinbarung.generateSecret();
  }

  private static byte[] verbinden(byte[]... teile) {
    int laenge = 0;
    for (byte[] teil : teile) {
      laenge += teil.length;
    }
    ByteBuffer puffer = ByteBuffer.allocate(laenge);
    for (byte[] teil : teile) {
      puffer.put(teil);
    }
    return puffer.array();
  }

  // Coverage-Ausnahme (CLAUDE-java.md §5.4) für die drei folgenden Methoden: Ihr catch-Zweig ist
  // unerreichbar. ECDH, HKDF-SHA256 (JEP 510, seit JDK 25 fest) und AES/GCM/NoPadding gehören zu
  // jeder JRE; Schlüssel- und Nonce-Längen sind hier feste Konstanten, und jeder Aufruf hat einen
  // frischen Cipher — die Sperre gegen eine wiederholte GCM-Nonce kann nicht greifen. Die
  // Erfolgszweige deckt der Vektortest in WebPushVerschluesselungTest Byte für Byte ab.
  @ExcludeFromJacocoGeneratedReport
  private static KeyAgreement keyAgreement() {
    try {
      return KeyAgreement.getInstance("ECDH");
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("ECDH nicht verfügbar", e);
    }
  }

  @ExcludeFromJacocoGeneratedReport
  private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int laenge) {
    try {
      return KDF.getInstance("HKDF-SHA256")
          .deriveData(
              HKDFParameterSpec.ofExtract().addSalt(salt).addIKM(ikm).thenExpand(info, laenge));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HKDF-SHA256 nicht verfügbar", e);
    }
  }

  @ExcludeFromJacocoGeneratedReport
  private static byte[] aesGcm(byte[] schluessel, byte[] nonce, byte[] daten) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(schluessel, "AES"),
          new GCMParameterSpec(TAG_BITS, nonce));
      return cipher.doFinal(daten);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-128-GCM nicht verfügbar", e);
    }
  }
}
