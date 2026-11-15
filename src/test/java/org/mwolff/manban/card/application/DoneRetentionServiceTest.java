package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;

/** Verhaltenstests der Done-Retention-Archivierung (Mockito am CardRepository-Port). */
class DoneRetentionServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-30T00:00:00Z");

  private CardRepository cards;
  private DoneRetentionService service;

  /** Projekt 2 hat ein fremdes Board (Issue #2161). */
  private static final long FREMDES_PROJEKT = 2L;

  private static Card doneCard(long id) {
    return doneCard(id, 1L);
  }

  private static Card doneCard(long id, long projectId) {
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
        null,
        projectId,
        null,
        null,
        null,
        null);
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    service = new DoneRetentionService(cards, Set.of(FREMDES_PROJEKT)::contains);
  }

  @Test
  void archiveExpiredDoneCards_laesstGespiegelteProjekteUnberuehrt() {
    // Given: Karte 1 im eigenen, Karte 2 im Projekt mit fremdem Board (Issue #2161)
    when(cards.findArchivableDoneCards(any()))
        .thenReturn(List.of(doneCard(1), doneCard(2, FREMDES_PROJEKT)));
    when(cards.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    int count = service.archiveExpiredDoneCards(NOW, 14);

    // Then: nur die eigene Karte archiviert
    assertThat(count).isEqualTo(1);
    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().id()).isEqualTo(1L);
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

  @Test
  void archiveExpiredDoneCards_archivesNothingAndSkipsQuery_whenRetentionZero() {
    // Given: 0 = Auto-Archiv aus

    // When
    int count = service.archiveExpiredDoneCards(NOW, 0);

    // Then: kein Zugriff aufs Repository, nichts archiviert
    assertThat(count).isZero();
    verifyNoInteractions(cards);
  }

  @Test
  void archiveExpiredDoneCards_archiviertErledigtesArbeitspaketInEigenerSpalte() {
    // Plan #1294, E7: Der Job greift allein über den Done-Zeitstempel. Ein Arbeitspaket mit
    // Status DONE in einer eigenen Spalte (20 = „Anstehend") trägt ihn und wird deshalb erfasst.
    Card paket = doneCard(3).withStatus(CardStatus.DONE);
    when(cards.findArchivableDoneCards(any())).thenReturn(List.of(paket));
    when(cards.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

    ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
    int count = service.archiveExpiredDoneCards(NOW, 14);

    assertThat(count).isEqualTo(1);
    verify(cards).save(captor.capture());
    assertThat(captor.getValue().archived()).isTrue();
    assertThat(captor.getValue().status()).isEqualTo(CardStatus.DONE);
  }
}
