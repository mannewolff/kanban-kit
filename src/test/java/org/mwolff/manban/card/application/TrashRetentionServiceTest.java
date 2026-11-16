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
  private CardBestandRepository bestand;
  private CardDependencyRepository dependencies;
  private ApplicationEventPublisher events;
  private TrashRetentionService service;

  /** Projekt 2 hat ein fremdes Board (Issue #2161). */
  private static final long FREMDES_PROJEKT = 2L;

  private static Card card(long id) {
    return card(id, 1L);
  }

  private static Card card(long id, long projectId) {
    return new Card(
        id,
        10L,
        20L,
        (int) id,
        "T" + id,
        null,
        0,
        false,
        null,
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
    bestand = mock(CardBestandRepository.class);
    dependencies = mock(CardDependencyRepository.class);
    events = mock(ApplicationEventPublisher.class);
    service =
        new TrashRetentionService(
            cards,
            bestand,
            new KartenAbhaengigkeiten(dependencies, cards),
            events,
            java.util.Set.of(FREMDES_PROJEKT)::contains);
  }

  @Test
  void purgeExpiredTrash_laesstGespiegelteProjekteUnberuehrt() {
    // Given: Karte 2 gehört zu einem Projekt mit fremdem Board (Issue #2161, Plan #2152 A8) —
    // auch wenn die Abfrage sie liefern sollte, löscht der Dienst sie nicht.
    Instant threshold = NOW.minus(Duration.ofDays(30));
    when(cards.findPurgeableTrash(threshold))
        .thenReturn(List.of(card(1), card(2, FREMDES_PROJEKT)));

    int count = service.purgeExpiredTrash(NOW, 30);

    assertThat(count).isEqualTo(1);
    verify(cards).deleteById(1L);
    verify(cards, org.mockito.Mockito.never()).deleteById(2L);
    verify(events).publishEvent(new CardsPurgedEvent(List.of(1L)));
  }

  @Test
  void purgeExpiredTrash_tutNichts_wennNurGespiegelteKartenAbgelaufenSind() {
    Instant threshold = NOW.minus(Duration.ofDays(30));
    when(cards.findPurgeableTrash(threshold)).thenReturn(List.of(card(2, FREMDES_PROJEKT)));

    assertThat(service.purgeExpiredTrash(NOW, 30)).isZero();
    verifyNoInteractions(events, bestand);
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

    verifyNoInteractions(events, bestand);
  }

  @Test
  void purgeExpiredTrash_bewahrtHerkunftsnummernVorDemLoeschen() {
    Instant threshold = NOW.minus(Duration.ofDays(30));
    when(cards.findPurgeableTrash(threshold)).thenReturn(List.of(card(1), card(2)));

    service.purgeExpiredTrash(NOW, 30);

    // Plan #1574, E18: einmal für alle abgelaufenen Karten, bevor die erste gelöscht wird.
    InOrder inOrder = inOrder(bestand, cards);
    inOrder.verify(bestand).bewahreHerkunftsnummern(List.of(1L, 2L));
    inOrder.verify(cards).deleteById(1L);
    inOrder.verify(cards).deleteById(2L);
  }
}
