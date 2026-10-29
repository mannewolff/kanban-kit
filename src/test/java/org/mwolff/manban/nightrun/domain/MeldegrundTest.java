package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mwolff.manban.nightrun.domain.Meldegrund.Grund;
import org.mwolff.manban.nightrun.domain.Meldegrund.Zustand;

/**
 * Wann ein Lauf hängt oder gescheitert ist (Issue #1994, Plan #1989 E11, fachliche Quelle #1961 AK
 * 10).
 *
 * <p>Anders als {@link NightRunOutcome} zählt ein rotes Paket schon, während der Lauf
 * weiterarbeitet — die Nachricht soll sofort kommen (AK 11). Die Stille rechnet weiter {@link
 * NightRunOutcome#verstummt}, hier wird nur ihr Rand festgehalten.
 */
// PMD.TooManyMethods: Das Paket verlangt jeden Grund laufend und beendet, die Grenzfaelle und den
// Vorrang je einzeln — Faelle werden nicht zusammengelegt, um eine Zahl zu druecken.
@SuppressWarnings("PMD.TooManyMethods")
class MeldegrundTest {

  private static final Instant START = Instant.parse("2026-10-28T22:00:00Z");
  private static final Duration FRIST = Duration.ofMinutes(90);
  private static final Instant LEBENSZEICHEN = START.plus(Duration.ofMinutes(30));

  /** Innerhalb der Frist nach dem letzten Lebenszeichen. */
  private static final Instant FRISCH = LEBENSZEICHEN.plus(Duration.ofMinutes(10));

  private static NightRun lauf(boolean complete) {
    return lauf(NightRunKind.NIGHT, NightRunMode.IMPLEMENTATION, complete, null, null, null, null);
  }

  private static NightRun lauf(
      NightRunKind kind,
      NightRunMode mode,
      boolean complete,
      @Nullable String noWorkReason,
      @Nullable String abortReason,
      @Nullable NightRunAbortKind abortKind,
      @Nullable Instant closedAt) {
    return new NightRun(
        1L,
        7L,
        START,
        mode,
        kind,
        1_000,
        0,
        0,
        0,
        null,
        START,
        NightRunOrigin.TOKEN,
        "runner",
        complete,
        LEBENSZEICHEN,
        null,
        noWorkReason,
        null,
        abortReason,
        abortKind,
        null,
        List.of(),
        closedAt,
        null);
  }

  private static NightRunItem paket(
      int karte, NightRunState state, @Nullable NightRunErrorClass errorClass) {
    return new NightRunItem(
        karte * 10L,
        1L,
        7L,
        START,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        karte,
        "Paket " + karte,
        state,
        errorClass,
        1_000L,
        null,
        "Auszug " + karte,
        null,
        null,
        null,
        List.of(),
        List.of());
  }

  private static Optional<Meldegrund> grund(NightRun run, List<NightRunItem> items) {
    return Meldegrund.von(run, items, FRISCH, FRIST);
  }

  private static Optional<Grund> art(NightRun run, NightRunItem... items) {
    return grund(run, List.of(items)).map(Meldegrund::grund);
  }

  @Test
  void einLaufenderLaufOhneAuffaelligesPaketHatKeinenGrund() {
    assertThat(
            art(
                lauf(false),
                paket(1, NightRunState.GREEN, null),
                paket(2, NightRunState.GREY, null)))
        .isEmpty();
  }

  @Test
  void einRotesPaketMeldetSchonWaehrendDerLaufWeiterarbeitet() {
    NightRunItem rot = paket(1, NightRunState.RED, NightRunErrorClass.CHECKS_RED);

    Optional<Meldegrund> grund = grund(lauf(false), List.of(rot));

    assertThat(grund).map(Meldegrund::grund).contains(Grund.GESCHEITERT);
    assertThat(grund).map(Meldegrund::paket).contains(rot);
    assertThat(grund).map(Meldegrund::zustand).contains(Zustand.GESCHEITERT);
  }

