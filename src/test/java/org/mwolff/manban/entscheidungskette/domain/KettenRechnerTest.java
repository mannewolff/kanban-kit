package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.FACHPLAN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.PAKET;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.PLAN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.PLAN_TECHNISCH;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.TASK;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.TOKEN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.abschnitt;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.anderesProjekt;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.archiviert;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.erstes;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.geloescht;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.karte;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.papierkorb;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.um;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.vorhanden;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.wegGeaendert;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Art;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;

/** Die Rechnung der Dokument-Glieder (Issue #2115, Plan #2108 E12, E13, E14, E17). */
class KettenRechnerTest {

  // --- Abschnitte je Kartenart -----------------------------------------------------------------

  @Test
  void paket_abschnitteVonDerIdeeBisZumPaket() {
    Entscheidungskette kette =
        KettenAufbau.an(PAKET).vorfahr(vorhanden(PLAN)).vorfahr(vorhanden(FACHPLAN)).rechne();

    assertThat(kette.art()).isEqualTo(Dokumentart.PAKET);
    assertThat(kette.abschnitte())
        .extracting(Abschnitt::art)
        .containsExactly(
            Dokumentart.IDEE, Dokumentart.FACHPLAN, Dokumentart.PLAN, Dokumentart.PAKET);
  }

  @Test
  void plan_abschnitteBisZumPlan() {
    Entscheidungskette kette = KettenAufbau.an(PLAN).vorfahr(vorhanden(FACHPLAN)).rechne();

    assertThat(kette.art()).isEqualTo(Dokumentart.PLAN);
    assertThat(kette.abschnitte())
        .extracting(Abschnitt::art)
        .containsExactly(Dokumentart.IDEE, Dokumentart.FACHPLAN, Dokumentart.PLAN);
  }

  @Test
  void fachplan_abschnitteIdeeUndFachplan() {
    Entscheidungskette kette = KettenAufbau.an(FACHPLAN).rechne();

    assertThat(kette.art()).isEqualTo(Dokumentart.FACHPLAN);
    assertThat(kette.abschnitte())
        .extracting(Abschnitt::art)
        .containsExactly(Dokumentart.IDEE, Dokumentart.FACHPLAN);
  }

  @Test
  void vorfahren_ideeUndVorhabenFuellenKeinGlied() {
    Karte idee = karte(4, 5, Art.IDEE, "[Idea] x", null);
    Karte vorhaben = karte(5, 6, Art.VORHABEN, "Vorhaben", null);

    Entscheidungskette kette =
        KettenAufbau.an(PLAN_TECHNISCH)
            .vorfahr(vorhanden(idee))
            .vorfahr(vorhanden(vorhaben))
            .rechne();

    assertThat(erstes(kette, Dokumentart.FACHPLAN).zustand()).isEqualTo(Zustand.NICHT_VORGESEHEN);
  }

  // --- Idee ------------------------------------------------------------------------------------

