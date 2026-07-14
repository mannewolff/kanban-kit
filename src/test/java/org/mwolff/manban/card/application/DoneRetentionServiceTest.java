package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;

/** Verhaltenstests der Done-Retention-Archivierung (Mockito am CardRepository-Port). */
class DoneRetentionServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-30T00:00:00Z");

  private CardRepository cards;
  private DoneRetentionService service;

  private static Card doneCard(long id) {
    return new Card(
        id,
        10L,
        20L,
        (int) id,
        "T" + id,
        null,
        0,
        false,
        NOW.minus(Duration.ofDays(40)),
        1L,
        NOW,
        NOW,
        CardType.CARD,
        null,
        null,
        null);
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    service = new DoneRetentionService(cards);
  }

  @Test
  void archiveExpiredDoneCards_queriesRepositoryWithComputedThreshold() {
    // Given
    when(cards.findArchivableDoneCards(any())).thenReturn(List.of());

    // When
    service.archiveExpiredDoneCards(NOW, 14);

    // Then
    verify(cards).findArchivableDoneCards(NOW.minus(Duration.ofDays(14)));
  }

  @Test
  void archiveExpiredDoneCards_returnsCountOfArchivedCards() {
    // Given
    when(cards.findArchivableDoneCards(any())).thenReturn(List.of(doneCard(1), doneCard(2)));
    when(cards.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    int count = service.archiveExpiredDoneCards(NOW, 14);

    // Then
    assertThat(count).isEqualTo(2);
  }

  @Test
  void archiveExpiredDoneCards_marksEachExpiredCardAsArchived() {
    // Given
    when(cards.findArchivableDoneCards(any())).thenReturn(List.of(doneCard(1)));
    when(cards.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    service.archiveExpiredDoneCards(NOW, 14);

    // Then
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().archived()).isTrue();
  }

  @Test
  void archiveExpiredDoneCards_returnsZero_whenNothingExpired() {
    // Given
    when(cards.findArchivableDoneCards(any())).thenReturn(List.of());

    // When
    int count = service.archiveExpiredDoneCards(NOW, 14);

    // Then
    assertThat(count).isZero();
  }
}
