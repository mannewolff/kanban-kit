package org.mwolff.manban.board.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardChangedEvent;
import org.mwolff.manban.board.application.BoardChangedEvent.ChangeType;

/** Der Listener reicht das Event an die Registry weiter. */
class BoardEventListenerTest {

  @Test
  void onBoardChanged_forwardsToRegistry() {
    BoardEventRegistry registry = mock(BoardEventRegistry.class);
    var listener = new BoardEventListener(registry);
    var event = new BoardChangedEvent(42L, ChangeType.CREATED, 7L);

    listener.onBoardChanged(event);

    verify(registry).publish(42L, event);
  }
}
