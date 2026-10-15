package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardRunQueryService.BoardKarteView;
import org.mwolff.manban.card.application.CardRunQueryService.KartenGruppe;
import org.mwolff.manban.card.application.CardRunQueryService.LabelEreignisView;
import org.mwolff.manban.card.application.CardRunQueryService.LaufBewegungView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardLabelEvent;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.LaufBewegung;

/**
 * Unit-Tests der Lese-Abfragen für die Nacharbeit (Issue #1755, Plan #1752 E1, E2, E5, E10).
 *
 * <p>Eigene Klasse wie die übrigen {@code CardRunQueryService*Test}; der Bestandstest {@code
 * CardRunQueryServiceLaufspurenTest} bleibt unberührt. Als Unit-Test, weil PIT allein Unit-Tests
 * misst.
 */
class CardRunQueryServiceNacharbeitTest {

  private static final Instant FIXED = Instant.parse("2026-10-14T12:00:00Z");
  private static final Instant VON = Instant.parse("2026-10-14T22:00:00Z");
  private static final Instant LAUF = Instant.parse("2026-10-14T23:00:00Z");
  private static final long BOARD = 2L;

  private CardRepository cards;
  private CardActivityRepository activity;
  private CardLabelEventRepository labelEvents;
  private CardRunQueryService service;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    activity = mock(CardActivityRepository.class);
    labelEvents = mock(CardLabelEventRepository.class);
    service =
        new CardRunQueryService(
            cards, activity, mock(KartenZuordnung.class), mock(BoardService.class), labelEvents);
  }

  private static Card karte(long id, String titel, CardType type) {
    return new Card(
        id,
        BOARD,
        10L,
        (int) id + 100,
        titel,
        null,
        0,
        false,
        null,
        null,
        FIXED,
        FIXED,
        type,
        null,
        null,
        null,
        1L,
        null,
        null,
        null,
        null);
  }

  @Test
  void boardKartenOrdnetJedeKarteIhrerGruppeZu() {
    when(cards.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                karte(1L, "Paket bauen", CardType.CARD),
                karte(2L, "[Task] Kleinigkeit", CardType.CARD),
                karte(3L, "[Requirement] Nacharbeit", CardType.CARD),
                karte(4L, "[Plan] Nacharbeit", CardType.CARD),
                karte(5L, "[Idea] Vielleicht", CardType.CARD),
                karte(6L, "Vorhaben", CardType.EPIC)));

    assertThat(service.boardKarten(BOARD))
        .containsExactly(
            new BoardKarteView(1L, 101, "Paket bauen", KartenGruppe.PAKET),
            new BoardKarteView(2L, 102, "[Task] Kleinigkeit", KartenGruppe.PAKET),
            new BoardKarteView(3L, 103, "[Requirement] Nacharbeit", KartenGruppe.DOKUMENT),
            new BoardKarteView(4L, 104, "[Plan] Nacharbeit", KartenGruppe.DOKUMENT),
            new BoardKarteView(5L, 105, "[Idea] Vielleicht", KartenGruppe.ANDERE),
            new BoardKarteView(6L, 106, "Vorhaben", KartenGruppe.ANDERE));
  }

  @Test
  void boardKartenLiestUeberDenPortOhnePapierkorbMitArchiv() {
    // findByBoardId liefert archivierte Karten mit und Papierkorb-Karten nicht (E10); die Fassade
    // reicht genau diese Menge durch, ohne selbst zu filtern.
    Card archiviert =
        new Card(
            7L,
            BOARD,
            10L,
            107,
            "Archiviert",
            null,
            0,
            true,
            null,
            null,
            FIXED,
            FIXED,
            CardType.CARD,
            null,
            null,
            null,
            1L,
            null,
            null,
            null,
            null);
    when(cards.findByBoardId(BOARD)).thenReturn(List.of(archiviert));

    assertThat(service.boardKarten(BOARD))
        .containsExactly(new BoardKarteView(7L, 107, "Archiviert", KartenGruppe.PAKET));
  }

  @Test
  void laufBewegungenReichtDieBewegungenAlsSichtDurch() {
    when(activity.findLaufBewegungen(List.of(1L, 2L), VON))
        .thenReturn(
            List.of(
                new LaufBewegung(
                    1L,
                    CardActivityType.MOVED,
                    LAUF,
                    LAUF,
                    CardStatus.READY,
                    CardStatus.IN_PROGRESS),
                new LaufBewegung(
                    2L,
                    CardActivityType.STATUS_CHANGED,
                    LAUF,
                    LAUF,
                    CardStatus.IN_PROGRESS,
                    null)));

    assertThat(service.laufBewegungen(List.of(1L, 2L), VON))
        .containsExactly(
            new LaufBewegungView(1L, "MOVED", LAUF, LAUF, "READY", "IN_PROGRESS"),
            new LaufBewegungView(2L, "STATUS_CHANGED", LAUF, LAUF, "IN_PROGRESS", null));
  }

  @Test
  void labelEreignisseReichtDieEreignisseAlsSichtDurch() {
    when(labelEvents.findLaufEreignisse(List.of(3L), VON))
        .thenReturn(
            List.of(
                new CardLabelEvent(
                    3L,
                    "kit:clarify",
                    LAUF.plusSeconds(60),
                    CardActivityOrigin.TOKEN,
                    "Nachtlauf",
                    "claude-opus-5-5",
                    LAUF)));

    assertThat(service.labelEreignisse(List.of(3L), VON))
        .containsExactly(new LabelEreignisView(3L, "kit:clarify", LAUF.plusSeconds(60), LAUF));
  }

  @Test
  void labelErfassungSeitKommtVomPort() {
    when(labelEvents.erfassungSeit()).thenReturn(FIXED);

    assertThat(service.labelErfassungSeit()).isEqualTo(FIXED);
  }
}
