package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  @ValueSource(strings = {"[Fachlich] Export", "  [fachlich] Export", "[FACHLICH]Export"})
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
  @ValueSource(strings = {"Export", "[Task] Export", "Export [Fachlich]", "[Idee] Plan"})
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
}