  @Test
  void einRotesPaketMeldetAuchImBeendetenLauf() {
    assertThat(art(lauf(true), paket(1, NightRunState.RED, NightRunErrorClass.CHECKS_RED)))
        .contains(Grund.GESCHEITERT);
  }

  @Test
  void einHarterAbbruchImPaketIstGescheitert() {
    assertThat(art(lauf(true), paket(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT)))
        .contains(Grund.GESCHEITERT);
  }

  @Test
  void einRotesPaketOhneFehlerklasseIstGescheitert() {
    assertThat(art(lauf(false), paket(1, NightRunState.RED, null))).contains(Grund.GESCHEITERT);
  }

  @ParameterizedTest
  @EnumSource(
      value = NightRunState.class,
      names = {"YELLOW", "RED"})
  void dasZeitbudgetZaehltGelbUndRot_laufendUndBeendet(NightRunState state) {
    NightRunItem paket = paket(1, state, NightRunErrorClass.TIME_BUDGET_EXCEEDED);

    assertThat(art(lauf(false), paket)).contains(Grund.ZEITBUDGET);
    assertThat(art(lauf(true), paket)).contains(Grund.ZEITBUDGET);
    assertThat(grund(lauf(true), List.of(paket)))
        .map(Meldegrund::zustand)
        .contains(Zustand.GESCHEITERT);
  }

  @Test
  void einGelbesPaketMitAndererFehlerklasseMeldetNicht() {
    assertThat(art(lauf(true), paket(1, NightRunState.YELLOW, NightRunErrorClass.REVIEWER_FAILED)))
        .isEmpty();
  }

  @Test
  void einGrauesPaketMitFehlerklasseWartet_laufendUndBeendet() {
    NightRunItem wartet = paket(1, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION);

    assertThat(art(lauf(false), wartet)).contains(Grund.WARTET);
    assertThat(art(lauf(true), wartet)).contains(Grund.WARTET);
    assertThat(grund(lauf(false), List.of(wartet)))
        .map(Meldegrund::zustand)
        .contains(Zustand.HAENGT);
  }

  @ParameterizedTest
  @EnumSource(
      value = NightRunState.class,
      names = {"GREY", "YELLOW", "RED"})
  void einFestgefahrenesPaketHaengt_laufendUndBeendet(NightRunState state) {
    NightRunItem stuck = paket(1, state, NightRunErrorClass.STUCK);

    assertThat(art(lauf(false), stuck)).contains(Grund.FESTGEFAHREN);
    assertThat(art(lauf(true), stuck)).contains(Grund.FESTGEFAHREN);
    assertThat(grund(lauf(true), List.of(stuck))).map(Meldegrund::zustand).contains(Zustand.HAENGT);
  }

  @ParameterizedTest
  @EnumSource(NightRunErrorClass.class)
  void einGruenesPaketMeldetNie(NightRunErrorClass errorClass) {
    assertThat(art(lauf(true), paket(1, NightRunState.GREEN, errorClass))).isEmpty();
  }

  @Test
  @DisplayName("Szenario: Lauf verstummt")
  void szenarioLaufVerstummt() {
    Instant nachNeunzigMinuten = LEBENSZEICHEN.plus(FRIST).plusSeconds(1);

    Optional<Meldegrund> grund =
        Meldegrund.von(
            lauf(false), List.of(paket(1, NightRunState.GREEN, null)), nachNeunzigMinuten, FRIST);

    assertThat(grund).map(Meldegrund::grund).contains(Grund.VERSTUMMT);
    assertThat(grund).map(Meldegrund::zustand).contains(Zustand.GESCHEITERT);
    assertThat(grund.orElseThrow().paket()).isNull();
  }

  @Test
  void genauAufDerFristIstDerLaufNochNichtVerstummt() {
    assertThat(Meldegrund.von(lauf(false), List.of(), LEBENSZEICHEN.plus(FRIST), FRIST)).isEmpty();
  }

