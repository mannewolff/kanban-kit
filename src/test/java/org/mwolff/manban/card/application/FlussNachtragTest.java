package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.card.application.CardFlowStayRepository.ErfassungsMarke;
import org.mwolff.manban.card.application.FlussNachtragQuelle.Bestand;
import org.mwolff.manban.card.application.FlussNachtragQuelle.Kartenverlauf;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Kartenstand;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spaltenort;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spaltenzeile;
import org.mwolff.manban.card.domain.FlussStay;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;

/**
 * Wie der Nachtrag den rekonstruierten Verlauf an die Saat anpasst (Issue #1770, Plan #1765 E6, E7,
 * E8, E20). Die Rekonstruktion selbst prüft {@code FlussRekonstruktionTest}; hier zählt, was vor
 * dem Schnitt geschrieben, was an der Saat korrigiert und was ausgelassen wird.
 */
class FlussNachtragTest {

  private static final Instant JETZT = Instant.parse("2026-06-05T10:00:00Z");
  private static final Instant SINCE = Instant.parse("2026-06-01T00:00:00Z");
  private static final Instant T0 = Instant.parse("2026-03-01T08:00:00Z");
  private static final Instant T1 = Instant.parse("2026-03-02T08:00:00Z");
  private static final Instant T2 = Instant.parse("2026-03-03T08:00:00Z");
  private static final long KARTEN_ID = 7;
  private static final long BOARD = 1;
  private static final long ANDERES_BOARD = 2;
  private static final long SAAT_ID = 99;

  private final FlussNachtragQuelle quelle = mock(FlussNachtragQuelle.class);
  private final CardFlowStayRepository aufenthalte = mock(CardFlowStayRepository.class);
  private final FlussNachtrag nachtrag =
      new FlussNachtrag(quelle, aufenthalte, Clock.fixed(JETZT, ZoneOffset.UTC));
  private final List<Kartenverlauf> karten = new ArrayList<>();

  @BeforeEach
  void marke() {
    when(aufenthalte.markeSperren()).thenReturn(new ErfassungsMarke(SINCE, null));
    when(quelle.lesen()).thenAnswer(aufruf -> new Bestand(karten, List.of()));
  }

  @Test
  void eineGesetzteMarkeLaesstAllesStehen() {
    when(aufenthalte.markeSperren()).thenReturn(new ErfassungsMarke(SINCE, T0));

    assertThat(nachtrag.nachtragen()).isFalse();

    verifyNoInteractions(quelle);
    verify(aufenthalte, never()).nachtragen(any());
    verify(aufenthalte, never()).markeSetzen(any());
  }

  @Test
  void derVerlaufVorDemSchnittKommtHinzuUndDieSaatBekommtIhrenEintritt() {
    karte(CardStatus.READY, null, zeile("Backlog", BOARD, T0, T1), zeile("Ready", BOARD, T1, null));
    saat(CardStatus.READY, BOARD, SINCE, false);

    assertThat(nachtrag.nachtragen()).isTrue();

    InOrder reihenfolge = inOrder(aufenthalte);
    reihenfolge.verify(aufenthalte).markeSperren();
    reihenfolge.verify(aufenthalte).eintrittKorrigieren(SAAT_ID, T1, true);
    reihenfolge
        .verify(aufenthalte)
        .nachtragen(
            List.of(
                new FlussStay(
                    null, KARTEN_ID, BOARD, CardStatus.BACKLOG, T0, T1, true, EndedBy.STATUS)));
    reihenfolge.verify(aufenthalte).markeSetzen(JETZT);
  }

  @Test
  void eineSaatMitBekanntemEintrittBleibtUndDerVerlaufEndetAnIhremBeginn() {
    // Fertig mit moved_to_done_at = T1: die Saat beginnt dort, der Verlauf endet genau dort.
    karte(CardStatus.DONE, T1, zeile("Backlog", BOARD, T0, T1), zeile("Done", BOARD, T1, null));
    saat(CardStatus.DONE, BOARD, T1, true);

    nachtrag.nachtragen();

    verify(aufenthalte, never()).eintrittKorrigieren(anyLong(), any(), anyBoolean());
    assertThat(geschrieben())
        .containsExactly(
            new FlussStay(
                null, KARTEN_ID, BOARD, CardStatus.BACKLOG, T0, T1, true, EndedBy.STATUS));
  }

  @Test
  void derSchnittLiegtAmBeginnDerSaatNichtAmBeginnDerErfassung() {
    // Die Saat beginnt bei T1, der Verlauf führt Done erst ab T2: Der Backlog-Aufenthalt reicht
    // über den Schnitt T1 und wird bis dorthin eine unbekannte Spanne, nicht bis T2 ein bekannter.
    karte(CardStatus.DONE, T2, zeile("Backlog", BOARD, T0, T2), zeile("Done", BOARD, T2, null));
    saat(CardStatus.DONE, BOARD, T1, true);

    nachtrag.nachtragen();

    verify(aufenthalte, never()).eintrittKorrigieren(anyLong(), any(), anyBoolean());
    assertThat(geschrieben())
        .containsExactly(
            new FlussStay(null, KARTEN_ID, BOARD, null, T0, T1, false, EndedBy.STATUS));
  }

