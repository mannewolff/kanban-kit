package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Eintrag;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.Karte;

/**
 * Grenzfälle des Kettenstands an der Karte (Issue #1475): genau die Bedingungen, an denen die
 * Anzeige der Stufenleiste kippt. Die übrigen Fälle stehen in {@code FortschrittErmittlungTest}.
 */
class KettenAbleitungTest {

  private static final String UM = " um 2026-10-06T01:00:00Z";
  private static final String GRENZE_WARTET =
      "wartet: Übergang abdeckung→umsetzung im Projekt nicht freigegeben — weiter mit kit:night";
  private static final String GRENZE_WAITING =
      "waiting: transition coverage→implementation not enabled in project — continue";

  private static Karte anforderung(String... labels) {
    return new Karte(
        5000L, 500, "[Fachlich] Anforderung", 3L, null, List.of(labels), null, false, "");
  }

  private static Karte plan(String... labels) {
    return new Karte(5010L, 501, "[Plan] Plan", 3L, null, List.of(labels), 5000L, false, "");
  }

  private static String laufstand(String... zeilen) {
    return "## Laufstand\n\n" + String.join("\n", zeilen);
  }

  private static String fertig(String stufe) {
    return "zuletzt abgeschlossen: " + stufe + " fertig für #501" + UM;
  }

  private static String begonnen(String stufe) {
    return "zuletzt begonnen: " + stufe + " begonnen für #501" + UM;
  }

  private static StationStand station(KettenStand stand, ProgressStage stufe) {
    return stand.stationen().stream().filter(s -> s.station() == stufe).findFirst().orElseThrow();
  }

  // --- Zeile 138: der Weg beginnt bei einem Plan als Startkarte mit den Paketen ---------------

  @Test
  void anEinemPlanOhneEintraegeLaeuftDieErsteStationDesWegs() {
    KettenStand stand = KettenAbleitung.stand(plan("lauf:laeuft"), laufstand());

    assertThat(station(stand, ProgressStage.PLAN).zustand())
        .isEqualTo(StationsZustand.VOR_DEM_LAUF_ERBRACHT);
    assertThat(station(stand, ProgressStage.PAKETE).zustand()).isEqualTo(StationsZustand.LAEUFT);
  }

  // --- Zeile 166: „läuft (n Prüfer)“ nur an der Prüfstation -----------------------------------

  @Test
  void diePruefstationNenntDiePrueferzahl() {
    KettenStand stand =
        KettenAbleitung.stand(
            anforderung("lauf:laeuft"),
            laufstand("Prüfer: 2", "zuletzt abgeschlossen: plan fertig für #500" + UM));

    assertThat(station(stand, ProgressStage.REVIEW).text()).isEqualTo("läuft (2 Prüfer)");
  }

  @Test
  void eineAndereLaufendeStationNenntDiePrueferzahlNicht() {
    KettenStand stand =
        KettenAbleitung.stand(anforderung("lauf:laeuft"), laufstand("Prüfer: 2", fertig("review")));

    assertThat(station(stand, ProgressStage.PAKETE).zustand()).isEqualTo(StationsZustand.LAEUFT);
    assertThat(station(stand, ProgressStage.PAKETE).text()).isEqualTo("läuft");
  }

  // --- Zeile 173: Projektgrenze erst hinter der Grenzstation -----------------------------------

  @Test
  void hinterDerGrenzeWartetDieKetteAufDasProjekt() {
    KettenStand stand =
        KettenAbleitung.stand(
            anforderung("lauf:wartet"),
            laufstand("Ziel: umsetzung", "Grenze: abdeckung", GRENZE_WARTET, fertig("abdeckung")));

    assertThat(station(stand, ProgressStage.UMSETZUNG))
        .isEqualTo(
            new StationStand(
                ProgressStage.UMSETZUNG, StationsZustand.WARTET, "Projektgrenze", GRENZE_WARTET));
  }

  @Test
  void anDerGrenzstationSelbstWartetDieKetteAufDenMenschen() {
    KettenStand stand =
        KettenAbleitung.stand(
            anforderung("lauf:wartet"),
            laufstand(
                "Ziel: umsetzung", "Grenze: abdeckung", GRENZE_WARTET, begonnen("abdeckung")));

    assertThat(station(stand, ProgressStage.ABDECKUNG).zustand()).isEqualTo(StationsZustand.WARTET);
    assertThat(station(stand, ProgressStage.ABDECKUNG).text()).isEqualTo("wartet");
  }

  // --- Issue #1591: englische Lauf-Labels und Laufstand-Zeilen (Plan #1582 A3, A5, E1, E13) ----

