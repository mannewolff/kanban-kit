package org.mwolff.manban.common.token;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.mwolff.manban.common.SecureTokens;
import org.springframework.stereotype.Component;

/**
 * SHA-256-basierte Umsetzung von {@link TokenCryptoPort}. Der Klartext hat die Form {@code
 * tk_<64hex>} (32 Zufallsbytes); persistiert wird nur dessen SHA-256-Hash.
 */
@Component
public class Sha256TokenCryptoAdapter implements TokenCryptoPort {

  private static final SecureRandom RANDOM = new SecureRandom();

  @Override
  public GeneratedToken generate() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String plaintext = "tk_" + HexFormat.of().formatHex(bytes);
    return new GeneratedToken(plaintext, hash(plaintext));
  }

  @Override
  public String hash(String plaintext) {
    return SecureTokens.sha256Hex(plaintext);
  }
}
