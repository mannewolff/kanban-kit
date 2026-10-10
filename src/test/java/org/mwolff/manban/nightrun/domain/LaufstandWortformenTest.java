package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.domain.LaufstandWortformen.Stufenzeile;

/**
 * Die Lesetabelle des Laufstands in deutscher und englischer Form (Issue #1590, Plan #1582 A5, A6,
 * E1, E12): jede Zeile der Tabelle in beiden Formen.
 */
class LaufstandWortformenTest {

  private static final String ZEIT = "2026-10-03T13:11:00Z";

  // --- Stufenwort
  // ---------------------------------------------------------------------------------

  @Test
  void dieDeutschenStufenwoerterFuehrenZuIhrerStufe() {
    assertThat(LaufstandWortformen.stufe("plan")).contains(ProgressStage.PLAN);
    assertThat(LaufstandWortformen.stufe("review")).contains(ProgressStage.REVIEW);
    assertThat(LaufstandWortformen.stufe("pakete")).contains(ProgressStage.PAKETE);
    assertThat(LaufstandWortformen.stufe("abdeckung")).contains(ProgressStage.ABDECKUNG);
    assertThat(LaufstandWortformen.stufe("umsetzung")).contains(ProgressStage.UMSETZUNG);
    assertThat(LaufstandWortformen.stufe("vorbereitung")).contains(ProgressStage.VORBEREITUNG);
  }

  @Test
  void dieEnglischenStufenwoerterFuehrenZuDerselbenStufe() {
    assertThat(LaufstandWortformen.stufe("packages")).contains(ProgressStage.PAKETE);
    assertThat(LaufstandWortformen.stufe("coverage")).contains(ProgressStage.ABDECKUNG);
    assertThat(LaufstandWortformen.stufe("implementation")).contains(ProgressStage.UMSETZUNG);
    assertThat(LaufstandWortformen.stufe("preparation")).contains(ProgressStage.VORBEREITUNG);
  }

  @Test
  void einStufenwortGehtUeberDieTabelleNichtUeberDenKonstantennamen() {
    assertThat(LaufstandWortformen.stufe("PAKETE")).isEmpty();
    assertThat(LaufstandWortformen.stufe("Plan")).isEmpty();
    assertThat(LaufstandWortformen.stufe("entwurf")).isEmpty();
  }

  // --- Stufeneintrag: begonnen|fertig, für #N um T
  // ------------------------------------------------

  @Test
  void einDeutscherStufeneintragWirdGelesen() {
    assertThat(
            LaufstandWortformen.stufenzeile(
                "zuletzt begonnen: pakete begonnen für #501 um " + ZEIT))
        .contains(new Stufenzeile(ProgressStage.PAKETE, false, 501, ZEIT));
    assertThat(LaufstandWortformen.stufenzeile("abdeckung fertig für #7 um " + ZEIT))
        .contains(new Stufenzeile(ProgressStage.ABDECKUNG, true, 7, ZEIT));
  }

  @Test
  void einEnglischerStufeneintragErgibtDasselbeWieSeinDeutschesGegenstueck() {
    assertThat(
            LaufstandWortformen.stufenzeile("last started: packages started for #501 at " + ZEIT))
        .contains(new Stufenzeile(ProgressStage.PAKETE, false, 501, ZEIT));
    assertThat(LaufstandWortformen.stufenzeile("coverage finished for #7 at " + ZEIT))
        .contains(new Stufenzeile(ProgressStage.ABDECKUNG, true, 7, ZEIT));
    assertThat(LaufstandWortformen.stufenzeile("plan started for #500 at " + ZEIT))
        .contains(new Stufenzeile(ProgressStage.PLAN, false, 500, ZEIT));
  }

  @Test
  void jedesEnglischeStufenwortStehtImStufeneintrag() {
    for (String wort : List.of("review", "implementation", "preparation")) {
      assertThat(LaufstandWortformen.stufenzeile(wort + " finished for #1 at " + ZEIT))
          .map(Stufenzeile::stufe)
          .isEqualTo(LaufstandWortformen.stufe(wort));
    }
  }

  @Test
  void dieSprachenMischenSichInEinemEintragNicht() {
    assertThat(LaufstandWortformen.stufenzeile("plan started für #500 um " + ZEIT)).isEmpty();
    assertThat(LaufstandWortformen.stufenzeile("plan begonnen for #500 at " + ZEIT)).isEmpty();
    assertThat(LaufstandWortformen.stufenzeile("packages begonnen für #500 um " + ZEIT)).isEmpty();
    assertThat(LaufstandWortformen.stufenzeile("pakete started for #500 at " + ZEIT)).isEmpty();
  }

