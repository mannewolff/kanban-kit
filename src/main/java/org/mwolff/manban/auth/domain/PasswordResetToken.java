package org.mwolff.manban.auth.domain;

import java.time.Instant;

/**
 * Passwort-Reset-Token. Persistiert wird nur der Hash; das Klartext-Token erhält der
 * Nutzer einmalig per E-Mail.
 *
 * @param id        technische ID; {@code null} vor der Persistierung
 * @param userId    zugehöriger Benutzer
 * @param tokenHash SHA-256-Hash des Klartext-Tokens
 * @param expiresAt Ablaufzeitpunkt
 * @param usedAt    Zeitpunkt der Einlösung; {@code null} solange ungenutzt
 */
public record PasswordResetToken(
        Long id,
        Long userId,
        String tokenHash,
        Instant expiresAt,
        Instant usedAt) {

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public PasswordResetToken markUsed(Instant when) {
        return new PasswordResetToken(id, userId, tokenHash, expiresAt, when);
    }
}
