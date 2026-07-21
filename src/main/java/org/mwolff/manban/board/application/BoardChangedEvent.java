package org.mwolff.manban.board.application;

import org.jspecify.annotations.Nullable;

/**
 * Anwendungs-Event: an einem Board hat sich etwas geändert (für Live-Updates via SSE). Wird von den
 * Card-Use-Cases über den Spring-{@code ApplicationEventPublisher} publiziert und vom
 * Board-Event-Listener an die SSE-Registry weitergereicht — so bleiben die Use-Cases
 * SSE-unabhängig.
 *
 * @param boardId betroffenes Board
 * @param type Art der Änderung (der Client nutzt zunächst nur „etwas hat sich geändert")
 * @param cardId betroffene Karte, sofern die Änderung eine einzelne Karte betrifft (sonst {@code
 *     null})
 */
public record BoardChangedEvent(long boardId, ChangeType type, @Nullable Long cardId) {

  /** Typ der Board-Änderung. */
  public enum ChangeType {
    CREATED,
    UPDATED,
    MOVED,
    ARCHIVED,
    RESTORED,
    DELETED
  }
}
