package org.mwolff.manban.accesstoken.domain;

import java.time.Instant;

/**
 * Persönliches API-Zugriffstoken (PAT). Persistiert wird nur {@code tokenHash};
 * der Klartext wird bei der Erstellung genau einmal ausgegeben.
 *
 * @param id          technische ID; {@code null} vor der Persistierung
 * @param userId      Besitzer
 * @param name        vom Nutzer vergebener Name
 * @param tokenHash   SHA-256-Hash des Klartext-Tokens
 * @param displayName Anzeigename (z. B. als Autor PAT-erzeugter Kommentare, ab B4)
 * @param createdAt   Erstellzeitpunkt
 * @param lastUsedAt  letzte Verwendung; {@code null} solange ungenutzt
 * @param revoked     ob das Token widerrufen wurde
 */
public record AccessToken(
        Long id,
        Long userId,
        String name,
        String tokenHash,
        String displayName,
        Instant createdAt,
        Instant lastUsedAt,
        boolean revoked) {

    public AccessToken withLastUsedAt(Instant when) {
        return new AccessToken(id, userId, name, tokenHash, displayName, createdAt, when, revoked);
    }

    public AccessToken asRevoked() {
        return new AccessToken(id, userId, name, tokenHash, displayName, createdAt, lastUsedAt, true);
    }
}
