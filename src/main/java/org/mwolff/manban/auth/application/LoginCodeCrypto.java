package org.mwolff.manban.auth.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.mwolff.manban.common.ExcludeFromJacocoGeneratedReport;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Erzeugung und Speicherform von Code und Link der Code-Anmeldung (Plan #1801, A3, E9).
 *
 * <p>Der Code hat sechs Ziffern aus {@link SecureRandom}, gleichverteilt über 000000–999999,
 * führende Nullen bleiben. Gespeichert wird er als HMAC-SHA256 mit dem Sitzungsgeheimnis {@code
 * manban.auth.session-secret}: Bei nur 10⁶ Möglichkeiten ließe sich ein bloßer SHA-256 bei
 * Lesezugriff auf die Datenbank in Sekunden zurückrechnen, der Schlüssel liegt dagegen nicht in der
 * Datenbank. Die feste Vorsilbe {@value #DOMAIN} trennt diese Signaturen von denen der
 * Sitzungs-Tokens, die mit demselben Geheimnis signiert werden.
 *
 * <p>Der Link trägt ein eigenes 256-Bit-Token aus {@link SecureTokens#newToken()}, gespeichert als
 * SHA-256 wie die übrigen Einmal-Tokens.
 *
 * <p>Eigene Klasse statt Erweiterung von {@code common.token.TokenCryptoPort}: Der Schlüssel hängt
 * am Sitzungsgeheimnis des Moduls {@code auth}, der gemeinsame Port bleibt schlüssellos.
 */
@Component
public class LoginCodeCrypto {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String DOMAIN = "login-code:";
  private static final int CODE_SPACE = 1_000_000;

  private final byte[] secret;
  private final SecureRandom random;

  /** Für Spring: Zufall aus einem eigenen {@link SecureRandom}. */
  @Autowired
  public LoginCodeCrypto(AuthProperties properties) {
    this(properties, new SecureRandom());
  }

  /** Für Tests: vorgegebener Zufall, etwa für führende Nullen. */
  LoginCodeCrypto(AuthProperties properties, SecureRandom random) {
    this.secret = properties.sessionSecret().getBytes(StandardCharsets.UTF_8);
    this.random = random;
  }

  /** Neuer sechsstelliger Code, mit führenden Nullen. */
  public String newCode() {
    return String.format("%06d", random.nextInt(CODE_SPACE));
  }

  /** HMAC-SHA256 (Hex) des Codes, mit Domänentrennung. */
  public String codeHmac(String code) {
    return HexFormat.of().formatHex(hmac(DOMAIN + code));
  }

  /** Neues Link-Token (256 Bit, URL-sicher). */
  public String newLinkToken() {
    return SecureTokens.newToken();
  }

  /** Speicherform des Link-Tokens. */
  public String linkHash(String linkToken) {
    return SecureTokens.sha256Hex(linkToken);
  }

  // Coverage-Ausnahme wie SignedSessionTokens#hmac: Der catch-Zweig ist unerreichbar — HmacSHA256
  // gehört zu jeder JRE, und das Sitzungsgeheimnis ist laut AuthProperties nie leer, sonst würfe
  // schon der SecretKeySpec-Konstruktor. Zum Kompilieren der geprüften Ausnahmen ist er zwingend;
  // die Fachlogik deckt codeHmac über LoginCodeCryptoTest ab.
  @ExcludeFromJacocoGeneratedReport
  private byte[] hmac(String data) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 nicht verfügbar", e);
    }
  }
}
