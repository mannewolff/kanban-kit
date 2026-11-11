package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.FACHPLAN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.LAUF;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.PAKET;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.PLAN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.SESSION;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.TASK;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.TOKEN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.abschnitt;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.inSpalte;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.karte;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.um;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.vorhanden;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.BERICHT;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.CODE_REVIEW;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.P;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.einziges;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.label;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.paket;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.uebertragung;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.veroeffentlichung;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.vonHand;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Art;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;

/** Abschnitt des Pakets und zuletzt erreichtes Glied (Issue #2116, Plan #2108 E15). */
class PaketgliederTest {

  @Test
  void paket_gliederInDerFolgeDesWegs_ohneAngabenNichtErfasst() {
    Entscheidungskette kette = paket().rechne();

    List<Glied> alle = abschnitt(kette, Dokumentart.PAKET).glieder();
    assertThat(alle)
        .extracting(Glied::art)
        .containsExactly(
            Gliedart.HERKUNFT,
            Gliedart.ISSUE_REVIEW,
            Gliedart.FREIGABE,
            Gliedart.UMSETZUNG,
            Gliedart.CODE_REVIEW,
            Gliedart.UEBERTRAGUNG,
            Gliedart.VEROEFFENTLICHUNG);
    assertThat(alle.subList(1, 7))
        .allSatisfy(
            g -> {
              assertThat(g.zustand()).isEqualTo(Zustand.NICHT_ERFASST);
              assertThat(g.quelle()).isEqualTo(Verweis.KEINER);
              assertThat(g.einzelheiten()).isNull();
            });
  }

  @Test
  void planUndFachplan_tragenKeineGliederDesPakets() {
    Entscheidungskette kette =
        KettenAufbau.an(PLAN)
            .vorfahr(vorhanden(FACHPLAN))
            .zugVonMensch(2, "READY", 77, um(1))
            .rechne();

    assertThat(abschnitt(kette, Dokumentart.PLAN).glieder())
        .extracting(Glied::art)
        .containsExactly(Gliedart.HERKUNFT);
  }

  // --- Zuletzt erreichtes Glied eines Pakets (E15) ---------------------------------------------

  private static Nachfahre nachfahre(KettenAufbau aufbau) {
    return aufbau.rechne().nachfahren().getFirst();
  }

  private static KettenAufbau amPlan() {
    return KettenAufbau.an(PLAN).vorfahr(vorhanden(FACHPLAN)).nachfahr(vorhanden(PAKET));
  }

  @Test
  void nachfahre_paketOhneGlieder_herkunft() {
    assertThat(nachfahre(amPlan()).zuletztErreicht()).isEqualTo(Gliedart.HERKUNFT);
  }

  @Test
  void nachfahre_paketMitIssueReview() {
    KettenAufbau aufbau = amPlan().kommentar(50, P, "## Issue-Review, Runde 1\n", um(1));

    assertThat(nachfahre(aufbau).zuletztErreicht()).isEqualTo(Gliedart.ISSUE_REVIEW);
  }

  @Test
  void nachfahre_vonHandGeaendertesIssueReview_zaehltNicht() {
    KettenAufbau aufbau = amPlan().kommentar(vonHand(50, "## Issue-Review, Runde 1", um(1)));

    assertThat(nachfahre(aufbau).zuletztErreicht()).isEqualTo(Gliedart.HERKUNFT);
  }

  @Test
  void nachfahre_zurueckgezogenerVersuch_zaehltNicht() {
    KettenAufbau aufbau =
        amPlan()
            .zugVonMensch(P, "READY", 77, um(1))
            .zugUeberToken(P, "IN_PROGRESS", um(2), LAUF)
            .zugUeberToken(P, "BACKLOG", um(3), LAUF);

    assertThat(nachfahre(aufbau).zuletztErreicht()).isEqualTo(Gliedart.FREIGABE);
  }

  @Test
  void nachfahre_zugOhneSpalte_trenntRueckzugUndReadyNicht() {
    KettenAufbau aufbau =
        amPlan()
            .zugVonMensch(P, "IN_PROGRESS", 77, um(1))
            .zugVonMensch(P, null, 77, um(2))
            .zugVonMensch(P, "READY", 77, um(3));

    assertThat(nachfahre(aufbau).zuletztErreicht()).isEqualTo(Gliedart.HERKUNFT);
  }

  @Test
  void nachfahre_offenerVersuch_umsetzung() {
    KettenAufbau aufbau = amPlan().zugUeberToken(P, "IN_PROGRESS", um(2), LAUF);

    assertThat(nachfahre(aufbau).zuletztErreicht()).isEqualTo(Gliedart.UMSETZUNG);
  }

