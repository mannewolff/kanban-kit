package org.mwolff.manban.config;

import org.mwolff.manban.board.application.BoardChangedEvent;
import org.mwolff.manban.board.application.BoardChangedEvent.ChangeType;
import org.mwolff.manban.card.application.Anbietersicht;
import org.mwolff.manban.card.application.CardBoardActivityEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Übersetzt das card-eigene {@link CardBoardActivityEvent} in das board-eigene {@link
 * BoardChangedEvent} (#459). Als Composition-Root-Baustein (analog zu {@code SecurityConfig}) kennt
 * sie beide Module, ohne dass eines vom anderen abhängt — das card-Modul publiziert seinen eigenen
 * Vertrag, der SSE-Vertrag bleibt Sache des board-Moduls.
 *
 * <p>Bewusst ein gewöhnlicher {@link EventListener}, kein {@code TransactionalEventListener}: die
 * Übersetzung läuft synchron innerhalb der auslösenden Transaktion, damit der board-eigene
 * SSE-Listener das übersetzte Event wie bisher erst {@code AFTER_COMMIT} zugestellt bekommt. Würde
 * hier schon nach dem Commit übersetzt, liefe das Folge-Event ohne aktive Transaktion und käme bei
 * den Abonnenten nie an.
 */
@Component
class CardBoardActivityBridge {

  private final ApplicationEventPublisher events;
  private final Anbietersicht anbieter;

  CardBoardActivityBridge(ApplicationEventPublisher events, Anbietersicht anbieter) {
    this.events = events;
    this.anbieter = anbieter;
  }

  /**
   * Übersetzt das Ereignis. Betrifft es eine Reparaturkarte, geht es ohne Karten-ID hinaus (Issue
   * #2211, Plan #2206 E18): Der Strom schickt jedem Abonnenten des Boards dasselbe und kennt den
   * Leser nicht; ohne ID lädt ein Client das Board neu wie bei jeder anderen Änderung und erfährt
   * nichts über die Karte. Gefiltert wird hier, weil {@code board} {@code card} nicht aufrufen
   * darf.
   */
  @EventListener
  void onCardBoardActivity(CardBoardActivityEvent event) {
    Long cardId = event.cardId();
    // Beide Enums sind namensgleich (siehe CardBoardActivityEvent.ActivityType); der Test
    // uebersetztJedenAktivitaetstyp haelt das fest.
    events.publishEvent(
        new BoardChangedEvent(
            event.boardId(),
            ChangeType.valueOf(event.type().name()),
            cardId == null || anbieter.istReparaturkarte(cardId) ? null : cardId));
  }
}
