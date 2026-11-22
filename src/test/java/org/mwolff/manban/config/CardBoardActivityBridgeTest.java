package org.mwolff.manban.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.board.application.BoardChangedEvent;
import org.mwolff.manban.board.application.BoardChangedEvent.ChangeType;
import org.mwolff.manban.card.application.Anbietersicht;
import org.mwolff.manban.card.application.CardBoardActivityEvent;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.springframework.context.ApplicationEventPublisher;

/** Verhaltenstests der Event-Übersetzung zwischen card- und board-Modul (Issue #459). */
class CardBoardActivityBridgeTest {

  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final Anbietersicht anbieter = mock(Anbietersicht.class);
  private final CardBoardActivityBridge bridge = new CardBoardActivityBridge(events, anbieter);

  @Test
  void publishesBoardChangedEvent_withSameBoardAndCard() {
    // When
    bridge.onCardBoardActivity(new CardBoardActivityEvent(10L, ActivityType.MOVED, 7L));

    // Then
    verify(events).publishEvent(new BoardChangedEvent(10L, ChangeType.MOVED, 7L));
  }

  @Test
  void keepsNullCardId() {
    // Given: Änderungen ohne einzelne Karte (z. B. board-weite Mutationen)

    // When
    bridge.onCardBoardActivity(new CardBoardActivityEvent(10L, ActivityType.UPDATED, null));

    // Then
    verify(events).publishEvent(new BoardChangedEvent(10L, ChangeType.UPDATED, null));
  }

  @ParameterizedTest
  @EnumSource(ActivityType.class)
  void translatesEveryActivityType_toSameNamedChangeType(ActivityType type) {
    // When: jeder Aktivitätstyp durchläuft die Übersetzung — ein neuer Wert ohne Pendant im
    // board-Modul fällt hier auf, statt erst im Betrieb zu scheitern.
    bridge.onCardBoardActivity(new CardBoardActivityEvent(10L, type, 1L));

    // Then
    ArgumentCaptor<BoardChangedEvent> captor = ArgumentCaptor.forClass(BoardChangedEvent.class);
    verify(events).publishEvent(captor.capture());
    assertThat(captor.getValue().type().name()).isEqualTo(type.name());
  }

  @Test
  void reparaturkarte_gehtOhneKartenIdHinaus() {
    // Issue #2211: Der Strom kennt den Leser nicht; ohne ID erfährt ein Kunde nichts über die
    // Karte.
    when(anbieter.istReparaturkarte(42L)).thenReturn(true);

    bridge.onCardBoardActivity(new CardBoardActivityEvent(7L, ActivityType.UPDATED, 42L));

    ArgumentCaptor<BoardChangedEvent> captor = ArgumentCaptor.forClass(BoardChangedEvent.class);
    verify(events).publishEvent(captor.capture());
    assertThat(captor.getValue()).isEqualTo(new BoardChangedEvent(7L, ChangeType.UPDATED, null));
  }

  @Test
  void ohneKartenId_fragtSieNichtNach() {
    bridge.onCardBoardActivity(new CardBoardActivityEvent(7L, ActivityType.UPDATED, null));

    verifyNoInteractions(anbieter);
  }
}
