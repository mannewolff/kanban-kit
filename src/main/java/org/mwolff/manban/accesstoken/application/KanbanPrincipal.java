package org.mwolff.manban.accesstoken.application;

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
 */
public record KanbanPrincipal(long userId, long tokenId, Long projectId, Long boardId) {

  public boolean isBound() {
    return projectId != null && boardId != null;
  }
}