  @Test
  void einFruehererDoneEintrittAlsDieSaatAendertDieBekannteSaatNicht() {
    karte(CardStatus.DONE, T2, zeile("Done", BOARD, T0, null));
    saat(CardStatus.DONE, BOARD, T2, true);

    nachtrag.nachtragen();

    verify(aufenthalte, never()).eintrittKorrigieren(anyLong(), any(), anyBoolean());
    assertThat(geschrieben()).isEmpty();
  }

  @Test
  void einAndererZustandAlsDieSaatMachtDieZeitBisZumSchnittUnbekannt() {
    karte(CardStatus.READY, null, zeile("In Progress", BOARD, T0, null));
    saat(CardStatus.READY, BOARD, SINCE, false);

    nachtrag.nachtragen();

    verify(aufenthalte, never()).eintrittKorrigieren(anyLong(), any(), anyBoolean());
    assertThat(geschrieben())
        .containsExactly(
            new FlussStay(null, KARTEN_ID, BOARD, null, T0, SINCE, false, EndedBy.STATUS));
  }

  @Test
  void einAnderesBoardAlsDieSaatEndetDieUnbekannteSpanneMitBoard() {
    karte(CardStatus.READY, null, zeile("Ready", ANDERES_BOARD, T0, null));
    saat(CardStatus.READY, BOARD, SINCE, false);

    nachtrag.nachtragen();

    assertThat(geschrieben())
        .containsExactly(
            new FlussStay(null, KARTEN_ID, ANDERES_BOARD, null, T0, SINCE, false, EndedBy.BOARD));
  }

  @Test
  void ohneSaatEndetEinOffenerVerlaufAlsUnbekannteSpanneAmBeginnDerErfassung() {
    karte(CardStatus.READY, null, zeile("Ready", BOARD, T0, null));

    nachtrag.nachtragen();

    assertThat(geschrieben())
        .containsExactly(
            new FlussStay(null, KARTEN_ID, BOARD, null, T0, SINCE, false, EndedBy.EXIT));
  }

  @Test
  void einAufenthaltNachDemSchnittBleibtDerErfassung() {
    Instant danach = SINCE.plusSeconds(60);
    karte(CardStatus.READY, null, zeile("Ready", BOARD, SINCE, danach));

    nachtrag.nachtragen();

    assertThat(geschrieben()).isEmpty();
  }

  @Test
  void hatEineKarteZweiFruehereAufenthalteGiltDerErsteAlsSaat() {
    karte(CardStatus.READY, null, zeile("Backlog", BOARD, T0, T1), zeile("Ready", BOARD, T1, null));
    FlussStay erste =
        new FlussStay(SAAT_ID, KARTEN_ID, BOARD, CardStatus.READY, SINCE, null, false, null);
    FlussStay spaetere =
        new FlussStay(SAAT_ID + 1, KARTEN_ID, BOARD, CardStatus.READY, SINCE, null, false, null);
    when(aufenthalte.findEnteredUpTo(SINCE)).thenReturn(List.of(erste, spaetere));

    nachtrag.nachtragen();

    verify(aufenthalte).eintrittKorrigieren(SAAT_ID, T1, true);
  }

  private void karte(CardStatus status, @Nullable Instant fertig, Spaltenzeile... zeilen) {
    karten.add(
        new Kartenverlauf(
            new Kartenstand(KARTEN_ID, status, zeilen[0].enteredAt(), fertig, null),
            List.of(zeilen),
            List.of()));
  }

  private static Spaltenzeile zeile(String name, long board, Instant von, @Nullable Instant bis) {
    return new Spaltenzeile(new Spaltenort(board * 10, board), name, von, bis);
  }

  private void saat(CardStatus status, long board, Instant ab, boolean bekannt) {
    when(aufenthalte.findEnteredUpTo(SINCE))
        .thenReturn(
            List.of(new FlussStay(SAAT_ID, KARTEN_ID, board, status, ab, null, bekannt, null)));
  }

  // unchecked: ArgumentCaptor.forClass kennt nur die rohe Klasse List, nicht List<FlussStay>.
  @SuppressWarnings("unchecked")
  private List<FlussStay> geschrieben() {
    ArgumentCaptor<List<FlussStay>> captor = ArgumentCaptor.forClass(List.class);
    verify(aufenthalte).nachtragen(captor.capture());
    return captor.getValue();
  }
}
