package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Eintrag;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.nightrun.domain.NachtFreigabe.Startstation;

/**
 * Der kleine Stufenstand einer freigegebenen Karte für die Übersicht „Heute Nacht“ (Issue #1454,
 * Plan #1447 E4, E10): Startstation aus dem Titel, Ziel aus {@code ziel:*} mit den Vorgaben, die
 * auch die Stufenleiste der Karte anlegt, und die Prüferzahl aus {@code planreview:*}.
 */
class NachtFreigabeTest {

  private static final String ANFORDERUNG = "[Fachlich] Export";
  private static final String PLAN = "[Plan] Export";

  private static NachtFreigabe freigabe(String titel, String... labels) {
    return NachtFreigabe.aus(12, titel, "Entwicklung", List.of(labels));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[Fachlich] Export",
        "  [fachlich] Export",
        "[FACHLICH]Export",
        "[Requirement] Export",
        " [requirement] Export"
      })
  void eineFachlicheAnforderungStartetBeimFachplan(String titel) {
    assertThat(NachtFreigabe.istStartkarte(titel)).isTrue();
    assertThat(freigabe(titel).start()).isEqualTo(Startstation.FACHPLAN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"[Plan] Export", " [plan] Export"})
  void einPlanStartetBeimPlan(String titel) {
    assertThat(NachtFreigabe.istStartkarte(titel)).isTrue();
    assertThat(freigabe(titel).start()).isEqualTo(Startstation.PLAN);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Export",
        "[Task] Export",
        "Export [Fachlich]",
        "[Idee] Plan",
        "[Idea] Plan",
        "[Human] Plan"
      })
  void eineKarteOhnePraefixIstKeineStartkarte(String titel) {
    assertThat(NachtFreigabe.istStartkarte(titel)).isFalse();
  }

  @Test
  void nummerTitelUndBoardWerdenUebernommen() {
    NachtFreigabe f = freigabe(ANFORDERUNG, "kit:night");

    assertThat(f.number()).isEqualTo(12);
    assertThat(f.title()).isEqualTo(ANFORDERUNG);
    assertThat(f.boardName()).isEqualTo("Entwicklung");
  }

  @Test
  void ohneZielLabelIstDasZielArbeitspakete() {
    assertThat(freigabe(ANFORDERUNG, "kit:night").ziel()).isEqualTo(ProgressStage.PAKETE);
  }

  @Test
  void mitDurchziehenUndOhneZielLabelIstDasZielUmsetzung() {
    assertThat(freigabe(ANFORDERUNG, "kit:durchziehen", "kit:night").ziel())
        .isEqualTo(ProgressStage.UMSETZUNG);
  }

  @Test
  void jedesZielLabelGiltAlsZiel() {
    assertThat(freigabe(ANFORDERUNG, "ziel:plan").ziel()).isEqualTo(ProgressStage.PLAN);
    assertThat(freigabe(ANFORDERUNG, "ziel:pakete").ziel()).isEqualTo(ProgressStage.PAKETE);
    assertThat(freigabe(ANFORDERUNG, "ziel:umsetzung").ziel()).isEqualTo(ProgressStage.UMSETZUNG);
    assertThat(freigabe(ANFORDERUNG, "ziel:push-vorbereitet").ziel())
        .isEqualTo(ProgressStage.VORBEREITUNG);
  }

  @Test
  void einUnbekanntesZielLabelZaehltNicht() {
    assertThat(freigabe(ANFORDERUNG, "ziel:mond").ziel()).isEqualTo(ProgressStage.PAKETE);
  }

  @Test
  void anEinemPlanZaehltZielPlanNicht() {
    assertThat(freigabe(PLAN, "ziel:plan").ziel()).isEqualTo(ProgressStage.PAKETE);
    assertThat(freigabe(PLAN, "ziel:umsetzung").ziel()).isEqualTo(ProgressStage.UMSETZUNG);
  }

  @Test
  void mitDurchziehenGiltMindestensUmsetzung() {
    assertThat(freigabe(ANFORDERUNG, "kit:durchziehen", "ziel:plan").ziel())
        .isEqualTo(ProgressStage.UMSETZUNG);
    assertThat(freigabe(ANFORDERUNG, "kit:durchziehen", "ziel:pakete").ziel())
        .isEqualTo(ProgressStage.UMSETZUNG);
    // Issue #1475: Das Ziel an der Grenze selbst bleibt stehen.
    assertThat(freigabe(ANFORDERUNG, "kit:durchziehen", "ziel:umsetzung").ziel())
        .isEqualTo(ProgressStage.UMSETZUNG);
    assertThat(freigabe(ANFORDERUNG, "kit:durchziehen", "ziel:push-vorbereitet").ziel())
        .isEqualTo(ProgressStage.VORBEREITUNG);
  }

  @Test
  void diePrueferzahlKommtAusPlanreview() {
    assertThat(freigabe(ANFORDERUNG, "planreview:1").pruefer()).isEqualTo(1);
    assertThat(freigabe(ANFORDERUNG, "planreview:2").pruefer()).isEqualTo(2);
  }

  @Test
  void ohneOderMitUnbekanntemPlanreviewFehltDiePrueferzahl() {
    assertThat(freigabe(ANFORDERUNG).pruefer()).isNull();
    assertThat(freigabe(ANFORDERUNG, "planreview:3").pruefer()).isNull();
  }

  @Test
  void anEinemPlanFehltDiePrueferzahl() {
    assertThat(freigabe(PLAN, "planreview:2").pruefer()).isNull();
  }

  // --- Issue #1591: Ziel, Prüferzahl und Durchziehen unter allen Namen der Sache (A3, E13) -----

  @ParameterizedTest
  @CsvSource({
    "ziel:plan, goal:plan",
    "ziel:pakete, goal:packages",
    "ziel:umsetzung, goal:implementation",
    "ziel:push-vorbereitet, goal:push-prepared"
  })
  void einEnglischesZielLabelErgibtDasselbeZiel(String deutsch, String englisch) {
    assertThat(freigabe(ANFORDERUNG, englisch).ziel())
        .isEqualTo(freigabe(ANFORDERUNG, deutsch).ziel());
    assertThat(freigabe(PLAN, englisch).ziel()).isEqualTo(freigabe(PLAN, deutsch).ziel());
  }

  @Test
  void dasErsteZielNachStationGiltAuchAufEinemGemischtenBoard() {
    assertThat(freigabe(ANFORDERUNG, "goal:implementation", "ziel:pakete").ziel())
        .isEqualTo(ProgressStage.PAKETE);
    assertThat(freigabe(ANFORDERUNG, "ziel:umsetzung", "goal:packages").ziel())
        .isEqualTo(ProgressStage.PAKETE);
  }

  @Test
  void carryThroughGiltWieDurchziehen() {
    assertThat(freigabe(ANFORDERUNG, "kit:carry-through").ziel())
        .isEqualTo(ProgressStage.UMSETZUNG);
    assertThat(freigabe(ANFORDERUNG, "kit:carry-through", "goal:packages").ziel())
        .isEqualTo(ProgressStage.UMSETZUNG);
    assertThat(freigabe(ANFORDERUNG, "kit:carry-through", "goal:push-prepared").ziel())
        .isEqualTo(ProgressStage.VORBEREITUNG);
  }

  @Test
  void planreviewBleibtDiePrueferzahlNebenEnglischenLabels() {
    assertThat(freigabe(ANFORDERUNG, "goal:packages", "planreview:2").pruefer()).isEqualTo(2);
  }

  @Test
  void einZusatznameImRegisterWirktWieDerStandardname() {
    KitNamen register =
        KitNamen.aus(
            List.of(
                new Eintrag(Sache.ZIEL_UMSETZUNG, "goal:build"),
                new Eintrag(Sache.PLANREVIEW_2, "planreview:zwei"),
                new Eintrag(Sache.DURCHZIEHEN, "kit:through")));

    NachtFreigabe mitZiel =
        NachtFreigabe.aus(12, ANFORDERUNG, "Entwicklung", List.of("goal:build"), register);
    NachtFreigabe mitPruefern =
        NachtFreigabe.aus(12, ANFORDERUNG, "Entwicklung", List.of("planreview:zwei"), register);
    NachtFreigabe mitDurchziehen =
        NachtFreigabe.aus(12, ANFORDERUNG, "Entwicklung", List.of("kit:through"), register);

    assertThat(mitZiel.ziel()).isEqualTo(ProgressStage.UMSETZUNG);
    assertThat(mitPruefern.pruefer()).isEqualTo(2);
    assertThat(mitDurchziehen.ziel()).isEqualTo(ProgressStage.UMSETZUNG);
    assertThat(freigabe(ANFORDERUNG, "goal:build").ziel()).isEqualTo(ProgressStage.PAKETE);
  }
}
