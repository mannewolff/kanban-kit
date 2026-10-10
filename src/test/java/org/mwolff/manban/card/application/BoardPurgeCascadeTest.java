package org.mwolff.manban.card.application;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mwolff.manban.board.application.BoardPurgedEvent;
import org.springframework.context.ApplicationEventPublisher;

/** Übersetzung Board-Purge → Karten-Purge (Issue #503). */
class BoardPurgeCascadeTest {

  private final CardRepository cards = mock(CardRepository.class);
  private final CardBestandRepository bestand = mock(CardBestandRepository.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final BoardPurgeCascade cascade = new BoardPurgeCascade(cards, bestand, events);

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
    verifyNoInteractions(events, bestand);
  }

  @Test
  void onBoardPurged_bewahrtHerkunftsnummernAllerKartenVorDerCascade() {
    // Given
    when(cards.findAllIdsByBoardId(10L)).thenReturn(List.of(1L, 2L, 3L));

    // When
    cascade.onBoardPurged(new BoardPurgedEvent(10L));

    // Then — Plan #1574, E18: Der Listener läuft vor dem Delete des Boards, dessen DB-Cascade die
    // Karten entfernt; die Nummern stehen danach schon an den Kindern auf anderen Boards.
    InOrder inOrder = inOrder(cards, bestand);
    inOrder.verify(cards).findAllIdsByBoardId(10L);
    inOrder.verify(bestand).bewahreHerkunftsnummern(List.of(1L, 2L, 3L));
  }
}
