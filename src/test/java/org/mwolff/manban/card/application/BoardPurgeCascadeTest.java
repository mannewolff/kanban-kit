package org.mwolff.manban.card.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardPurgedEvent;
import org.springframework.context.ApplicationEventPublisher;

/** Übersetzung Board-Purge → Karten-Purge (Issue #503). */
class BoardPurgeCascadeTest {

  private final CardRepository cards = mock(CardRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final BoardPurgeCascade cascade = new BoardPurgeCascade(cards, events);

  @Test
  void onBoardPurged_republishesAllCardIdsOfTheBoard() {
    // Given — findAllIdsByBoardId liefert bewusst auch archivierte und Papierkorb-Karten.
    when(cards.findAllIdsByBoardId(10L)).thenReturn(List.of(1L, 2L, 3L));

    // When
    cascade.onBoardPurged(new BoardPurgedEvent(10L));

    // Then
    verify(events).publishEvent(new CardsPurgedEvent(List.of(1L, 2L, 3L)));
  }

  @Test
  void onBoardPurged_publishesNothing_forBoardWithoutCards() {
    // Given
    when(cards.findAllIdsByBoardId(10L)).thenReturn(List.of());

    // When
    cascade.onBoardPurged(new BoardPurgedEvent(10L));

    // Then
    verifyNoInteractions(events);
  }
}
