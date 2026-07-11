package org.mwolff.manban.common.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.token.TokenCryptoPort.GeneratedToken;

/** Unit-Tests des SHA-256-Token-Krypto-Adapters. */
class Sha256TokenCryptoAdapterTest {

  private Sha256TokenCryptoAdapter adapter;

  private static String sha256Hex(String value) throws NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  @BeforeEach
  void setUp() {
    adapter = new Sha256TokenCryptoAdapter();
  }

  @Test
  void generate_producesPrefixedHexPlaintext() {
    // When
    GeneratedToken token = adapter.generate();

    // Then
    assertThat(token.plaintext()).matches("tk_[0-9a-f]{64}");
  }

  @Test
  void generate_hashMatchesSha256OfPlaintext() throws NoSuchAlgorithmException {
    // When
    GeneratedToken token = adapter.generate();

    // Then
    assertThat(token.hash()).isEqualTo(sha256Hex(token.plaintext()));
  }

  @Test
  void generate_producesDistinctTokensAcrossCalls() {
    // When
    GeneratedToken first = adapter.generate();
    GeneratedToken second = adapter.generate();

    // Then
    assertThat(first.plaintext()).isNotEqualTo(second.plaintext());
  }

  @Test
  void hash_returnsSha256HexOfInput() throws NoSuchAlgorithmException {
    // When
    String hash = adapter.hash("tk_sample");

    // Then
    assertThat(hash).isEqualTo(sha256Hex("tk_sample"));
  }
}
