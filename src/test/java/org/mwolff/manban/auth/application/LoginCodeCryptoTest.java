package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.SecureTokens;

/** Codeerzeugung und Speicherform von Code und Link (Plan #1801, A3, E9). */
class LoginCodeCryptoTest {

  private static final String SECRET = "test-sitzungsgeheimnis-mit-genug-laenge";

  private static AuthProperties properties(String secret) {
    return new AuthProperties(null, null, secret, Duration.ofDays(30), true, null, null);
  }

  private static final LoginCodeCrypto CRYPTO = new LoginCodeCrypto(properties(SECRET));

  /** Liefert für {@code nextInt(bound)} stets denselben Wert — für führende Nullen. */
  private static final class FesterZufall extends SecureRandom {
    private static final long serialVersionUID = 1L;
    private final int wert;
    private int letzteGrenze;

    FesterZufall(int wert) {
      this.wert = wert;
    }

    @Override
    public int nextInt(int bound) {
      letzteGrenze = bound;
      return wert;
    }
  }

  private static String hmacHex(String secret, String message) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void codeHatGenauSechsZiffern() {
    for (int i = 0; i < 200; i++) {
      assertThat(CRYPTO.newCode()).matches("\\d{6}");
    }
  }

  @Test
  void fuehrendeNullenBleibenErhalten() {
    LoginCodeCrypto crypto = new LoginCodeCrypto(properties(SECRET), new FesterZufall(42));

    assertThat(crypto.newCode()).isEqualTo("000042");
  }

  @Test
  void kleinsterUndGroessterCode() {
    assertThat(new LoginCodeCrypto(properties(SECRET), new FesterZufall(0)).newCode())
        .isEqualTo("000000");
    assertThat(new LoginCodeCrypto(properties(SECRET), new FesterZufall(999_999)).newCode())
        .isEqualTo("999999");
  }

  @Test
  void ziehtGleichverteiltAusEinerMillion() {
    FesterZufall zufall = new FesterZufall(1);

    new LoginCodeCrypto(properties(SECRET), zufall).newCode();

    assertThat(zufall.letzteGrenze).isEqualTo(1_000_000);
  }

  @Test
  void gleicherCodeErgibtGleichenHmac() {
    assertThat(CRYPTO.codeHmac("123456")).isEqualTo(CRYPTO.codeHmac("123456"));
  }

  @Test
  void andererCodeErgibtAnderenHmac() {
    assertThat(CRYPTO.codeHmac("123456")).isNotEqualTo(CRYPTO.codeHmac("123457"));
  }

  @Test
  void andererSchluesselErgibtAnderenHmac() {
    LoginCodeCrypto anderer = new LoginCodeCrypto(properties("ein-ganz-anderes-geheimnis-123"));

    assertThat(anderer.codeHmac("123456")).isNotEqualTo(CRYPTO.codeHmac("123456"));
  }

  @Test
  void hmacTraegtDieDomaenentrennung() throws Exception {
    String hmac = CRYPTO.codeHmac("123456");

    assertThat(hmac).isEqualTo(hmacHex(SECRET, "login-code:123456"));
    assertThat(hmac).isNotEqualTo(hmacHex(SECRET, "123456"));
  }

  @Test
  void hmacIstKeinBlosserSha256() {
    assertThat(CRYPTO.codeHmac("123456")).isNotEqualTo(SecureTokens.sha256Hex("123456"));
  }

  @Test
  void linkTokenIstNeuUndUrlSicher() {
    String erstes = CRYPTO.newLinkToken();

    assertThat(erstes).matches("[A-Za-z0-9_-]{43}");
    assertThat(CRYPTO.newLinkToken()).isNotEqualTo(erstes);
  }

  @Test
  void linkHashIstSha256DesTokens() {
    assertThat(CRYPTO.linkHash("ein-token")).isEqualTo(SecureTokens.sha256Hex("ein-token"));
  }
}