  @Test
  void einBeendeterLaufVerstummtNicht() {
    assertThat(Meldegrund.von(lauf(true), List.of(), LEBENSZEICHEN.plus(Duration.ofDays(1)), FRIST))
        .isEmpty();
  }

  @Test
  void einVonHandBeendeterLaufMeldetNicht_auchNichtMitRotemPaket() {
    NightRun geschlossen =
        lauf(
            NightRunKind.NIGHT,
            NightRunMode.IMPLEMENTATION,
            false,
            null,
            null,
            null,
            LEBENSZEICHEN);

    assertThat(
            Meldegrund.von(
                geschlossen,
                List.of(paket(1, NightRunState.RED, NightRunErrorClass.CHECKS_RED)),
                LEBENSZEICHEN.plus(Duration.ofDays(1)),
                FRIST))
        .isEmpty();
  }

  @Test
  void einAbgeschlossenerLaufMitKennzeichnungZaehltSeinePakete() {
    NightRun gemeldet =
        lauf(NightRunKind.NIGHT, NightRunMode.IMPLEMENTATION, true, null, null, null, START);

    assertThat(art(gemeldet, paket(1, NightRunState.RED, NightRunErrorClass.CHECKS_RED)))
        .contains(Grund.GESCHEITERT);
  }

  @Test
  void einEndeAnDerEigenenGrenzeMeldetNicht() {
    NightRun anDerGrenze =
        new NightRun(
            1L,
            7L,
            START,
            NightRunMode.CHAIN,
            NightRunKind.NIGHT,
            1_000,
            2,
            1,
            0,
            null,
            START,
            NightRunOrigin.TOKEN,
            "runner",
            true,
            LEBENSZEICHEN,
            null,
            null,
            new NightRunBudget(30, 20, 120, null, new BigDecimal("5.00"), null, List.of()),
            null,
            null,
            null,
            List.of(),
            null,
            null);

    assertThat(
            art(
                anDerGrenze,
                paket(1, NightRunState.GREEN, null),
                paket(2, NightRunState.GREEN, null),
                paket(3, NightRunState.GREY, null)))
        .isEmpty();
  }

  @Test
  void einLaufOhneArbeitMeldetNicht() {
    NightRun ohneArbeit =
        lauf(
            NightRunKind.NIGHT,
            NightRunMode.IMPLEMENTATION,
            true,
            NightRunOutcome.GRUND_UNBEKANNT,
            null,
            null,
            null);

    assertThat(art(ohneArbeit)).isEmpty();
  }

  @Test
  void einNichtAngelaufenerLaufMeldetNicht() {
    NightRun nichtAngelaufen =
        lauf(
            NightRunKind.NIGHT,
            NightRunMode.CHAIN,
            true,
            null,
            "Vorflug: Arbeitsbaum nicht sauber",
            NightRunAbortKind.REPORTED,
            null);

    assertThat(art(nichtAngelaufen, paket(1, NightRunState.GREY, null))).isEmpty();
  }

  @Test
  void eineInteraktiveSitzungMeldetNie() {
    NightRun sitzung =
        lauf(NightRunKind.INTERACTIVE, NightRunMode.IMPLEMENTATION, false, null, null, null, null);

    assertThat(art(sitzung, paket(1, NightRunState.RED, NightRunErrorClass.CHECKS_RED))).isEmpty();
    assertThat(Meldegrund.von(sitzung, List.of(), LEBENSZEICHEN.plus(Duration.ofDays(1)), FRIST))
        .isEmpty();
  }

  @Test
  void gescheitertSchlaegtZeitbudgetVerstummtFestgefahrenUndWarten() {
    NightRunItem rot = paket(5, NightRunState.RED, NightRunErrorClass.CHECKS_RED);

    Optional<Meldegrund> grund =
        Meldegrund.von(
            lauf(false),
            List.of(
                paket(1, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION),
                paket(2, NightRunState.GREY, NightRunErrorClass.STUCK),
                paket(3, NightRunState.YELLOW, NightRunErrorClass.TIME_BUDGET_EXCEEDED),
                rot),
            LEBENSZEICHEN.plus(Duration.ofDays(1)),
            FRIST);

    assertThat(grund).map(Meldegrund::grund).contains(Grund.GESCHEITERT);
    assertThat(grund).map(Meldegrund::paket).contains(rot);
  }