  @Test
  void nachfahre_nichtUebertragenOderOhneAusgang_zaehltNicht() {
    KettenAufbau aufbau =
        amPlan()
            .kommentar(70, P, CODE_REVIEW, um(7))
            .kommentar(80, P, uebertragung("u1", "lokal", "ausstehend", "nicht übertragen"), um(8))
            .kommentar(81, P, "## Übertragung auf den Hauptzweig\n\nWeg: lokal\n", um(9));

    assertThat(nachfahre(aufbau).zuletztErreicht()).isEqualTo(Gliedart.CODE_REVIEW);
  }

  @Test
  void nachfahre_pullRequestAngelegt_zaehltNichtAlsVeroeffentlicht() {
    KettenAufbau aufbau =
        amPlan()
            .kommentar(
                80,
                P,
                uebertragung("u1", "lokal", "keine, 2026-11-10T11:00:00Z", "übertragen"),
                um(8))
            .kommentar(90, P, veroeffentlichung("3.18.0", "Pull Request angelegt"), um(10));

    assertThat(nachfahre(aufbau).zuletztErreicht()).isEqualTo(Gliedart.UEBERTRAGUNG);
  }

  @Test
  void nachfahre_spaeteresGliedVorFruehererMeldung_hoechstesGilt() {
    KettenAufbau aufbau =
        amPlan()
            .kommentar(90, P, veroeffentlichung("3.18.0", "veröffentlicht"), um(1))
            .kommentar(70, P, CODE_REVIEW, um(7));

    assertThat(nachfahre(aufbau).zuletztErreicht()).isEqualTo(Gliedart.VEROEFFENTLICHUNG);
  }

  @Test
  void nachfahre_planAmFachplan_nachDenDokumentregeln() {
    KettenAufbau aufbau =
        KettenAufbau.an(FACHPLAN)
            .nachfahr(vorhanden(PLAN))
            .zugVonMensch(2, "READY", 77, um(1))
            .kommentar(20, 2, "## Plan-Review, Runde 1\n", um(2));

    assertThat(nachfahre(aufbau).zuletztErreicht()).isEqualTo(Gliedart.PRUEFRUNDE);
  }

  @Test
  void nachfahre_taskAmFachplan_nachDenRegelnDesPakets() {
    KettenAufbau aufbau =
        KettenAufbau.an(FACHPLAN).nachfahr(vorhanden(TASK)).zugVonMensch(P, "READY", 77, um(1));

    assertThat(nachfahre(aufbau).zuletztErreicht()).isEqualTo(Gliedart.FREIGABE);
  }

  // --- Vollständige Kette ----------------------------------------------------------------------

  @Test
  void vollstaendigeKette_keinGliedNichtErfasst() {
    Karte paket = inSpalte(PAKET, "In review", "IN_REVIEW");
    Entscheidungskette kette =
        KettenAufbau.an(paket)
            .vorfahr(vorhanden(PLAN))
            .vorfahr(vorhanden(FACHPLAN))
            .idee(karte(4, 5, Art.IDEE, "[Idea] Kette", "## Geklärter Stand\nErgebnis: #10\n"))
            .angelegt(4, SESSION, 77L, um(0))
            .angelegt(1, TOKEN, null, um(1))
            .angelegt(2, TOKEN, null, um(2))
            .angelegt(P, TOKEN, null, um(3))
            .kommentar(10, 1, "## Fachplan-Review, Runde 1\n", um(4))
            .kommentar(11, 1, "## Einarbeitung, Runde 1\n", um(5))
            .kommentar(20, 2, "## Plan-Review, Runde 1\n", um(6))
            .kommentar(21, 2, "## Einarbeitung, Runde 1\n", um(7))
            .kommentar(50, P, "## Issue-Review, Runde 1\n", um(8))
            .kommentar(51, P, "## Einarbeitung, Runde 1\n", um(9))
            .label(label(1, "ziel:umsetzung", um(10), 77L, SESSION))
            .reviewVorReady(true, null)
            .zugUeberToken(P, "READY", um(11), LAUF)
            .zugUeberToken(P, "IN_PROGRESS", um(12), LAUF)
            .kommentar(60, P, BERICHT, um(13))
            .zugUeberToken(P, "IN_REVIEW", um(13), LAUF)
            .kommentar(70, P, CODE_REVIEW, um(14))
            .kommentar(
                80,
                P,
                uebertragung("u1", "lokal", "keine, 2026-11-10T11:00:00Z", "übertragen"),
                um(15))
            .kommentar(90, P, veroeffentlichung("3.18.0", "veröffentlicht"), um(16))
            .rechne();

    assertThat(kette.abschnitte())
        .flatExtracting(Abschnitt::glieder)
        .allSatisfy(
            g -> {
              assertThat(g.zustand()).isEqualTo(Zustand.ERFASST);
              assertThat(g.ausgefuehrtVon()).isNotNull();
              assertThat(g.zeitpunkt()).isNotNull();
            });
    assertThat(((Umsetzungsangaben) einziges(kette, Gliedart.UMSETZUNG).einzelheiten()).commit())
        .isEqualTo("abc1234");
  }
}
