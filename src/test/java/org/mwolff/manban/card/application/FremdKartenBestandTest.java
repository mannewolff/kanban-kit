package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.FremdKartenBestand.KartenBestand;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;

/**
 * Lesewege für das Anbinden eines fremden Boards ohne Datenbank (Issue #2183): Sperre vor der
 * Bestandsauskunft, Kartennummern und -IDs aus dem Repository, die fehlenden Status eines Boards
 * und die Leer-Regel des Bestands. Das Zusammenspiel mit der Datenbank zeigt {@code
 * FremdKartenSchreiberIT}; dieser Test trägt die Mutationsprüfung, die keine Integrationstests
 * fährt.
 */
class FremdKartenBestandTest {

  private static final Instant ZEITPUNKT = Instant.parse("2026-11-01T08:00:00Z");
  private static final long PROJEKT = 3L;
  private static final long BOARD = 10L;

  private CardRepository cards;
  private CardBestandRepository bestandsAuskunft;
  private BoardService boardService;
  private FremdKartenBestand bestand;

  private static Card karte(long id, Integer nummer) {
    return new Card(
        id,
        BOARD,
        100L,
        nummer,
        "Titel",
        "Text",
        0,
        false,
        null,
        null,
        ZEITPUNKT,
        ZEITPUNKT,
        CardType.CARD,
        null,
        null,
        null,
        PROJEKT,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static ColumnView spalte(long id, String name, int position) {
    return new ColumnView(id, name, position, null);
  }

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    bestandsAuskunft = mock(CardBestandRepository.class);
    boardService = mock(BoardService.class);
    bestand = new FremdKartenBestand(cards, bestandsAuskunft, boardService);
  }

  @Test
  void bestandSperrtDieKartennummernVorDerAuskunftUndGibtSieZurueck() {
    KartenBestand erwartet = new KartenBestand(1, 2, 3, 4);
    when(bestandsAuskunft.bestand(PROJEKT)).thenReturn(erwartet);

    assertThat(bestand.bestand(PROJEKT)).isEqualTo(erwartet);

    InOrder reihenfolge = inOrder(cards, bestandsAuskunft);
    reihenfolge.verify(cards).lockCardNumbers(PROJEKT);
    reihenfolge.verify(bestandsAuskunft).bestand(PROJEKT);
  }

  @Test
  void kartenIdLiefertDieIdDerKarteMitDieserNummer() {
    when(cards.findByProjectIdAndNumber(PROJEKT, 42)).thenReturn(Optional.of(karte(55L, 42)));

    assertThat(bestand.kartenId(PROJEKT, 42)).contains(55L);
  }

  @Test
  void kartenIdIstLeerOhneKarte() {
    when(cards.findByProjectIdAndNumber(PROJEKT, 42)).thenReturn(Optional.empty());

    assertThat(bestand.kartenId(PROJEKT, 42)).isEmpty();
  }

  @Test
  void nummernLassenKartenOhneNummerAus() {
    when(cards.findByProjectId(PROJEKT))
        .thenReturn(Arrays.asList(karte(1L, 7), karte(2L, null), karte(3L, 9)));

    assertThat(bestand.nummern(PROJEKT)).containsExactly(7, 9);
  }

  @Test
  void fehlendeStatusNennenDieStatusOhneSpalteInProzessreihenfolge() {
    when(boardService.listColumns(BOARD))
        .thenReturn(
            List.of(spalte(1L, "Backlog", 0), spalte(2L, "Sonstiges", 1), spalte(3L, "Done", 2)));

    assertThat(bestand.fehlendeStatus(BOARD)).containsExactly("Ready", "In progress", "In review");
  }

  @Test
  void fehlendeStatusSindLeerWennJederStatusEineSpalteHat() {
    when(boardService.listColumns(BOARD))
        .thenReturn(
            List.of(
                spalte(1L, "Backlog", 0),
                spalte(2L, "Ready", 1),
                spalte(3L, "In progress", 2),
                spalte(4L, "In review", 3),
                spalte(5L, "Done", 4)));

    assertThat(bestand.fehlendeStatus(BOARD)).isEmpty();
  }

  @Test
  void leerNurOhneEinzigeKarte() {
    assertThat(new KartenBestand(0, 0, 0, 0).leer()).isTrue();
    assertThat(new KartenBestand(1, 1, 0, 0).leer()).isFalse();
    assertThat(new KartenBestand(1, 0, 1, 0).leer()).isFalse();
    assertThat(new KartenBestand(1, 0, 0, 1).leer()).isFalse();
  }
}
