package org.mwolff.manban.board.web;

import org.mwolff.manban.board.application.BoardChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reicht ein {@link BoardChangedEvent} an die SSE-Registry weiter. Als eigener Listener bleiben die
 * Card-Use-Cases, die das Event publizieren, SSE-unabhängig (ArchUnit-konform, kein web-Import in
 * der Application-Schicht).
 *
 * <p>Erst {@link TransactionPhase#AFTER_COMMIT}: ein publiziertes Event erreicht die Abonnenten
 * nur, wenn die auslösende Transaktion committet — bei einem Rollback wird nichts gepusht (so sehen
 * Clients nie eine Änderung, die es gar nicht gab).
 */
@Component
class BoardEventListener {

  private final BoardEventRegistry registry;

  BoardEventListener(BoardEventRegistry registry) {
    this.registry = registry;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  void onBoardChanged(BoardChangedEvent event) {
    registry.publish(event.boardId(), event);
  }
}
