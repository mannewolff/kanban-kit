package org.mwolff.manban.card.application;

import org.jspecify.annotations.Nullable;

/**
 * Anwendungs-Event: eine Karten-Mutation betrifft ein Board (für Live-Updates via SSE). Wird von
 * den Karten-Use-Cases über den Spring-{@code ApplicationEventPublisher} publiziert.
 *
 * <p>Bewusst ein card-eigenes Event statt des board-eigenen {@code BoardChangedEvent} (#459): das
 * card-Modul kennt damit weder den SSE-Vertrag des board-Moduls noch dessen Event-Typ. Die
 * Übersetzung in {@code BoardChangedEvent} übernimmt ein Listener in der Composition-Root — der
 * SSE-Weg (Registry, Auslieferung erst nach Commit) bleibt unverändert.
 *
 * @param boardId betroffenes Board
 * @param type Art der Änderung
 * @param cardId betroffene Karte, sofern die Änderung eine einzelne Karte betrifft (sonst {@code
 *     null})
 */
public record CardBoardActivityEvent(long boardId, ActivityType type, @Nullable Long cardId) {

  /**
   * Art der Karten-Aktivität. Deckungsgleich mit {@code BoardChangedEvent.ChangeType} — die
   * Composition-Root übersetzt namensgleich.
   */
  public enum ActivityType {
    CREATED,
    UPDATED,
    MOVED,
    ARCHIVED,
    RESTORED,
    DELETED
  }
}
