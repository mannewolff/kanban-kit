package org.mwolff.manban.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Erzeugen und Hashen von Geheimnis-Tokens (E-Mail-Verifikation, Passwort-Reset, PAT).
 *
 * <p>Im Klartext ausgegebene Tokens werden nur einmalig zugestellt; persistiert wird
 * ausschließlich ihr SHA-256-Hash.
 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private SecureTokens() {
    }

    /** Neues, URL-sicheres Zufalls-Token (256 Bit Entropie). */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /** SHA-256-Hash (Hex, lowercase) des übergebenen Klartexts. */
    public static String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
