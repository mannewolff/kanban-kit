package org.mwolff.manban.accesstoken.domain;

import java.time.Instant;

/**
 * Persönliches API-Zugriffstoken (PAT). Persistiert wird nur {@code tokenHash}; der Klartext wird
 * bei der Erstellung genau einmal ausgegeben.
 *
 * <p>Optional an ein Projekt + Board gebunden ({@code projectId}/{@code boardId}): ein solches
 * Token adressiert genau dieses Board (Kanban-Compat-API, #45), ähnlich einem
 * GitHub-Fine-grained-PAT. Sind beide {@code null}, ist das Token ungebunden.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param userId Besitzer
 * @param projectId gebundenes Projekt; {@code null} = ungebunden
 * @param boardId gebundenes Board; {@code null} = ungebunden
 * @param name vom Nutzer vergebener Name
 * @param tokenHash SHA-256-Hash des Klartext-Tokens
 * @param displayName Anzeigename (z. B. als Autor PAT-erzeugter Kommentare)
 * @param createdAt Erstellzeitpunkt
 * @param lastUsedAt letzte Verwendung; {@code null} solange ungenutzt
 * @param revoked ob das Token widerrufen wurde
 */
public record AccessToken(
    Long id,
    Long userId,
    Long projectId,
    Long boardId,
    String name,
    String tokenHash,
    String displayName,
    Instant createdAt,
    Instant lastUsedAt,
    boolean revoked) {

  /** Ob das Token an ein Projekt + Board gebunden ist. */
  public boolean isBound() {
    return projectId != null && boardId != null;
  }

  public AccessToken withLastUsedAt(Instant when) {
    return new AccessToken(
        id, userId, projectId, boardId, name, tokenHash, displayName, createdAt, when, revoked);
  }

  public AccessToken asRevoked() {
    return new AccessToken(
        id, userId, projectId, boardId, name, tokenHash, displayName, createdAt, lastUsedAt, true);
  }
}
