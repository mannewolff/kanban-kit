package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.domain.Meldegrund.Zustand;
import org.mwolff.manban.nightrun.domain.MobilerLauf.LetzteMeldung;
import org.mwolff.manban.nightrun.domain.MobilerLauf.MeldungsArt;

/**
 * Zustand, Dauer und letzte Meldung eines Laufs auf dem Handy (Issue #1994, Plan #1989 E19).
 *
 * <p>Die letzte Meldung folgt einer festen Reihenfolge: Abbruchgrund, Auszug des Pakets, Stille,
 * Stufe. Jeder Fall hier hält eine Stufe fest, auf die die vorige nicht zutrifft.
 */
class MobilerLaufTest {

  private static final Instant JETZT = Instant.parse("2026-10-29T08:00:00Z");
  private static final Duration FRIST = Duration.ofMinutes(90);
  private static final Duration GEMELDET = Duration.ofHours(1);

  private static Instant vorMinuten(long minuten) {
    return JETZT.minus(Duration.ofMinutes(minuten));
  }

  private static NightRun lauf(
      Instant start,
      @Nullable Instant updatedAt,
      boolean complete,
      @Nullable String abortReason,
      List<RunnerStufe> current,
      @Nullable Instant closedAt) {
    return new NightRun(
        11L,
        7L,
        start,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        GEMELDET.toMillis(),
        0,
        0,
        0,
        null,
        start,
        NightRunOrigin.TOKEN,
        "runner",
        complete,
        updatedAt,
        null,
        null,
        null,
        abortReason,
        null,
        null,
        current,
        closedAt,
        null);
  }

  private static NightRun offen(Instant start, @Nullable Instant updatedAt) {
    return lauf(start, updatedAt, false, null, List.of(), null);
  }

  private static NightRunItem paket(
      int karte, NightRunState state, NightRunErrorClass errorClass, @Nullable String excerpt) {
    return new NightRunItem(
        110L + karte,
        11L,
        7L,
        JETZT,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        karte,
        "Paket " + karte,
        state,
        errorClass,
        1_000L,
        null,
        excerpt,
        null,
        null,
        null,
        List.of(),
        List.of());
  }

  private static MobilerLauf sicht(NightRun run, NightRunItem... items) {
    return MobilerLauf.von(run, List.of(items), JETZT, FRIST);
  }

  @Test
  void einOffenerLaufLaeuft_undDauertBisJetzt() {
    MobilerLauf sicht = sicht(offen(vorMinuten(20), vorMinuten(5)));

    assertThat(sicht.zustand()).isEqualTo(Zustand.LAEUFT);
    assertThat(sicht.dauer()).isEqualTo(Duration.ofMinutes(20));
    assertThat(sicht.meldung()).isNull();
  }

  @Test
  void einVerstummterLaufIstGescheitert_undDauertBisZuSeinemLetztenLebenszeichen() {
    MobilerLauf sicht = sicht(offen(vorMinuten(150), vorMinuten(91)));

    assertThat(sicht.zustand()).isEqualTo(Zustand.GESCHEITERT);
    assertThat(sicht.dauer()).isEqualTo(Duration.ofMinutes(59));
    assertThat(sicht.meldung())
        .isEqualTo(new LetzteMeldung(MeldungsArt.STILLE, null, null, null, vorMinuten(91)));
  }

  @Test
  void ohneLebenszeichenZaehltDerStart_fuerDauerUndStille() {
    MobilerLauf sicht = sicht(offen(vorMinuten(300), null));

    assertThat(sicht.dauer()).isEqualTo(Duration.ZERO);
    assertThat(sicht.meldung())
        .isEqualTo(new LetzteMeldung(MeldungsArt.STILLE, null, null, null, vorMinuten(300)));
  }

