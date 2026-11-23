package org.mwolff.manban.fremdboard.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.ExcludeFromJacocoGeneratedReport;
import org.mwolff.manban.fremdboard.application.FremdboardProperties;
import org.mwolff.manban.fremdboard.application.ZugangsSchutz;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Verschlüsselt den hinterlegten Lesezugang eines fremden Boards (Issue #2157, Plan #2152 A13):
 * AES-GCM mit dem Schlüssel aus {@code MANBAN_SECRET_KEY} (32 Byte, hex). Ein Datenbankauszug
 * allein verrät den Zugang damit nicht.
 *
 * <p>Gespeichert wird Base64 über Startwert (12 Byte, je Verschlüsselung neu aus {@link
 * SecureRandom}) und Geheimtext samt Prüfmarke (128 Bit). Fehlt der Schlüssel, ist der Tresor nicht
 * verfügbar und das Anbinden gesperrt. Ein ungültiger Schlüssel bricht den Start ab. Lässt sich ein
 * gespeicherter Wert nicht entschlüsseln, wirft er {@link ZugangNichtLesbarException}; keine
 * Meldung nennt Zugang oder Schlüssel.
 */
@Component
public final class ZugangsTresor implements ZugangsSchutz {

  private static final String VERFAHREN = "AES/GCM/NoPadding";
  private static final int SCHLUESSEL_LAENGE = 32;
  private static final int STARTWERT_LAENGE = 12;
  private static final int MARKE_BITS = 128;
  private static final int MARKE_LAENGE = MARKE_BITS / Byte.SIZE;
  private static final String HINWEIS_SCHLUESSEL =
      "MANBAN_SECRET_KEY muss 32 Byte hex sein (64 Zeichen 0-9a-f), erzeugen mit openssl rand -hex"
          + " 32";

  private final @Nullable SecretKeySpec schluessel;
  private final SecureRandom zufall;

  @Autowired
  ZugangsTresor(FremdboardProperties properties) {
    this(properties.secretKey(), new SecureRandom());
  }

  ZugangsTresor(@Nullable String schluesselHex, SecureRandom zufall) {
    this.schluessel = schluesselAus(schluesselHex);
    this.zufall = zufall;
  }

  /** Wahr, wenn ein Schlüssel gesetzt ist; sonst ist das Anbinden gesperrt. */
  @Override
  public boolean verfuegbar() {
    return schluessel != null;
  }

  /**
   * Verschlüsselt {@code klartext} zum Speichern.
   *
   * @throws IllegalStateException wenn kein Schlüssel gesetzt ist
   */
  @Override
  public String verschluesseln(String klartext) {
    if (schluessel == null) {
      throw new IllegalStateException(
          "Kein Schlüssel für den Zugang gesetzt (MANBAN_SECRET_KEY) — Anbinden ist gesperrt");
    }
    byte[] startwert = new byte[STARTWERT_LAENGE];
    zufall.nextBytes(startwert);
    byte[] geheimtext =
        gcmVerschluesseln(schluessel, startwert, klartext.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder()
        .encodeToString(
            ByteBuffer.allocate(startwert.length + geheimtext.length)
                .put(startwert)
                .put(geheimtext)
                .array());
  }

  /**
   * Entschlüsselt einen mit {@link #verschluesseln(String)} gespeicherten Wert.
   *
   * @throws ZugangNichtLesbarException wenn kein Schlüssel gesetzt ist oder der Wert sich mit ihm
   *     nicht entschlüsseln lässt
   */
  public String entschluesseln(String gespeichert) {
    if (schluessel == null) {
      throw new ZugangNichtLesbarException(
          "Kein Schlüssel für den Zugang gesetzt (MANBAN_SECRET_KEY) — Zugang nicht lesbar");
    }
    byte[] roh;
    try {
      roh = Base64.getDecoder().decode(gespeichert);
    } catch (IllegalArgumentException e) {
      throw new ZugangNichtLesbarException("Gespeicherter Zugang ist kein gültiger Wert", e);
    }
    if (roh.length < STARTWERT_LAENGE + MARKE_LAENGE) {
      throw new ZugangNichtLesbarException("Gespeicherter Zugang ist zu kurz");
    }
    try {
      Cipher verfahren = Cipher.getInstance(VERFAHREN);
      verfahren.init(
          Cipher.DECRYPT_MODE,
          schluessel,
          new GCMParameterSpec(MARKE_BITS, roh, 0, STARTWERT_LAENGE));
      byte[] klartext = verfahren.doFinal(roh, STARTWERT_LAENGE, roh.length - STARTWERT_LAENGE);
      return new String(klartext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new ZugangNichtLesbarException(
          "Gespeicherter Zugang lässt sich mit dem Schlüssel aus MANBAN_SECRET_KEY nicht"
              + " entschlüsseln",
          e);
    }
  }

  /**
   * Ob sich der gespeicherte Wert entschlüsseln lässt (Plan #2152 A13): Die Einstellung zeigt
   * sonst, dass der Zugang neu hinterlegt werden muss.
   */
  @Override
  public boolean lesbar(String gespeichert) {
    try {
      entschluesseln(gespeichert);
      return true;
    } catch (ZugangNichtLesbarException _) {
      return false;
    }
  }

  private static @Nullable SecretKeySpec schluesselAus(@Nullable String schluesselHex) {
    if (schluesselHex == null || schluesselHex.isBlank()) {
      return null;
    }
    byte[] bytes;
    try {
      bytes = HexFormat.of().parseHex(schluesselHex.strip());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(HINWEIS_SCHLUESSEL, e);
    }
    if (bytes.length != SCHLUESSEL_LAENGE) {
      throw new IllegalStateException(HINWEIS_SCHLUESSEL);
    }
    return new SecretKeySpec(bytes, "AES");
  }

  // Coverage-Ausnahme (CLAUDE-java.md §5.4): Der catch-Zweig ist unerreichbar — AES/GCM/NoPadding
  // gehört zu jeder JRE, Schlüssel (32 Byte) und Startwert (12 Byte, frisch) sind geprüft. Den
  // Erfolgszweig decken die Hin-und-Rückweg-Tests in ZugangsTresorTest ab.
  @ExcludeFromJacocoGeneratedReport
  private static byte[] gcmVerschluesseln(
      SecretKeySpec schluessel, byte[] startwert, byte[] klartext) {
    try {
      Cipher verfahren = Cipher.getInstance(VERFAHREN);
      verfahren.init(Cipher.ENCRYPT_MODE, schluessel, new GCMParameterSpec(MARKE_BITS, startwert));
      return verfahren.doFinal(klartext);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("AES-GCM nicht verfügbar", e);
    }
  }
}