  @Test
  void idee_mitErgebnis_erfasstMitWortlautUndVerweis() {
    Karte idee = karte(4, 5, Art.IDEE, "[Idea] Kette", "## Geklärter Stand\nErgebnis: Issue #10\n");

    Entscheidungskette kette =
        KettenAufbau.an(FACHPLAN).idee(idee).angelegt(4, null, 77L, um(1)).rechne();

    Glied glied = erstes(kette, Dokumentart.IDEE);
    assertThat(glied.art()).isEqualTo(Gliedart.IDEE);
    assertThat(glied.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(glied.ergebnis()).isEqualTo("Issue #10");
    assertThat(glied.quelle()).isEqualTo(new Verweis(4L, 5, "[Idea] Kette", true, null));
    assertThat(glied.ausgefuehrtVon()).isEqualTo(Akteur.mensch(77L));
    assertThat(glied.zeitpunkt()).isEqualTo(um(1));
  }

  @Test
  void idee_ohneErgebnis_nichtErfasstNieNichtVorgesehen() {
    Karte idee = karte(4, 5, Art.IDEE, "[Idea] Kette", "## Geklärter Stand\nBahn: 2\n");

    Entscheidungskette kette = KettenAufbau.an(TASK).idee(idee).rechne();

    Glied glied = erstes(kette, Dokumentart.IDEE);
    assertThat(glied.zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(glied.quelle()).isEqualTo(Verweis.KEINER);
    assertThat(glied.ergebnis()).isNull();
  }

  @Test
  void idee_ergebnisOhneNummerDerKette_nichtErfasst() {
    Karte fremd = karte(4, 5, Art.IDEE, "[Idea] x", "## Geklärter Stand\nErgebnis: Issue #99\n");
    Karte ohneNummer =
        karte(6, 7, Art.IDEE, "[Idea] y", "## Geklärter Stand\nErgebnis: Commit abc\n");

    Entscheidungskette kette = KettenAufbau.an(FACHPLAN).idee(fremd).idee(ohneNummer).rechne();

    assertThat(erstes(kette, Dokumentart.IDEE).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
  }

  @Test
  void idee_aufDenFachplanVorDerAufDasPaket() {
    Karte aufPaket = karte(4, 5, Art.IDEE, "[Idea] Paket", "## Geklärter Stand\nErgebnis: #30\n");
    Karte aufFachplan =
        karte(6, 7, Art.IDEE, "[Idea] Fachplan", "## Clarified state\nResult: Issue #10\n");

    Entscheidungskette kette =
        KettenAufbau.an(PAKET)
            .vorfahr(vorhanden(PLAN))
            .vorfahr(vorhanden(FACHPLAN))
            .idee(aufPaket)
            .idee(aufFachplan)
            .rechne();

    assertThat(erstes(kette, Dokumentart.IDEE).quelle().nummer()).isEqualTo(7);
  }

  @Test
  void idee_gleichesZiel_kleinereIdGewinnt() {
    Karte spaeter = karte(9, 8, Art.IDEE, "[Idea] b", "## Geklärter Stand\nErgebnis: #20\n");
    Karte frueher = karte(4, 5, Art.IDEE, "[Idea] a", "## Geklärter Stand\nErgebnis: #20\n");

    Entscheidungskette kette = KettenAufbau.an(PLAN_TECHNISCH).idee(spaeter).idee(frueher).rechne();

    assertThat(erstes(kette, Dokumentart.IDEE).quelle().nummer()).isEqualTo(5);
  }

  // --- „nicht vorgesehen“ (Kriterium 6, E13, E14) ---------------------------------------------

  @Test
  void task_planNichtVorgesehen_fachplanNichtErfasst() {
    Entscheidungskette kette = KettenAufbau.an(TASK).rechne();

    assertThat(erstes(kette, Dokumentart.PLAN).zustand()).isEqualTo(Zustand.NICHT_VORGESEHEN);
    assertThat(erstes(kette, Dokumentart.FACHPLAN).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
  }

  @Test
  void task_mitWegBelegMarke_planNichtErfasst() {
    Entscheidungskette kette = KettenAufbau.an(wegGeaendert(TASK)).rechne();

    assertThat(erstes(kette, Dokumentart.PLAN).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
  }

  @Test
  void task_mitFachplan_zeigtIhn() {
    Entscheidungskette kette = KettenAufbau.an(TASK).vorfahr(vorhanden(FACHPLAN)).rechne();

    assertThat(erstes(kette, Dokumentart.FACHPLAN).zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(erstes(kette, Dokumentart.PLAN).zustand()).isEqualTo(Zustand.NICHT_VORGESEHEN);
  }

  @Test
  void paket_planMitPlanModellOhneFachlicheQuelle_fachplanNichtVorgesehen() {
    Karte paket = karte(3, 30, Art.ARBEITSPAKET, "Paket", "Plan: Issue #20\n");

    Entscheidungskette kette = KettenAufbau.an(paket).vorfahr(vorhanden(PLAN_TECHNISCH)).rechne();

    assertThat(erstes(kette, Dokumentart.FACHPLAN).zustand()).isEqualTo(Zustand.NICHT_VORGESEHEN);
    assertThat(erstes(kette, Dokumentart.PLAN).zustand()).isEqualTo(Zustand.ERFASST);
  }

  @Test
  void task_mitPlanAlsVorfahr_planBleibtNichtVorgesehen() {
    Entscheidungskette kette = KettenAufbau.an(TASK).vorfahr(vorhanden(PLAN)).rechne();

    assertThat(erstes(kette, Dokumentart.PLAN).zustand()).isEqualTo(Zustand.NICHT_VORGESEHEN);
  }

  @Test
  void paket_planOhneText_fachplanNichtErfasst() {
    Karte plan = karte(2, 20, Art.PLAN, "[Plan] leer", null);

    Entscheidungskette kette = KettenAufbau.an(PAKET).vorfahr(vorhanden(plan)).rechne();

    Glied fachplan = erstes(kette, Dokumentart.FACHPLAN);
    assertThat(fachplan.zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(fachplan.quelle()).isEqualTo(Verweis.nurNummer(10));
  }

  @Test
  void paket_planMitWegBelegMarke_fachplanNichtErfasst() {
    Entscheidungskette kette =
        KettenAufbau.an(PAKET).vorfahr(vorhanden(wegGeaendert(PLAN_TECHNISCH))).rechne();

    assertThat(erstes(kette, Dokumentart.FACHPLAN).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
  }

  @Test
  void plan_ohnePlanModell_fachplanNichtErfasst() {
    Karte plan = karte(2, 20, Art.PLAN, "[Plan] Alt", "Kein Kopf\n");

    Entscheidungskette kette = KettenAufbau.an(plan).rechne();

    assertThat(erstes(kette, Dokumentart.FACHPLAN).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
  }

  @Test
  void plan_englischOhneRequirement_fachplanNichtVorgesehen() {
    Karte plan = karte(2, 20, Art.PLAN, "[Plan] Tech", "Plan-Model: gpt\n");

    Entscheidungskette kette = KettenAufbau.an(plan).rechne();

    assertThat(erstes(kette, Dokumentart.FACHPLAN).zustand()).isEqualTo(Zustand.NICHT_VORGESEHEN);
  }

  @Test
  void plan_mitRequirementZeileOhneFachplan_nichtErfasstMitNummer() {
    Karte plan = karte(2, 20, Art.PLAN, "[Plan] x", "Plan-Model: gpt\nRequirement: #10\n");

    Entscheidungskette kette = KettenAufbau.an(plan).rechne();

    Glied fachplan = erstes(kette, Dokumentart.FACHPLAN);
    assertThat(fachplan.zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(fachplan.quelle()).isEqualTo(Verweis.nurNummer(10));
  }

  @Test
  void paket_ohnePlan_planNichtErfasstMitNummerAusDerZeile() {
    Entscheidungskette kette = KettenAufbau.an(PAKET).rechne();

    Glied plan = erstes(kette, Dokumentart.PLAN);
    assertThat(plan.zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(plan.quelle()).isEqualTo(Verweis.nurNummer(20));
    // Ohne Plan ist der Weg nicht belegt.
    assertThat(erstes(kette, Dokumentart.FACHPLAN).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
  }

  // --- Archiv, Papierkorb, gelöscht, anderes Projekt (E12) -------------------------------------

  @Test
  void vorfahr_archiviert_mitGliedernVerweisOhneOeffnen() {
    Entscheidungskette kette =
        KettenAufbau.an(PAKET)
            .vorfahr(vorhanden(PLAN))
            .vorfahr(vorhanden(archiviert(FACHPLAN)))
            .angelegt(1, TOKEN, null, um(1))
            .kommentar(11, 1, "## Fachplan-Review, Runde 1\n", um(2))
            .rechne();

    Abschnitt fachplan = abschnitt(kette, Dokumentart.FACHPLAN);
    Glied herkunft = fachplan.glieder().getFirst();
    assertThat(herkunft.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(herkunft.ausgefuehrtVon()).isEqualTo(Akteur.modell("claude-opus-5-5"));
    assertThat(herkunft.zeitpunkt()).isEqualTo(um(1));
    assertThat(herkunft.quelle())
        .isEqualTo(new Verweis(1L, 10, "[Requirement] Kette", false, null));
    assertThat(fachplan.glieder()).hasSize(2);
    assertThat(fachplan.glieder().get(1).quelle().oeffenbar()).isFalse();
  }

  @Test
  void vorfahr_planGeloescht_nichtErfasstUebrigeGliederStehen() {
    Entscheidungskette kette =
        KettenAufbau.an(PAKET).vorfahr(geloescht(20)).vorfahr(vorhanden(FACHPLAN)).rechne();

    Glied plan = erstes(kette, Dokumentart.PLAN);
    assertThat(plan.zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(plan.quelle()).isEqualTo(Verweis.nurNummer(20));
    assertThat(erstes(kette, Dokumentart.FACHPLAN).zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(erstes(kette, Dokumentart.PAKET).zustand()).isEqualTo(Zustand.ERFASST);
  }

  @Test
  void vorfahr_fachplanImPapierkorb_nichtErfasstNurMitNummer() {
    Entscheidungskette kette =
        KettenAufbau.an(PAKET).vorfahr(vorhanden(PLAN)).vorfahr(papierkorb(10)).rechne();

    Glied fachplan = erstes(kette, Dokumentart.FACHPLAN);
    assertThat(fachplan.zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(fachplan.quelle()).isEqualTo(Verweis.nurNummer(10));
    assertThat(fachplan.hinweise()).isEmpty();
  }

  @Test
  void vorfahr_anderesProjekt_nichtErfasstMitVermerkOhneTitel() {
    Karte paket = karte(3, 30, Art.ARBEITSPAKET, "Paket", null);

    Entscheidungskette kette = KettenAufbau.an(paket).vorfahr(anderesProjekt()).rechne();

    Glied plan = erstes(kette, Dokumentart.PLAN);
    assertThat(plan.zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(plan.hinweise()).containsExactly(Hinweis.ANDERES_PROJEKT);
    assertThat(plan.quelle()).isEqualTo(Verweis.KEINER);
    assertThat(erstes(kette, Dokumentart.FACHPLAN).hinweise()).isEmpty();
  }

  @Test
  void vorfahr_ohneZeilennummer_folgtDerStufe() {
    Karte paket = karte(3, 30, Art.ARBEITSPAKET, "Paket", null);

    Entscheidungskette kette =
        KettenAufbau.an(paket).vorfahr(vorhanden(PLAN)).vorfahr(geloescht(10)).rechne();

    assertThat(erstes(kette, Dokumentart.FACHPLAN).quelle()).isEqualTo(Verweis.nurNummer(10));
  }

  @Test
  void vorfahr_ueberzaehlig_wirdNichtGezeigt() {
    Karte paket = karte(3, 30, Art.ARBEITSPAKET, "Paket", null);

    Entscheidungskette kette =
        KettenAufbau.an(paket)
            .vorfahr(vorhanden(PLAN))
            .vorfahr(vorhanden(FACHPLAN))
            .vorfahr(geloescht(99))
            .vorfahr(vorhanden(karte(8, 80, Art.PLAN, "[Plan] zweiter", null)))
            .rechne();

    assertThat(erstes(kette, Dokumentart.PLAN).quelle().nummer()).isEqualTo(20);
    assertThat(erstes(kette, Dokumentart.FACHPLAN).quelle().nummer()).isEqualTo(10);
  }

  @Test
  void vorfahr_nummerAusDerQuellzeile_fuelltDenFachplan() {
    Entscheidungskette kette =
        KettenAufbau.an(PAKET).vorfahr(geloescht(10)).vorfahr(vorhanden(PLAN)).rechne();

    assertThat(erstes(kette, Dokumentart.FACHPLAN).quelle()).isEqualTo(Verweis.nurNummer(10));
    assertThat(erstes(kette, Dokumentart.PLAN).zustand()).isEqualTo(Zustand.ERFASST);
  }

  // --- Stabilität (E17) ------------------------------------------------------------------------

  @Test
  void zweimalGerechnet_dieselbeAusgabe() {
    KettenAufbau aufbau =
        KettenAufbau.an(PAKET)
            .vorfahr(vorhanden(PLAN))
            .vorfahr(vorhanden(FACHPLAN))
            .idee(karte(4, 5, Art.IDEE, "[Idea] x", "## Geklärter Stand\nErgebnis: #10\n"))
            .angelegt(2, TOKEN, null, um(1))
            .kommentar(21, 2, "## Plan-Review, Runde 1\n", um(5))
            .kommentar(20, 2, "## Plan-Review, Runde 2\n", um(5));
    KettenEingabe eingabe = aufbau.eingabe();

    assertThat(KettenRechner.rechne(eingabe)).isEqualTo(KettenRechner.rechne(eingabe));
  }
}
