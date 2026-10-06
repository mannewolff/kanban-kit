package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.Karte;

/**
 * Grenzfälle des Kettenstands an der Karte (Issue #1475): genau die Bedingungen, an denen die
 * Anzeige der Stufenleiste kippt. Die übrigen Fälle stehen in {@code FortschrittErmittlungTest}.
 */
class KettenAbleitungTest {

  private static final String UM = " um 2026-10-06T01:00:00Z";
  private static final String GRENZE_WARTET =
      "wartet: Übergang abdeckung→umsetzung im Projekt nicht freigegeben — weiter mit kit:night";

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
}
