package org.mwolff.manban.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Verhaltenstests der Ablauf-/Einlöse-Logik eines {@link PasswordResetToken}. */
class PasswordResetTokenTest {

    private static final Instant EXPIRES = Instant.parse("2026-01-02T03:04:05Z");

    private static PasswordResetToken token(Instant usedAt) {
        return new PasswordResetToken(1L, 7L, "hash", EXPIRES, usedAt);
    }

    @Test
    void isUsed_returnsTrue_whenUsedAtSet() {
        // Given / When / Then
        assertThat(token(EXPIRES).isUsed()).isTrue();
    }

    @Test
    void isUsed_returnsFalse_whenUsedAtNull() {
        // Given / When / Then
        assertThat(token(null).isUsed()).isFalse();
    }

    @Test
    void isExpired_returnsTrue_whenExpiresBeforeNow() {
        // Given / When / Then
        assertThat(token(null).isExpired(EXPIRES.plusSeconds(1))).isTrue();
    }

    @Test
    void isExpired_returnsFalse_whenExpiresAfterNow() {
        // Given / When / Then
        assertThat(token(null).isExpired(EXPIRES.minusSeconds(1))).isFalse();
    }

    @Test
    void isExpired_returnsFalse_atExactExpiry() {
        // Given / When / Then — isBefore ist strikt, der Ablaufmoment gilt noch nicht als abgelaufen.
        assertThat(token(null).isExpired(EXPIRES)).isFalse();
    }

    @Test
    void markUsed_setsUsedAt() {
        // Given / When / Then
        assertThat(token(null).markUsed(EXPIRES).usedAt()).isEqualTo(EXPIRES);
    }
}