  @Test
  void zeitbudgetSchlaegtVerstummt() {
    assertThat(
            Meldegrund.von(
                    lauf(false),
                    List.of(paket(1, NightRunState.RED, NightRunErrorClass.TIME_BUDGET_EXCEEDED)),
                    LEBENSZEICHEN.plus(Duration.ofDays(1)),
                    FRIST)
                .map(Meldegrund::grund))
        .contains(Grund.ZEITBUDGET);
  }

  @Test
  void verstummtSchlaegtFestgefahrenUndWarten() {
    Optional<Meldegrund> grund =
        Meldegrund.von(
            lauf(false),
            List.of(
                paket(1, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION),
                paket(2, NightRunState.GREY, NightRunErrorClass.STUCK)),
            LEBENSZEICHEN.plus(Duration.ofDays(1)),
            FRIST);

    assertThat(grund).map(Meldegrund::grund).contains(Grund.VERSTUMMT);
  }

  @Test
  void festgefahrenSchlaegtWarten() {
    NightRunItem stuck = paket(2, NightRunState.GREY, NightRunErrorClass.STUCK);

    Optional<Meldegrund> grund =
        grund(
            lauf(false),
            List.of(paket(1, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION), stuck));

    assertThat(grund).map(Meldegrund::paket).contains(stuck);
  }

  @Test
  void unterGleichrangigenPaketenGiltDasErste() {
    NightRunItem erstes = paket(1, NightRunState.RED, NightRunErrorClass.CHECKS_RED);

    assertThat(
            grund(
                    lauf(false),
                    List.of(erstes, paket(2, NightRunState.RED, NightRunErrorClass.HARD_ABORT)))
                .map(Meldegrund::paket))
        .contains(erstes);
  }

  /** Dieselbe Regel wie im Befund (Issue #1123): In einer Kette ist das letzte der Bruch. */
  @Test
  void inEinerKetteGiltUnterGleichrangigenDasLetzte() {
    NightRun kette = lauf(NightRunKind.NIGHT, NightRunMode.CHAIN, true, null, null, null, null);
    NightRunItem letztes = paket(2, NightRunState.RED, NightRunErrorClass.HARD_ABORT);

    assertThat(
            grund(
                    kette,
                    List.of(paket(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT), letztes))
                .map(Meldegrund::paket))
        .contains(letztes);
  }

  @Test
  void ohneLebenszeichenZaehltDerStart() {
    NightRun nieFortgeschrieben =
        new NightRun(
            1L,
            7L,
            START,
            NightRunMode.IMPLEMENTATION,
            NightRunKind.NIGHT,
            0,
            0,
            0,
            0,
            null,
            START,
            NightRunOrigin.UPLOAD,
            null,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            null);

    assertThat(
            Meldegrund.von(nieFortgeschrieben, List.of(), START.plus(FRIST).plusSeconds(1), FRIST)
                .map(Meldegrund::grund))
        .contains(Grund.VERSTUMMT);
  }

  @Test
  void jederGrundNenntSeinenZustand() {
    assertThat(Grund.GESCHEITERT.zustand()).isEqualTo(Zustand.GESCHEITERT);
    assertThat(Grund.ZEITBUDGET.zustand()).isEqualTo(Zustand.GESCHEITERT);
    assertThat(Grund.VERSTUMMT.zustand()).isEqualTo(Zustand.GESCHEITERT);
    assertThat(Grund.FESTGEFAHREN.zustand()).isEqualTo(Zustand.HAENGT);
    assertThat(Grund.WARTET.zustand()).isEqualTo(Zustand.HAENGT);
  }
}
