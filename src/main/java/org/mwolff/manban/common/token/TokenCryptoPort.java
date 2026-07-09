package org.mwolff.manban.common.token;

/**
 * Erzeugen und Hashen von API-Zugriffstokens (PAT). Aus der Toolbox portiert.
 * Format des Klartext-Tokens: {@code tk_<64 Hex-Zeichen>}.
 */
public interface TokenCryptoPort {

    /** Neues Token: Klartext (einmalig ausgeben) + zugehöriger Hash (persistieren). */
    GeneratedToken generate();

    /** Hash eines Klartext-Tokens (zur Auflösung eingehender Header-Werte). */
    String hash(String plaintext);

    /**
     * @param plaintext einmalig auszugebender Klartext ({@code tk_<hex>})
     * @param hash      zu persistierender Hash
     */
    record GeneratedToken(String plaintext, String hash) {
    }
}
