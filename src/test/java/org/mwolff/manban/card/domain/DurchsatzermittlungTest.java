package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.domain.Durchsatzermittlung.Durchsatz;
import org.mwolff.manban.card.domain.Durchsatzermittlung.Kartenstand;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;

/** Durchsatz je Tag aus den Aufenthalten (Issue #1824, Plan #1819, A5, E1 bis E4). */
// PMD.TooManyMethods: Das Paket verlangt je Randfall der Zählregel einen eigenen Fall; eine kleine
// Testmethode je Fall bleibt lesbarer als eine Aufteilung auf mehrere Klassen.
@SuppressWarnings("PMD.TooManyMethods")
class DurchsatzermittlungTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
  private static final long BOARD = 1L;
  private static final long ANDERES_BOARD = 2L;

  /**
   * Jetzt ist der 2026-10-26, 13:00 Berliner Zeit: Der laufende Tag heißt 2026-10-27, die 30
   * abgeschlossenen Tage reichen vom 2026-09-27 bis zum 2026-10-26.
   */
  private static final Instant JETZT = lokal(LocalDate.of(2026, 10, 26), 13, 0);

  private static final LocalDate LAUFENDER_TAG = LocalDate.of(2026, 10, 27);
  private static final LocalDate ERSTER_TAG = LocalDate.of(2026, 9, 27);
  private static final LocalDate LETZTER_TAG = LocalDate.of(2026, 10, 26);

  /** Ein Tag mitten im Zeitraum. */
  private static final LocalDate TAG = LocalDate.of(2026, 10, 10);

  private static final Kartenstand OFFEN_IM_FLUSS =
      new Kartenstand(CardStatus.IN_REVIEW, false, false);

  private static Instant lokal(LocalDate datum, int stunde, int minute) {
    return LocalDateTime.of(datum, LocalTime.of(stunde, minute)).atZone(BERLIN).toInstant();
  }

  /**
   * 9:00 am Datum von {@code tag} — der Tag reicht von 12:00 am Vortag bis 12:00, 9:00 liegt darin.
   */
  private static Instant am(LocalDate tag) {
    return lokal(tag, 9, 0);
  }

  private static FlussStay stay(
      long karte,
      long board,
      @Nullable CardStatus status,
      Instant von,
      @Nullable Instant bis,
      @Nullable EndedBy endedBy) {
    return new FlussStay(null, karte, board, status, von, bis, true, endedBy);
  }

  private static FlussStay stay(
      long karte, @Nullable CardStatus status, Instant von, @Nullable Instant bis) {
    return stay(karte, BOARD, status, von, bis, bis == null ? null : EndedBy.STATUS);
  }

  /** Eine Karte, die am Vortag offen war und am Tag {@code tag} In Review erreicht. */
  private static List<FlussStay> erreichtReview(long karte, LocalDate tag) {
    Instant eintritt = am(tag);
    return List.of(
        stay(karte, CardStatus.IN_PROGRESS, eintritt.minus(1, ChronoUnit.DAYS), eintritt),
        stay(karte, CardStatus.IN_REVIEW, eintritt, null));
  }

  private static Durchsatz ermittle(List<FlussStay> aufenthalte) {
    return ermittle(BOARD, aufenthalte, alleImFluss(aufenthalte));
  }

  private static Durchsatz ermittle(
      long board, List<FlussStay> aufenthalte, Map<Long, Kartenstand> heute) {
    return Durchsatzermittlung.ermittle(BERLIN, JETZT, board, aufenthalte, heute);
  }

  private static Map<Long, Kartenstand> alleImFluss(List<FlussStay> aufenthalte) {
    Map<Long, Kartenstand> heute = new HashMap<>();
    aufenthalte.forEach(a -> heute.put(a.cardId(), OFFEN_IM_FLUSS));
    return heute;
  }

  private static int wert(Durchsatz durchsatz, LocalDate tag) {
    return durchsatz.tageswerte().get((int) ChronoUnit.DAYS.between(ERSTER_TAG, tag));
  }

  private static int summe(Durchsatz durchsatz) {
    return durchsatz.tageswerte().stream().mapToInt(Integer::intValue).sum();
  }

  @Test
  @DisplayName("E1: Eintritt in In Review zählt am Tag des Eintritts, 12 Uhr beginnt den nächsten")
  void eintrittInReviewZaehltAmTagDesEintritts() {
    Instant vorZwoelf = lokal(TAG, 11, 59);
    Instant zwoelf = lokal(TAG, 12, 0);
    List<FlussStay> aufenthalte = new ArrayList<>();
    aufenthalte.add(stay(1, CardStatus.IN_PROGRESS, vorZwoelf.minusSeconds(60), vorZwoelf));
    aufenthalte.add(stay(1, CardStatus.IN_REVIEW, vorZwoelf, null));
    aufenthalte.add(stay(2, CardStatus.READY, zwoelf.minusSeconds(60), zwoelf));
    aufenthalte.add(stay(2, CardStatus.IN_REVIEW, zwoelf, null));

    Durchsatz durchsatz = ermittle(aufenthalte);

    assertThat(wert(durchsatz, TAG)).isEqualTo(1);
    assertThat(wert(durchsatz, TAG.plusDays(1))).isEqualTo(1);
    assertThat(summe(durchsatz)).isEqualTo(2);
  }

  @Test
  @DisplayName("E1: direkt nach Done zählt wie In Review")
  void direktNachDoneZaehlt() {
    Instant eintritt = am(TAG);
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.IN_PROGRESS, eintritt.minusSeconds(3600), eintritt),
            stay(1, CardStatus.DONE, eintritt, null));

    assertThat(wert(ermittle(aufenthalte), TAG)).isEqualTo(1);
  }

  @Test
  @DisplayName("A5: ein Aufenthalt ohne Zustand vor In Review gilt als offen")
  void vorigerAufenthaltOhneZustandGiltAlsOffen() {
    Instant eintritt = am(TAG);
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, null, eintritt.minusSeconds(3600), eintritt),
            stay(1, CardStatus.IN_REVIEW, eintritt, null));

    assertThat(wert(ermittle(aufenthalte), TAG)).isEqualTo(1);
  }

  @Test
  @DisplayName("E2: der Wechsel von In Review nach Done zählt nicht neu")
  void wechselNachDoneZaehltNichtNeu() {
    Instant review = am(TAG);
    Instant done = am(TAG.plusDays(3));
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.IN_PROGRESS, review.minusSeconds(3600), review),
            stay(1, CardStatus.IN_REVIEW, review, done),
            stay(1, CardStatus.DONE, done, null));

    Durchsatz durchsatz = ermittle(aufenthalte);

    assertThat(wert(durchsatz, TAG)).isEqualTo(1);
    assertThat(summe(durchsatz)).isEqualTo(1);
  }

  @Test
  @DisplayName("E3: ein Erreichen mit unbekanntem Eintritt zählt nicht")
  void unbekannterEintrittZaehltNicht() {
    Instant eintritt = am(TAG);
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.IN_PROGRESS, eintritt.minusSeconds(3600), eintritt),
            new FlussStay(null, 1, BOARD, CardStatus.IN_REVIEW, eintritt, null, false, null),
            stay(2, null, eintritt.minusSeconds(3600), eintritt),
            new FlussStay(null, 2, BOARD, CardStatus.DONE, eintritt, null, false, null),
            new FlussStay(null, 3, BOARD, CardStatus.DONE, eintritt, null, false, null));

    assertThat(summe(ermittle(aufenthalte))).isZero();
  }

  @Test
  @DisplayName("E3: ein jüngeres Erreichen mit unbekanntem Eintritt verdrängt das ältere bekannte")
  void unbekanntesJuengeresErreichenVerdraengtDasAeltere() {
    Instant erstes = am(TAG);
    Instant zurueck = am(TAG.plusDays(1));
    Instant zweites = am(TAG.plusDays(2));
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.IN_PROGRESS, erstes.minusSeconds(3600), erstes),
            stay(1, CardStatus.IN_REVIEW, erstes, zurueck),
            stay(1, null, zurueck, zweites),
            new FlussStay(null, 1, BOARD, CardStatus.IN_REVIEW, zweites, null, false, null));

    assertThat(summe(ermittle(aufenthalte))).isZero();
  }

  @Test
  @DisplayName("AK 17: ein Rückläufer zählt nur am letzten Erreichen")
  void ruecklaeuferZaehltNurAmLetztenErreichen() {
    Instant erstes = am(TAG);
    Instant zurueck = am(TAG.plusDays(1));
    Instant zweites = am(TAG.plusDays(4));
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.IN_PROGRESS, erstes.minusSeconds(3600), erstes),
            stay(1, CardStatus.IN_REVIEW, erstes, zurueck),
            stay(1, CardStatus.IN_PROGRESS, zurueck, zweites),
            stay(1, CardStatus.IN_REVIEW, zweites, null));

    Durchsatz durchsatz = ermittle(aufenthalte);

    assertThat(wert(durchsatz, TAG.plusDays(4))).isEqualTo(1);
    assertThat(summe(durchsatz)).isEqualTo(1);
  }

  @Test
  @DisplayName("AK 17: ein Paket, das gerade wieder offen ist, zählt gar nicht")
  void geradeOffenesPaketZaehltNicht() {
    Instant erstes = am(TAG);
    Instant zurueck = am(TAG.plusDays(1));
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.IN_PROGRESS, erstes.minusSeconds(3600), erstes),
            stay(1, CardStatus.IN_REVIEW, erstes, zurueck),
            stay(1, CardStatus.BACKLOG, zurueck, null));

    assertThat(summe(ermittle(aufenthalte))).isZero();
  }

  @Test
  @DisplayName(
      "AK 14: jedes Arbeitspaket des Boards zählt — ein Vorhaben kennt die Ermittlung nicht")
  void paketeOhneVorhabenZaehlen() {
    List<FlussStay> aufenthalte = new ArrayList<>();
    aufenthalte.addAll(erreichtReview(1, TAG));
    aufenthalte.addAll(erreichtReview(2, TAG));
    aufenthalte.addAll(erreichtReview(3, TAG.plusDays(2)));

    Durchsatz durchsatz = ermittle(aufenthalte);

    assertThat(wert(durchsatz, TAG)).isEqualTo(2);
    assertThat(wert(durchsatz, TAG.plusDays(2))).isEqualTo(1);
  }

  @Test
  @DisplayName("AK 15: der laufende Tag zählt nicht, nur die 30 abgeschlossenen Tage davor")
  void laufenderTagZaehltNicht() {
    List<FlussStay> aufenthalte = new ArrayList<>();
    aufenthalte.addAll(erreichtReview(1, LAUFENDER_TAG));
    aufenthalte.addAll(erreichtReview(2, ERSTER_TAG.minusDays(1)));
    aufenthalte.addAll(erreichtReview(3, ERSTER_TAG));
    aufenthalte.addAll(erreichtReview(4, LETZTER_TAG));

    Durchsatz durchsatz = ermittle(aufenthalte);

    assertThat(durchsatz.tageswerte().getFirst()).isEqualTo(1);
    assertThat(durchsatz.tageswerte().getLast()).isEqualTo(1);
    assertThat(summe(durchsatz)).isEqualTo(2);
  }

  @Test
  @DisplayName("E4: ein Umzug nach In Review zählt genau einmal, auf dem Herkunftsboard")
  void umzugNachReviewZaehltGenauEinmal() {
    Instant review = am(TAG);
    Instant umzug = am(TAG.plusDays(2));
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.IN_PROGRESS, review.minusSeconds(3600), review),
            stay(1, BOARD, CardStatus.IN_REVIEW, review, umzug, EndedBy.BOARD),
            stay(1, ANDERES_BOARD, CardStatus.IN_REVIEW, umzug, null, null));
    Map<Long, Kartenstand> heute = alleImFluss(aufenthalte);

    Durchsatz herkunft = ermittle(BOARD, aufenthalte, heute);
    Durchsatz ziel = ermittle(ANDERES_BOARD, aufenthalte, heute);

    assertThat(wert(herkunft, TAG)).isEqualTo(1);
    assertThat(summe(herkunft)).isEqualTo(1);
    assertThat(summe(ziel)).isZero();
  }

  @Test
  @DisplayName("E4: wieder offen auf dem Zielboard — zählt auf dem Herkunftsboard weiter")
  void wiederOffenAufDemZielboardZaehltAufDerHerkunft() {
    Instant review = am(TAG);
    Instant umzug = am(TAG.plusDays(2));
    Instant zurueck = am(TAG.plusDays(3));
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.IN_PROGRESS, review.minusSeconds(3600), review),
            stay(1, BOARD, CardStatus.IN_REVIEW, review, umzug, EndedBy.BOARD),
            stay(1, ANDERES_BOARD, CardStatus.IN_REVIEW, umzug, zurueck, EndedBy.STATUS),
            stay(1, ANDERES_BOARD, CardStatus.IN_PROGRESS, zurueck, null, null));
    Map<Long, Kartenstand> heute =
        Map.of(1L, new Kartenstand(CardStatus.IN_PROGRESS, false, false));

    assertThat(wert(ermittle(BOARD, aufenthalte, heute), TAG)).isEqualTo(1);
    assertThat(summe(ermittle(ANDERES_BOARD, aufenthalte, heute))).isZero();
  }

  @Test
  @DisplayName("Rückkehr aus dem Archiv in In Review zählt nicht ein zweites Mal")
  void rueckkehrAusDemArchivZaehltNichtNeu() {
    Instant review = am(TAG);
    Instant archiv = am(TAG.plusDays(1));
    Instant zurueck = am(TAG.plusDays(5));
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.IN_PROGRESS, review.minusSeconds(3600), review),
            stay(1, BOARD, CardStatus.IN_REVIEW, review, archiv, EndedBy.EXIT),
            stay(1, CardStatus.IN_REVIEW, zurueck, null));

    Durchsatz durchsatz = ermittle(aufenthalte);

    assertThat(wert(durchsatz, TAG)).isEqualTo(1);
    assertThat(summe(durchsatz)).isEqualTo(1);
  }

  @Test
  @DisplayName("Rückkehr aus dem Papierkorb nach Done zählt nicht ein zweites Mal")
  void rueckkehrAusDemPapierkorbZaehltNichtNeu() {
    Instant done = am(TAG);
    Instant papierkorb = am(TAG.plusDays(1));
    Instant zurueck = am(TAG.plusDays(5));
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.READY, done.minusSeconds(3600), done),
            stay(1, BOARD, CardStatus.DONE, done, papierkorb, EndedBy.EXIT),
            stay(1, CardStatus.DONE, zurueck, null));

    Durchsatz durchsatz = ermittle(aufenthalte);

    assertThat(wert(durchsatz, TAG)).isEqualTo(1);
    assertThat(summe(durchsatz)).isEqualTo(1);
  }

  @Test
  @DisplayName("Rückkehr von einem fremden Board in In Review zählt nicht ein zweites Mal")
  void rueckkehrVomFremdenBoardZaehltNichtNeu() {
    Instant review = am(TAG);
    Instant hin = am(TAG.plusDays(1));
    Instant zurueck = am(TAG.plusDays(5));
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.IN_PROGRESS, review.minusSeconds(3600), review),
            stay(1, BOARD, CardStatus.IN_REVIEW, review, hin, EndedBy.BOARD),
            stay(1, ANDERES_BOARD, CardStatus.IN_REVIEW, hin, zurueck, EndedBy.BOARD),
            stay(1, BOARD, CardStatus.IN_REVIEW, zurueck, null, null));
    Map<Long, Kartenstand> heute = alleImFluss(aufenthalte);

    Durchsatz durchsatz = ermittle(BOARD, aufenthalte, heute);

    assertThat(wert(durchsatz, TAG)).isEqualTo(1);
    assertThat(summe(durchsatz)).isEqualTo(1);
    assertThat(summe(ermittle(ANDERES_BOARD, aufenthalte, heute))).isZero();
  }

  @Test
  @DisplayName("Auf dem fremden Board fertig geworden: zählt dort, nach der Rückkehr nicht hier")
  void fertigAufDemFremdenBoardZaehltDort() {
    Instant hin = am(TAG);
    Instant review = am(TAG.plusDays(1));
    Instant zurueck = am(TAG.plusDays(5));
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, BOARD, CardStatus.IN_PROGRESS, hin.minusSeconds(3600), hin, EndedBy.BOARD),
            stay(1, ANDERES_BOARD, CardStatus.IN_PROGRESS, hin, review, EndedBy.STATUS),
            stay(1, ANDERES_BOARD, CardStatus.IN_REVIEW, review, zurueck, EndedBy.BOARD),
            stay(1, BOARD, CardStatus.IN_REVIEW, zurueck, null, null));
    Map<Long, Kartenstand> heute = alleImFluss(aufenthalte);

    assertThat(summe(ermittle(BOARD, aufenthalte, heute))).isZero();
    assertThat(wert(ermittle(ANDERES_BOARD, aufenthalte, heute), TAG.plusDays(1))).isEqualTo(1);
  }

  @Test
  @DisplayName("Hier fertig, drüben wieder offen und fertig, zurück: zählt nur drüben")
  void druebenWiederOffenUndFertigZaehltNurDort() {
    Instant review = am(TAG);
    Instant hin = am(TAG.plusDays(1));
    Instant offen = am(TAG.plusDays(2));
    Instant wieder = am(TAG.plusDays(3));
    Instant zurueck = am(TAG.plusDays(5));
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.IN_PROGRESS, review.minusSeconds(3600), review),
            stay(1, BOARD, CardStatus.IN_REVIEW, review, hin, EndedBy.BOARD),
            stay(1, ANDERES_BOARD, CardStatus.IN_REVIEW, hin, offen, EndedBy.STATUS),
            stay(1, ANDERES_BOARD, CardStatus.IN_PROGRESS, offen, wieder, EndedBy.STATUS),
            stay(1, ANDERES_BOARD, CardStatus.IN_REVIEW, wieder, zurueck, EndedBy.BOARD),
            stay(1, BOARD, CardStatus.IN_REVIEW, zurueck, null, null));
    Map<Long, Kartenstand> heute = alleImFluss(aufenthalte);

    assertThat(summe(ermittle(BOARD, aufenthalte, heute))).isZero();
    assertThat(wert(ermittle(ANDERES_BOARD, aufenthalte, heute), TAG.plusDays(3))).isEqualTo(1);
  }

  @Test
  @DisplayName("EXIT nach In Review oder Done: die archivierte Karte zählt weiter")
  void archivierteKarteZaehltWeiter() {
    Instant review = am(TAG);
    Instant archiv = am(TAG.plusDays(1));
    List<FlussStay> aufenthalte =
        List.of(
            stay(1, CardStatus.IN_PROGRESS, review.minusSeconds(3600), review),
            stay(1, BOARD, CardStatus.IN_REVIEW, review, archiv, EndedBy.EXIT),
            stay(2, CardStatus.IN_PROGRESS, review.minusSeconds(3600), review),
            stay(2, BOARD, CardStatus.DONE, review, archiv, EndedBy.EXIT));
    Map<Long, Kartenstand> heute =
        Map.of(
            1L, new Kartenstand(CardStatus.IN_REVIEW, true, false),
            2L, new Kartenstand(CardStatus.DONE, true, false));

    assertThat(wert(ermittle(BOARD, aufenthalte, heute), TAG)).isEqualTo(2);
  }

  @Test
  @DisplayName("EXIT in den Papierkorb, nach Artwechsel oder ohne bekannten Stand zählt nicht")
  void papierkorbArtwechselUndUnbekannterStandZaehlenNicht() {
    Instant review = am(TAG);
    Instant austritt = am(TAG.plusDays(1));
    List<FlussStay> aufenthalte = new ArrayList<>();
    for (long karte = 1; karte <= 5; karte++) {
      aufenthalte.add(stay(karte, CardStatus.IN_PROGRESS, review.minusSeconds(3600), review));
      aufenthalte.add(stay(karte, BOARD, CardStatus.IN_REVIEW, review, austritt, EndedBy.EXIT));
    }
    Map<Long, Kartenstand> heute =
        Map.of(
            1L, new Kartenstand(CardStatus.IN_REVIEW, false, true),
            2L, new Kartenstand(CardStatus.IN_REVIEW, true, true),
            3L, new Kartenstand(null, false, false),
            4L, new Kartenstand(null, true, false));

    assertThat(summe(ermittle(BOARD, aufenthalte, heute))).isZero();
  }

  @Test
  @DisplayName("Tageswerte: genau 30, der älteste zuerst, Nulltage eingeschlossen")
  void dreissigTageswerteAeltesterZuerst() {
    List<FlussStay> aufenthalte = new ArrayList<>();
    aufenthalte.addAll(erreichtReview(1, ERSTER_TAG));
    aufenthalte.addAll(erreichtReview(2, ERSTER_TAG.plusDays(1)));
    aufenthalte.addAll(erreichtReview(3, ERSTER_TAG.plusDays(1)));
    aufenthalte.addAll(erreichtReview(4, LETZTER_TAG));

    Durchsatz durchsatz = ermittle(aufenthalte);

    List<Integer> erwartet = new ArrayList<>(Collections.nCopies(Durchsatzermittlung.TAGE, 0));
    erwartet.set(0, 1);
    erwartet.set(1, 2);
    erwartet.set(29, 1);
    assertThat(Durchsatzermittlung.TAGE).isEqualTo(30);
    assertThat(durchsatz.tageswerte()).containsExactlyElementsOf(erwartet);
    assertThat(durchsatz.tageMitPaketen()).isEqualTo(3);
  }

  @Test
  @DisplayName("Ohne Aufenthalte: 30 Nulltage und kein Tag mit Paketen")
  void ohneAufenthalte() {
    Durchsatz durchsatz = ermittle(List.of());

    assertThat(durchsatz.tageswerte()).hasSize(30).containsOnly(0);
    assertThat(durchsatz.tageMitPaketen()).isZero();
    assertThat(Durchsatzermittlung.MINDESTTAGE).isEqualTo(5);
  }
}
