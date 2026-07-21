package org.mwolff.manban.board.web;

import org.mwolff.manban.board.application.BoardChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reicht ein {@link BoardChangedEvent} an die SSE-Registry weiter. Als eigener Listener bleiben die
 * Card-Use-Cases, die das Event publizieren, SSE-unabhängig (ArchUnit-konform, kein web-Import in
 * der Application-Schicht).
 */
@Component
class BoardEventListener {

  private final BoardEventRegistry registry;

  BoardEventListener(BoardEventRegistry registry) {
    this.registry = registry;
  }

  @EventListener
  void onBoardChanged(BoardChangedEvent event) {
    registry.publish(event.boardId(), event);
  }
}