  @Test
  void einGemeldeterLaufIstBeendet_mitDerGemeldetenDauer() {
    MobilerLauf sicht = sicht(lauf(vorMinuten(300), vorMinuten(200), true, null, List.of(), null));

    assertThat(sicht.zustand()).isEqualTo(Zustand.BEENDET);
    assertThat(sicht.dauer()).isEqualTo(GEMELDET);
    assertThat(sicht.meldung()).isNull();
  }

  /** Von Hand beendet ist beendet — auch wenn die Stille längst abgelaufen ist. */
  @Test
  void einVonHandBeendeterLaufIstBeendet_ohneStille() {
    MobilerLauf sicht =
        sicht(lauf(vorMinuten(300), vorMinuten(290), false, null, List.of(), vorMinuten(10)));

    assertThat(sicht.zustand()).isEqualTo(Zustand.BEENDET);
    assertThat(sicht.dauer()).isEqualTo(GEMELDET);
    assertThat(sicht.meldung()).isNull();
  }

  @Test
  void dieLetzteMeldungIstZuerstDerAbbruchgrund() {
    MobilerLauf sicht =
        sicht(
            lauf(vorMinuten(60), vorMinuten(1), true, "Dirty-Guard", List.of(), null),
            paket(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT, "Auszug"));

    assertThat(sicht.zustand()).isEqualTo(Zustand.GESCHEITERT);
    assertThat(sicht.meldung())
        .isEqualTo(new LetzteMeldung(MeldungsArt.ABBRUCHGRUND, "Dirty-Guard", null, null, null));
  }

  @Test
  void ohneAbbruchgrundNenntSieDenAuszugDesPaketsMitDemGrund() {
    MobilerLauf sicht =
        sicht(
            offen(vorMinuten(60), vorMinuten(1)),
            paket(2, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION, "wartet auf Manne"));

    assertThat(sicht.zustand()).isEqualTo(Zustand.HAENGT);
    assertThat(sicht.meldung())
        .isEqualTo(new LetzteMeldung(MeldungsArt.PAKET, "wartet auf Manne", 2, null, null));
  }

  @Test
  void einPaketOhneAuszugFaelltAufDieStilleZurueck() {
    MobilerLauf sicht =
        sicht(
            lauf(
                vorMinuten(300),
                vorMinuten(200),
                false,
                null,
                List.of(new RunnerStufe(5, "umsetzung", vorMinuten(250), null, "SESSION", true)),
                null),
            paket(1, NightRunState.RED, NightRunErrorClass.CHECKS_RED, null));

    assertThat(sicht.meldung())
        .isEqualTo(new LetzteMeldung(MeldungsArt.STILLE, null, null, null, vorMinuten(200)));
  }

  @Test
  void einLeererAuszugFaelltAufDieZuletztBegonneneStufeZurueck() {
    Instant zuletzt = vorMinuten(4);
    MobilerLauf sicht =
        sicht(
            lauf(
                vorMinuten(60),
                vorMinuten(1),
                false,
                null,
                List.of(
                    new RunnerStufe(6, "pruefung", zuletzt, null, "CHECKS", false),
                    new RunnerStufe(5, "umsetzung", vorMinuten(30), null, "SESSION", true)),
                null),
            paket(1, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION, " "));

    assertThat(sicht.meldung())
        .isEqualTo(new LetzteMeldung(MeldungsArt.STUFE, null, 6, "pruefung", zuletzt));
  }

  @Test
  void einBeendeterLaufNenntKeineStilleSondernSeineStufe() {
    Instant stufe = vorMinuten(250);
    MobilerLauf sicht =
        sicht(
            lauf(
                vorMinuten(300),
                vorMinuten(200),
                true,
                null,
                List.of(new RunnerStufe(5, "umsetzung", stufe, null, "SESSION", true)),
                null));

    assertThat(sicht.meldung())
        .isEqualTo(new LetzteMeldung(MeldungsArt.STUFE, null, 5, "umsetzung", stufe));
  }
}
