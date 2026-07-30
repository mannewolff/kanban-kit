package org.mwolff.manban.accesstoken.application;

import org.jspecify.annotations.Nullable;

/**
 * Auflösungsergebnis eines eingehenden {@code X-Kanban-Token}: der Besitzer plus die optionale
 * Projekt-/Board-Bindung. Wird vom {@code PatAuthenticationFilter} an die Authentication-{@code
 * details} gehängt, damit die Kanban-Compat-API (#45) das gebundene Board ohne zweiten Token-Lookup
 * kennt.
 *
 * @param userId Besitzer des Tokens
 * @param tokenId technische Token-ID
 * @param projectId gebundenes Projekt; {@code null} = ungebundenes Token
 * @param boardId gebundenes Board; {@code null} = ungebundenes Token
 * @param tokenName Anzeigename des Tokens ({@code AccessToken.displayName}) — für die
 *     Herkunfts-Kennzeichnung im Aktivitätsverlauf (Issue #517), ohne zweiten Token-Lookup
 */
public record KanbanPrincipal(
    long userId, long tokenId, @Nullable Long projectId, @Nullable Long boardId, String tokenName) {

  public boolean isBound() {
    return projectId != null && boardId != null;
  }
}
