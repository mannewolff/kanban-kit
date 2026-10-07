package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;

/**
 * Verhaltenstests des Vorhaben-Abgleichs (Plan #1504, Issue #1505). Der Port ist gemockt; die
 * Zugehörigkeit rechnet die echte {@link EpicMembership}, damit der Maßstab derselbe ist wie für
 * {@code total} in {@code listEpics}.
 */
class VorhabenArchivierungTest {

  private static final Instant FIXED = Instant.parse("2026-10-07T00:00:00Z");
  private static final long BOARD = 10L;
  private static final long ANDERES_BOARD = 11L;
  private static final int POSITION = 7;

  private CardRepository cards;
  private VorhabenArchivierung archivierung;

  @BeforeEach
  void setUp() {
    cards = mock(CardRepository.class);
    archivierung = new VorhabenArchivierung(cards);
  }

  private static Card vorhaben(long id, boolean archiviert) {
    return karte(id, CardType.EPIC, null, null, archiviert);
  }

  private static Card karte(
      long id,
      CardType type,
      @Nullable Long parentId,
      @Nullable Long derivedFrom,
      boolean archiviert) {
    return new Card(
        id,
        BOARD,
        100L,
        (int) id,
        "Karte " + id,
        null,
        POSITION,
        archiviert,
        null,
        null,
        FIXED,
        FIXED,
        type,
        parentId,
        null,
        null,
        1L,
        null,
        derivedFrom,
        null,
        null);
  }

  private void kartenAuf(long boardId, Card... karten) {
    when(cards.findByBoardId(boardId)).thenReturn(List.of(karten));
  }

  private void boardsMitVorhaben(Long... boardIds) {
    when(cards.findBoardIdsWithEpics()).thenReturn(List.of(boardIds));
  }

  @Test
  void leeresVorhabenMitNurArchiviertenKartenWirdArchiviert() {
    Card epic = vorhaben(1, false);
    boardsMitVorhaben(BOARD);
    kartenAuf(BOARD, epic, karte(2, CardType.CARD, 1L, null, true));

    int anzahl = archivierung.gleicheAlleAb();

    assertThat(anzahl).isEqualTo(1);
    verify(cards).save(epic.asArchived());
  }

  @Test
  void vorhabenOhneJeEineKarteWirdArchiviert() {
    Card epic = vorhaben(1, false);
    boardsMitVorhaben(BOARD);
    kartenAuf(BOARD, epic);

    assertThat(archivierung.gleicheAlleAb()).isEqualTo(1);
    verify(cards).save(epic.asArchived());
  }

  @Test
  void karteImPapierkorbFehltInFindByBoardIdUndDasVorhabenWirdArchiviert() {
    // Die Papierkorb-Karte liefert findByBoardId nicht — das Board sieht nur das Vorhaben.
    Card epic = vorhaben(1, false);
    boardsMitVorhaben(BOARD);
    kartenAuf(BOARD, epic);

    assertThat(archivierung.gleicheAlleAb()).isEqualTo(1);
    verify(cards).save(epic.asArchived());
  }

  @Test
  void vorhabenMitFertigerNichtArchivierterKarteBleibt() {
    Card epic = vorhaben(1, false);
    Card fertig =
        new Card(
            2L,
            BOARD,
            100L,
            2,
            "fertig",
            null,
            0,
            false,
            FIXED,
            null,
            FIXED,
            FIXED,
            CardType.CARD,
            1L,
            null,
            null,
            1L,
            null,
            null,
            null,
            null);
    boardsMitVorhaben(BOARD);
    kartenAuf(BOARD, epic, fertig);

    assertThat(archivierung.gleicheAlleAb()).isZero();
    verify(cards, never()).save(any());
  }

