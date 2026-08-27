package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.springframework.context.ApplicationEventPublisher;

/** Verhaltenstests der Papierkorb-Retention (Ports gemockt). */
class TrashRetentionServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

  private CardRepository cards;
  private CardDependencyRepository dependencies;
  private ApplicationEventPublisher events;
  private TrashRetentionService service;

  private static Card card(long id) {
    return new Card(
        id,
        10L,
        20L,
        (int) id,
        "T" + id,
        null,
        0,
        false,
        false,
        null,
        1L,
        NOW,
        NOW,
        CardType.CARD,
        null,
        null,
        null,
        1L,
        null,
        null,
        null);
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    dependencies = mock(CardDependencyRepository.class);
    events = mock(ApplicationEventPublisher.class);
    service = new TrashRetentionService(cards, dependencies, events);
  }

  @Test
  void purgeExpiredTrash_hardDeletesEachExpiredCard() {
    Instant threshold = NOW.minus(Duration.ofDays(30));
    when(cards.findPurgeableTrash(threshold)).thenReturn(List.of(card(1), card(2)));

    int count = service.purgeExpiredTrash(NOW, 30);

    assertThat(count).isEqualTo(2);
    verify(dependencies).deleteByCardId(1L);
    verify(cards).deleteById(1L);
    verify(dependencies).deleteByCardId(2L);
    verify(cards).deleteById(2L);
  }

  @Test
  void purgeExpiredTrash_returnsZero_whenNothingExpired() {
    when(cards.findPurgeableTrash(NOW.minus(Duration.ofDays(30)))).thenReturn(List.of());

    assertThat(service.purgeExpiredTrash(NOW, 30)).isZero();
  }

  @Test
  void purgeExpiredTrash_publishesCardsPurgedBeforeDeleting() {
    Instant threshold = NOW.minus(Duration.ofDays(30));
    when(cards.findPurgeableTrash(threshold)).thenReturn(List.of(card(1), card(2)));

    service.purgeExpiredTrash(NOW, 30);

    // Reihenfolge ist die Zusage aus #503: Erst publizieren (Anhänge planen ihre Blob-Löschung
    // ein, solange die Metadaten existieren), dann löschen.
    InOrder inOrder = inOrder(events, cards);
    inOrder.verify(events).publishEvent(new CardsPurgedEvent(List.of(1L, 2L)));
    inOrder.verify(cards).deleteById(1L);
  }

  @Test
  void purgeExpiredTrash_publishesNothing_whenNothingExpired() {
    when(cards.findPurgeableTrash(NOW.minus(Duration.ofDays(30)))).thenReturn(List.of());

    service.purgeExpiredTrash(NOW, 30);

    verifyNoInteractions(events);
  }
}