  @Test
  void einStufenwortMussAlsWortBeginnen() {
    assertThat(LaufstandWortformen.stufenzeile("preplan started for #500 at " + ZEIT)).isEmpty();
    assertThat(LaufstandWortformen.stufenzeile("vorplan begonnen für #500 um " + ZEIT)).isEmpty();
  }

  @Test
  void einZerlegterUmlautInFuerWirdErkannt() {
    assertThat(LaufstandWortformen.stufenzeile("plan fertig für #500 um " + ZEIT))
        .contains(new Stufenzeile(ProgressStage.PLAN, true, 500, ZEIT));
  }

  @Test
  void einEintragMitLesbaremZeitstempelWirdZumStufeneintrag() {
    assertThat(new Stufenzeile(ProgressStage.REVIEW, true, 501, ZEIT).eintrag())
        .contains(
            new FortschrittErmittlung.StufenEintrag(
                ProgressStage.REVIEW, true, 501, Instant.parse(ZEIT)));
    assertThat(new Stufenzeile(ProgressStage.REVIEW, true, 501, "gestern").eintrag()).isEmpty();
  }

  // --- Zeilenanfänge -----------------------------------------------------------------------------

  @Test
  void anker() {
    assertThat(LaufstandWortformen.ANKER).containsExactly("## Laufstand", "## Run status");
  }

  @Test
  void zielZeileUndIhreWerte() {
    assertThat(LaufstandWortformen.ZIEL).containsExactly("Ziel:", "Goal:");
    assertThat(LaufstandWortformen.zielStation("plan")).contains(ProgressStage.PLAN);
    assertThat(LaufstandWortformen.zielStation("pakete")).contains(ProgressStage.PAKETE);
    assertThat(LaufstandWortformen.zielStation("packages")).contains(ProgressStage.PAKETE);
    assertThat(LaufstandWortformen.zielStation("umsetzung")).contains(ProgressStage.UMSETZUNG);
    assertThat(LaufstandWortformen.zielStation("implementation")).contains(ProgressStage.UMSETZUNG);
    assertThat(LaufstandWortformen.zielStation("push-vorbereitet"))
        .contains(ProgressStage.VORBEREITUNG);
    assertThat(LaufstandWortformen.zielStation("push-prepared"))
        .contains(ProgressStage.VORBEREITUNG);
    assertThat(LaufstandWortformen.zielStation("abdeckung")).isEmpty();
  }

  @Test
  void prueferGrenzeUndFertigBis() {
    assertThat(LaufstandWortformen.PRUEFER).containsExactly("Prüfer:", "Reviewers:");
    assertThat(LaufstandWortformen.GRENZE).containsExactly("Grenze:", "Boundary:");
    assertThat(LaufstandWortformen.FERTIG_BIS).containsExactly("fertig bis ", "done up to ");
  }

  @Test
  void zuletztAlsNaechstesProtokollUndPlanReview() {
    assertThat(LaufstandWortformen.ZULETZT).containsExactly("zuletzt ", "last ");
    assertThat(LaufstandWortformen.ALS_NAECHSTES).containsExactly("Als Nächstes:", "Next:");
    assertThat(LaufstandWortformen.PROTOKOLL).containsExactly("Protokoll:", "Log:");
    assertThat(LaufstandWortformen.PLAN_REVIEW).containsExactly("Plan-Review:", "Plan review:");
  }

  @Test
  void derWartetextDerProjektgrenzeInBeidenFormen() {
    List<Pattern> formen = LaufstandWortformen.GRENZ_WARTETEXT;

    assertThat(formen).hasSize(2);
    assertThat(
            formen
                .get(0)
                .matcher("wartet: Übergang abdeckung→umsetzung im Projekt nicht freigegeben")
                .find())
        .isTrue();
    assertThat(
            formen
                .get(1)
                .matcher("waiting: transition coverage→implementation not enabled in project")
                .find())
        .isTrue();
    assertThat(formen.get(1).matcher("waiting: transition not enabled in project").find())
        .isFalse();
  }

  // --- Wert nach einem Zeilenanfang
  // ---------------------------------------------------------------

  @Test
  void derWertNachEinemZeilenanfangInBeidenFormen() {
    assertThat(LaufstandWortformen.wert(LaufstandWortformen.PLAN_REVIEW, "Plan-Review: fable"))
        .contains(" fable");
    assertThat(LaufstandWortformen.wert(LaufstandWortformen.PLAN_REVIEW, "Plan review:fable"))
        .contains("fable");
    assertThat(LaufstandWortformen.wert(LaufstandWortformen.PLAN_REVIEW, "Kein Plan-Review: x"))
        .isEmpty();
  }
}