  @Test
  void vorhabenMitMitzaehlenderKarteNurUeberHerkunftBleibt() {
    // Karte 2 ist zugeordnet, aber archiviert; ihre Ableitung 3 zählt und gehört über die
    // Herkunft dazu.
    Card epic = vorhaben(1, false);
    boardsMitVorhaben(BOARD);
    kartenAuf(
        BOARD,
        epic,
        karte(2, CardType.CARD, 1L, null, true),
        karte(3, CardType.CARD, null, 2L, false));

    assertThat(archivierung.gleicheAlleAb()).isZero();
    verify(cards, never()).save(any());
  }

  @Test
  void archiviertesVorhabenMitMitzaehlenderKarteKehrtImAbgleichZurueck() {
    Card epic = vorhaben(1, true);
    boardsMitVorhaben(BOARD);
    kartenAuf(BOARD, epic, karte(2, CardType.CARD, 1L, null, false));

    int anzahl = archivierung.gleicheAlleAb();

    assertThat(anzahl).isZero();
    verify(cards).save(epic.asRestored(POSITION));
  }

  @Test
  void holeZurueckHoltZurueckUndArchiviertKeinLeeresVorhaben() {
    Card zurueck = vorhaben(1, true);
    Card leer = vorhaben(3, false);
    kartenAuf(BOARD, zurueck, karte(2, CardType.CARD, 1L, null, false), leer);

    archivierung.holeZurueck(BOARD);

    verify(cards).save(zurueck.asRestored(POSITION));
    verify(cards, never()).save(leer.asArchived());
    verify(cards, never()).findBoardIdsWithEpics();
  }

  @Test
  void holeZurueckLaesstArchiviertesLeeresVorhabenImArchiv() {
    kartenAuf(BOARD, vorhaben(1, true));

    archivierung.holeZurueck(BOARD);

    verify(cards, never()).save(any());
  }

  @Test
  void bereitsArchiviertesLeeresVorhabenWirdNichtErneutGespeichert() {
    boardsMitVorhaben(BOARD);
    kartenAuf(BOARD, vorhaben(1, true), karte(2, CardType.CARD, 1L, null, true));

    assertThat(archivierung.gleicheAlleAb()).isZero();
    verify(cards, never()).save(any());
  }

  @Test
  void rueckgabeZaehltNurArchivierungenNichtRueckholungen() {
    Card zurueck = vorhaben(1, true);
    Card leer1 = vorhaben(3, false);
    Card leer2 = vorhaben(4, false);
    boardsMitVorhaben(BOARD);
    kartenAuf(BOARD, zurueck, karte(2, CardType.CARD, 1L, null, false), leer1, leer2);

    assertThat(archivierung.gleicheAlleAb()).isEqualTo(2);
    verify(cards).save(zurueck.asRestored(POSITION));
    verify(cards).save(leer1.asArchived());
    verify(cards).save(leer2.asArchived());
  }

  @Test
  void boardsKommenAusFindBoardIdsWithEpicsJeBoardEinFindByBoardId() {
    Card epicA = vorhaben(1, false);
    Card epicB =
        new Card(
            5L,
            ANDERES_BOARD,
            200L,
            5,
            "B",
            null,
            0,
            false,
            null,
            null,
            FIXED,
            FIXED,
            CardType.EPIC,
            null,
            null,
            null,
            1L,
            null,
            null,
            null,
            null);
    boardsMitVorhaben(BOARD, ANDERES_BOARD);
    kartenAuf(BOARD, epicA);
    kartenAuf(ANDERES_BOARD, epicB);

    assertThat(archivierung.gleicheAlleAb()).isEqualTo(2);

    verify(cards).findBoardIdsWithEpics();
    verify(cards).findByBoardId(BOARD);
    verify(cards).findByBoardId(ANDERES_BOARD);
    verify(cards).save(epicA.asArchived());
    verify(cards).save(epicB.asArchived());
    verifyNoMoreInteractions(cards);
  }

  @Test
  void ohneBoardsMitVorhabenGeschiehtNichts() {
    boardsMitVorhaben();

    assertThat(archivierung.gleicheAlleAb()).isZero();
    verify(cards).findBoardIdsWithEpics();
    verifyNoMoreInteractions(cards);
  }
}
