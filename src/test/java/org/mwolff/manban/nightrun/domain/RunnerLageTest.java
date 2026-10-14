package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.domain.NightRunOutcome.Verdict;
import org.mwolff.manban.nightrun.domain.RunnerLage.AktiverLauf;
import org.mwolff.manban.nightrun.domain.RunnerLage.Gesamt;
import org.mwolff.manban.nightrun.domain.RunnerLage.LaufZustand;
import org.mwolff.manban.nightrun.domain.RunnerLage.LetzterLauf;

/** Was der Runner jetzt tut (Issue #1727, Plan #1724 E2, E3, E8, E14). */
class RunnerLageTest {

  private static final Duration FRIST = Duration.ofMinutes(90);
  private static final Instant JETZT = Instant.parse("2026-10-14T02:00:00Z");

  private static NightRun lauf(
      long id,
      NightRunMode mode,
      Instant startedAt,
      long durationMs,
      boolean complete,
      @Nullable Instant updatedAt,
      @Nullable String noWorkReason,
      List<RunnerStufe> current,
      @Nullable Instant closedAt) {
    return new NightRun(
        id,
        7L,
        startedAt,
        mode,
        NightRunKind.NIGHT,
        durationMs,
        0,
        0,
        0,
        null,
        startedAt,
        NightRunOrigin.TOKEN,
        "runner",
        complete,
        updatedAt,
        null,
        noWorkReason,
        null,
        null,
        null,
        null,
        current,
        closedAt);
  }

  private static NightRun beendet(long id, Instant startedAt, long durationMs) {
    return lauf(
        id,
        NightRunMode.IMPLEMENTATION,
        startedAt,
        durationMs,
        true,
        startedAt.plusMillis(durationMs),
        null,
        List.of(),
        null);
  }

  private static NightRun laufend(
      long id, NightRunMode mode, Instant letzteMeldung, List<RunnerStufe> current) {
    return lauf(
        id,
        mode,
        letzteMeldung.minus(Duration.ofMinutes(10)),
        0,
        false,
        letzteMeldung,
        null,
        current,
        null);
  }

  private static RunnerStufe stufe(int karte, String stage, Instant beginn, boolean haelt) {
    return new RunnerStufe(karte, stage, beginn, null, "SESSION", haelt);
  }

  private static NightRunItem paket(long runId, int karte, NightRunState state) {
    return new NightRunItem(
        null,
        runId,
        7L,
        JETZT,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        karte,
        "P",
        state,
        state == NightRunState.RED ? NightRunErrorClass.CHECKS_RED : null,
        null,
        null,
        null,
        null,
        null,
        List.of());
  }

  @Test
  @DisplayName("Szenario: Kein Runner aktiv")
  void keinRunnerAktiv() {
    Instant start = Instant.parse("2026-10-13T22:00:00Z");
    NightRun letzter =
        lauf(
            3L,
            NightRunMode.IMPLEMENTATION,
            start,
            60_000,
            true,
            start.plusSeconds(60),
            "Nichts freigegeben",
            List.of(),
            null);
    NightRun frueher = beendet(2L, start.minus(Duration.ofDays(1)), 1_000);

    RunnerLage lage = RunnerLage.ermittle(List.of(letzter, frueher), List.of(), JETZT, FRIST);

    assertThat(lage.gesamt()).isEqualTo(Gesamt.KEIN_RUNNER);
    assertThat(lage.laeufe()).isEmpty();
    assertThat(lage.letzterLauf())
        .isEqualTo(
            new LetzterLauf(
                3L,
                NightRunMode.IMPLEMENTATION,
                start,
                start.plusSeconds(60),
                Verdict.NO_WORK,
                "Nichts freigegeben"));
  }

  @Test
  @DisplayName("Szenario: Noch nie ein Lauf")
  void nochNieEinLauf() {
    RunnerLage lage = RunnerLage.ermittle(List.of(), List.of(), JETZT, FRIST);

    assertThat(lage).isEqualTo(new RunnerLage(Gesamt.NIE, List.of(), null));
  }

  @Test
  @DisplayName("Szenario: Der Runner verstummt")
  void derRunnerVerstummt() {
    Instant letzteMeldung = JETZT.minus(FRIST).minusSeconds(1);
    RunnerStufe frueh = stufe(1700, "PLAN", letzteMeldung.minusSeconds(600), false);
    RunnerStufe spaet = stufe(1701, "UMSETZUNG", letzteMeldung.minusSeconds(60), true);
    NightRun mitStufen =
        laufend(5L, NightRunMode.IMPLEMENTATION, letzteMeldung, List.of(spaet, frueh));
    NightRun ohneStufen = laufend(6L, NightRunMode.CHAIN, letzteMeldung, List.of());

    RunnerLage lage = RunnerLage.ermittle(List.of(mitStufen, ohneStufen), List.of(), JETZT, FRIST);

    assertThat(lage.gesamt()).isEqualTo(Gesamt.AKTIV);
    assertThat(lage.laeufe())
        .containsExactly(
            new AktiverLauf(
                5L,
                NightRunMode.IMPLEMENTATION,
                mitStufen.startedAt(),
                letzteMeldung,
                LaufZustand.VERSTUMMT,
                List.of(spaet, frueh),
                spaet),
            new AktiverLauf(
                6L,
                NightRunMode.CHAIN,
                ohneStufen.startedAt(),
                letzteMeldung,
                LaufZustand.VERSTUMMT,
                List.of(),
                null));
  }