  @ParameterizedTest
  @CsvSource({
    "lauf:laeuft, run:running",
    "lauf:wartet, run:waiting",
    "lauf:abgebrochen, run:aborted"
  })
  void einEnglischesLaufLabelWirktWieSeinDeutsches(String deutsch, String englisch) {
    String body = laufstand("Prüfer: 2", "Abbruch: Prüfer uneinig", fertig("plan"));

    assertThat(KettenAbleitung.stand(anforderung(englisch), body))
        .isEqualTo(KettenAbleitung.stand(anforderung(deutsch), body));
  }

  @Test
  void einEnglischerLaufstandWirdGelesenWieDerDeutsche() {
    KettenStand deutsch =
        KettenAbleitung.stand(
            anforderung("lauf:wartet"),
            laufstand("Ziel: umsetzung", "Prüfer: 1", "Grenze: abdeckung", GRENZE_WARTET));
    KettenStand englisch =
        KettenAbleitung.stand(
            anforderung("run:waiting"),
            "## Run status\n\n"
                + String.join(
                    "\n",
                    "Goal: implementation",
                    "Reviewers: 1",
                    "Boundary: coverage",
                    GRENZE_WAITING));

    assertThat(englisch.ziel()).isEqualTo(ProgressStage.UMSETZUNG);
    assertThat(englisch.pruefer()).isEqualTo(1);
    assertThat(englisch.projektgrenze())
        .isEqualTo(new KettenStand.Projektgrenze(ProgressStage.ABDECKUNG, GRENZE_WAITING));
    assertThat(deutsch.projektgrenze())
        .isEqualTo(new KettenStand.Projektgrenze(ProgressStage.ABDECKUNG, GRENZE_WARTET));
    assertThat(englisch.stationen()).hasSameSizeAs(deutsch.stationen());
  }

  @Test
  void dasEnglischeZielWirdAlsStationGelesen() {
    assertThat(KettenAbleitung.ziel("## Run status\nGoal: packages"))
        .contains(ProgressStage.PAKETE);
    assertThat(KettenAbleitung.ziel(laufstand("Ziel: pakete"))).contains(ProgressStage.PAKETE);
    assertThat(KettenAbleitung.ziel(laufstand("Goal: mond"))).isEmpty();
  }

  @Test
  void doneUpToMeldetDasZielAlsErreicht() {
    KettenStand deutsch =
        KettenAbleitung.stand(anforderung(), laufstand("Ziel: pakete", "fertig bis pakete"));
    KettenStand englisch =
        KettenAbleitung.stand(anforderung(), laufstand("Goal: packages", "done up to packages"));

    assertThat(englisch.zielErreicht()).isTrue();
    assertThat(englisch).isEqualTo(deutsch);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "Goal: plan",
        "Reviewers: 2",
        "Boundary: plan",
        "last started: plan started for #500 at 2026-10-06T01:00:00Z",
        "Next: review",
        "Log: .claude/protokolle/x.log"
      })
  void englischeDatenzeilenSindKeinGrund(String zeile) {
    KettenStand stand =
        KettenAbleitung.stand(
            anforderung("run:aborted"), "## Run status\n" + zeile + "\nAbbruch: Grund");

    assertThat(stand.stationen())
        .filteredOn(s -> s.zustand() == StationsZustand.ABGEBROCHEN)
        .extracting(StationStand::grund)
        .containsOnly("Abbruch: Grund");
  }

  @Test
  void einGemischtesBoardErgibtDasselbeWieEinDeutsches() {
    String gemischt =
        laufstand(
            "Goal: umsetzung", "Prüfer: 2", "zuletzt abgeschlossen: plan fertig für #500" + UM);
    String deutsch =
        laufstand(
            "Ziel: umsetzung", "Prüfer: 2", "zuletzt abgeschlossen: plan fertig für #500" + UM);

    assertThat(KettenAbleitung.stand(anforderung("run:running"), gemischt))
        .isEqualTo(KettenAbleitung.stand(anforderung("lauf:laeuft"), deutsch));
  }

  @Test
  void einZusatznameImRegisterWirktWieDerStandardname() {
    KitNamen register =
        KitNamen.aus(
            List.of(
                new Eintrag(Sache.LAUF_LAEUFT, "lauf:laeuft"),
                new Eintrag(Sache.LAUF_LAEUFT, "run:in-progress")));
    String body = laufstand(fertig("plan"));

    assertThat(KettenAbleitung.stand(anforderung("run:in-progress"), body, register))
        .isEqualTo(KettenAbleitung.stand(anforderung("lauf:laeuft"), body));
    assertThat(
            station(
                    KettenAbleitung.stand(anforderung("run:in-progress"), body),
                    ProgressStage.REVIEW)
                .zustand())
        .isEqualTo(StationsZustand.STEHT_AUS);
  }
}