  /** Genau auf der Frist ist der Lauf noch nicht verstummt — dieselbe Regel wie im Befund. */
  @Test
  void genauAufDerFrist_laeuftDerLaufNoch_undTraegtKeineLetzteStufe() {
    Instant letzteMeldung = JETZT.minus(FRIST);
    RunnerStufe s = stufe(1701, "UMSETZUNG", letzteMeldung, true);
    NightRun lauf = laufend(5L, NightRunMode.IMPLEMENTATION, letzteMeldung, List.of(s));

    RunnerLage lage = RunnerLage.ermittle(List.of(lauf), List.of(), JETZT, FRIST);

    assertThat(lage.laeufe())
        .containsExactly(
            new AktiverLauf(
                5L,
                NightRunMode.IMPLEMENTATION,
                lauf.startedAt(),
                letzteMeldung,
                LaufZustand.LAEUFT,
                List.of(s),
                null));
  }

  /** Ohne fortgeschriebene Meldung ist der Start das letzte Lebenszeichen. */
  @Test
  void ohneUpdatedAt_istDerStartDieLetzteMeldung() {
    Instant start = JETZT.minusSeconds(60);
    NightRun lauf =
        lauf(5L, NightRunMode.IMPLEMENTATION, start, 0, false, null, null, List.of(), null);

    RunnerLage lage = RunnerLage.ermittle(List.of(lauf), List.of(), JETZT, FRIST);

    assertThat(lage.laeufe().getFirst().letzteMeldung()).isEqualTo(start);
    assertThat(lage.laeufe().getFirst().zustand()).isEqualTo(LaufZustand.LAEUFT);
  }

  @Test
  void vonHandBeendet() {
    Instant start = JETZT.minus(Duration.ofHours(5));
    Instant geschlossen = JETZT.minus(Duration.ofHours(1));
    NightRun lauf =
        lauf(
            8L,
            NightRunMode.CHAIN,
            start,
            0,
            false,
            start.plusSeconds(60),
            null,
            List.of(stufe(1665, "VORBEREITUNG", start, true)),
            geschlossen);

    RunnerLage lage = RunnerLage.ermittle(List.of(lauf), List.of(), JETZT, FRIST);

    assertThat(lage.gesamt()).isEqualTo(Gesamt.KEIN_RUNNER);
    assertThat(lage.laeufe()).isEmpty();
    assertThat(lage.letzterLauf())
        .isEqualTo(
            new LetzterLauf(8L, NightRunMode.CHAIN, start, geschlossen, Verdict.CLOSED, null));
  }

  /** Eine Kette und eine Umsetzungsnacht zugleich: jede mit ihrem eigenen Zustand (E8). */
  @Test
  void ketteUndUmsetzungsnachtZugleich_habenJeEigenenZustand() {
    NightRun kette =
        laufend(
            10L,
            NightRunMode.CHAIN,
            JETZT.minusSeconds(60),
            List.of(stufe(1665, "VORBEREITUNG", JETZT.minusSeconds(600), true)));
    NightRun nacht =
        laufend(11L, NightRunMode.IMPLEMENTATION, JETZT.minus(FRIST).minusSeconds(5), List.of());

    RunnerLage lage = RunnerLage.ermittle(List.of(kette, nacht), List.of(), JETZT, FRIST);

    assertThat(lage.gesamt()).isEqualTo(Gesamt.AKTIV);
    assertThat(lage.laeufe())
        .extracting(AktiverLauf::runId, AktiverLauf::zustand)
        .containsExactly(tuple(10L, LaufZustand.LAEUFT), tuple(11L, LaufZustand.VERSTUMMT));
  }

  /**
   * Szenario 1 nach E14: Die Kette läuft, und die beendete Umsetzungsnacht steht trotzdem als
   * letzter Lauf mit ihrem Grund da.
   */
  @Test
  void neben_einemLaufendenLauf_stehtDerLetzteBeendeteMitSeinemGrund() {
    Instant start = JETZT.minus(Duration.ofMinutes(30));
    NightRun kette =
        laufend(
            10L,
            NightRunMode.CHAIN,
            JETZT.minusSeconds(60),
            List.of(stufe(1665, "VORBEREITUNG", start, true)));
    NightRun nacht =
        lauf(
            11L,
            NightRunMode.IMPLEMENTATION,
            start,
            2_000,
            true,
            start.plusSeconds(2),
            "Umsetzung belegt",
            List.of(),
            null);

    RunnerLage lage = RunnerLage.ermittle(List.of(kette, nacht), List.of(), JETZT, FRIST);

    assertThat(lage.gesamt()).isEqualTo(Gesamt.AKTIV);
    assertThat(lage.letzterLauf())
        .isEqualTo(
            new LetzterLauf(
                11L,
                NightRunMode.IMPLEMENTATION,
                start,
                start.plusSeconds(2),
                Verdict.NO_WORK,
                "Umsetzung belegt"));
  }

  /** Der letzte Lauf ist der zuletzt beendete, nicht der zuletzt gestartete. */
  @Test
  void derLetzteLaufIstDerZuletztBeendete() {
    Instant start = JETZT.minus(Duration.ofHours(4));
    NightRun langerFrueherer = beendet(1L, start, Duration.ofHours(3).toMillis());
    NightRun kurzerSpaeterer = beendet(2L, start.plus(Duration.ofHours(1)), 60_000);

    RunnerLage lage =
        RunnerLage.ermittle(List.of(kurzerSpaeterer, langerFrueherer), List.of(), JETZT, FRIST);

    assertThat(lage.letzterLauf()).extracting(LetzterLauf::runId).isEqualTo(1L);
  }

  /** Das Ergebnis kommt aus dem Befund samt den Paketen des Laufs — fremde Pakete zählen nicht. */
  @Test
  void dasErgebnisWertetNurDiePaketeDesLetztenLaufsAus() {
    NightRun letzter = beendet(3L, JETZT.minus(Duration.ofHours(2)), 60_000);

    RunnerLage gruen =
        RunnerLage.ermittle(
            List.of(letzter),
            List.of(paket(3L, 1700, NightRunState.GREEN), paket(99L, 1701, NightRunState.RED)),
            JETZT,
            FRIST);
    RunnerLage rot =
        RunnerLage.ermittle(
            List.of(letzter), List.of(paket(3L, 1700, NightRunState.RED)), JETZT, FRIST);

    assertThat(gruen.letzterLauf()).extracting(LetzterLauf::ergebnis).isEqualTo(Verdict.SUCCEEDED);
    assertThat(rot.letzterLauf()).extracting(LetzterLauf::ergebnis).isEqualTo(Verdict.FAILED);
  }

  @Test
  void letzterBeendeter_waehltNachEnde_undUebergehtUnbeendete() {
    NightRun offen = laufend(1L, NightRunMode.CHAIN, JETZT, List.of());
    NightRun frueh = beendet(2L, JETZT.minus(Duration.ofHours(3)), 1_000);
    NightRun spaet = beendet(3L, JETZT.minus(Duration.ofHours(2)), 1_000);

    assertThat(RunnerLage.letzterBeendeter(List.of(offen, frueh, spaet))).contains(spaet);
    assertThat(RunnerLage.letzterBeendeter(List.of(offen))).isEmpty();
  }

  /** Bei gleichem Ende entscheidet der spätere Start. */
  @Test
  void letzterBeendeter_beiGleichemEnde_derSpaeterGestartete() {
    Instant ende = JETZT.minus(Duration.ofHours(1));
    NightRun lang = beendet(2L, ende.minusSeconds(120), 120_000);
    NightRun kurz = beendet(3L, ende.minusSeconds(60), 60_000);

    assertThat(RunnerLage.letzterBeendeter(List.of(lang, kurz))).contains(kurz);
    assertThat(RunnerLage.letzterBeendeter(List.of(kurz, lang))).contains(kurz);
  }

  /** Meldet sich ein von Hand beendeter Lauf doch noch vollständig, gilt seine Meldung. */
  @Test
  void einVonHandBeendeterUndDannVollstaendigGemeldeterLauf_endetMitSeinerDauer() {
    Instant start = JETZT.minus(Duration.ofHours(3));
    NightRun lauf =
        lauf(
            4L,
            NightRunMode.IMPLEMENTATION,
            start,
            60_000,
            true,
            start.plusSeconds(60),
            null,
            List.of(),
            JETZT.minus(Duration.ofHours(1)));

    RunnerLage lage = RunnerLage.ermittle(List.of(lauf), List.of(), JETZT, FRIST);

    assertThat(lage.letzterLauf())
        .isEqualTo(
            new LetzterLauf(
                4L,
                NightRunMode.IMPLEMENTATION,
                start,
                start.plusSeconds(60),
                Verdict.SUCCEEDED,
                null));
  }

  @Test
  void dieListenSindUnveraenderlich() {
    RunnerStufe s = stufe(1, "PLAN", JETZT, false);
    List<RunnerStufe> stufen = new ArrayList<>(List.of(s));
    AktiverLauf lauf =
        new AktiverLauf(1L, NightRunMode.CHAIN, JETZT, JETZT, LaufZustand.LAEUFT, stufen, null);
    List<AktiverLauf> laeufe = new ArrayList<>(List.of(lauf));
    RunnerLage lage = new RunnerLage(Gesamt.AKTIV, laeufe, null);
    stufen.clear();
    laeufe.clear();

    assertThat(lauf.stufen()).containsExactly(s);
    assertThat(lage.laeufe()).containsExactly(lauf);
  }
}
