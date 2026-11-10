package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.AGENT;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.FACHPLAN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.PAKET;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.PLAN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.PLAN_TECHNISCH;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.TOKEN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.ZUGANG;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.abschnitt;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.anderesProjekt;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.archiviert;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.erstes;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.karte;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.papierkorb;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.um;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.vorhanden;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Art;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;

/**
 * Herkunft, Prüfrunden und Nachfahren der Dokument-Glieder (Issue #2115, Plan #2108 E4, E9, E15).
 */
class KartengliederTest {

  // --- Herkunft --------------------------------------------------------------------------------

  @Test
  void herkunft_ueberTokenMitPlanModell_modell() {
    Entscheidungskette kette =
        KettenAufbau.an(PLAN_TECHNISCH).angelegt(2, TOKEN, null, um(3)).rechne();

    Glied herkunft = erstes(kette, Dokumentart.PLAN);
    assertThat(herkunft.art()).isEqualTo(Gliedart.HERKUNFT);
    assertThat(herkunft.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(herkunft.ausgefuehrtVon()).isEqualTo(Akteur.modell("claude-opus-5-5"));
    assertThat(herkunft.zeitpunkt()).isEqualTo(um(3));
    assertThat(herkunft.quelle()).isEqualTo(new Verweis(2L, 20, "[Plan] Technik", true, null));
  }

  @Test
  void herkunft_ueberTokenMitAutorModell_modell() {
    Karte paket = karte(3, 30, Art.ARBEITSPAKET, "Paket", "Author-Model: fable\n");

    Entscheidungskette kette = KettenAufbau.an(paket).angelegt(3, TOKEN, null, um(3)).rechne();

    assertThat(erstes(kette, Dokumentart.PAKET).ausgefuehrtVon()).isEqualTo(Akteur.modell("fable"));
  }

  @Test
  void herkunft_ueberTokenOhneModellzeile_werkzeug() {
    Karte paket = karte(3, 30, Art.ARBEITSPAKET, "Paket", null);

    Entscheidungskette kette = KettenAufbau.an(paket).angelegt(3, TOKEN, null, um(3)).rechne();

    assertThat(erstes(kette, Dokumentart.PAKET).ausgefuehrtVon())
        .isEqualTo(Akteur.werkzeug(ZUGANG, AGENT));
  }

  @Test
  void herkunft_inDerOberflaecheMitModellzeile_mensch() {
    Entscheidungskette kette =
        KettenAufbau.an(PLAN_TECHNISCH).angelegt(2, "SESSION", 42L, um(3)).rechne();

    assertThat(erstes(kette, Dokumentart.PLAN).ausgefuehrtVon()).isEqualTo(Akteur.mensch(42L));
  }

  @Test
  void herkunft_ohnePersonUndOhneToken_akteurNichtErfasst() {
    Entscheidungskette kette =
        KettenAufbau.an(PLAN_TECHNISCH).angelegt(2, null, null, um(3)).rechne();

    Glied herkunft = erstes(kette, Dokumentart.PLAN);
    assertThat(herkunft.ausgefuehrtVon()).isNull();
    assertThat(herkunft.zeitpunkt()).isEqualTo(um(3));
  }

  @Test
  void herkunft_ohneAnlage_erfasstOhneAkteurUndZeitpunkt() {
    Entscheidungskette kette =
        KettenAufbau.an(PLAN_TECHNISCH)
            .aktivitaet(
                new KettenEingabe.Aktivitaet(2, 1L, "MOVED", um(4), TOKEN, ZUGANG, AGENT, "ready"))
            .rechne();

    Glied herkunft = erstes(kette, Dokumentart.PLAN);
    assertThat(herkunft.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(herkunft.ausgefuehrtVon()).isNull();
    assertThat(herkunft.zeitpunkt()).isNull();
  }

  @Test
  void herkunft_ersteAnlageZaehlt() {
    Entscheidungskette kette =
        KettenAufbau.an(PLAN_TECHNISCH)
            .angelegt(2, "SESSION", 2L, um(9))
            .angelegt(2, "SESSION", 1L, um(3))
            .angelegt(2, "SESSION", 3L, um(5))
            .angelegt(9, "SESSION", 5L, um(1))
            .rechne();

    assertThat(erstes(kette, Dokumentart.PLAN).ausgefuehrtVon()).isEqualTo(Akteur.mensch(1L));
  }

  // --- Prüfrunden (E9) -------------------------------------------------------------------------

  @Test
  void pruefrunde_mitPrueferModellFundenUndEinarbeitung() {
    String review =
        """
        ## Plan-Review, Runde 1

        ### Reviewer 1: Architektur, fable
        **WICHTIG**
        **Fund A**
        """;

    Entscheidungskette kette =
        KettenAufbau.an(PLAN_TECHNISCH)
            .kommentar(21, 2, review, um(5))
            .kommentar(22, 2, "## Einarbeitung, Runde 1\n", um(6))
            .rechne();

    Glied runde = abschnitt(kette, Dokumentart.PLAN).glieder().get(1);
    assertThat(runde.art()).isEqualTo(Gliedart.PRUEFRUNDE);
    assertThat(runde.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(runde.ausgefuehrtVon()).isEqualTo(Akteur.werkzeug(ZUGANG, AGENT));
    assertThat(runde.zeitpunkt()).isEqualTo(um(5));
    assertThat(runde.quelle()).isEqualTo(new Verweis(2L, 20, null, true, 21L));
    Pruefung pruefung = runde.pruefung();
    assertThat(pruefung).isNotNull();
    assertThat(pruefung.art()).isEqualTo(Meldungsart.PLAN_REVIEW);
    assertThat(pruefung.runde()).isEqualTo(1);
    assertThat(pruefung.pruefer()).extracting(Reviewer::modell).containsExactly("fable");
    assertThat(pruefung.funde()).isEqualTo(new Fundzahlen(0, 0, 1, 0));
    Glied einarbeitung = pruefung.einarbeitung();
    assertThat(einarbeitung.art()).isEqualTo(Gliedart.EINARBEITUNG);
    assertThat(einarbeitung.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(einarbeitung.zeitpunkt()).isEqualTo(um(6));
    assertThat(einarbeitung.quelle().kommentar()).isEqualTo(22L);
  }

  @Test
  void pruefrunde_ohneEinarbeitungDerselbenRunde_einarbeitungNichtErfasst() {
    Entscheidungskette kette =
        KettenAufbau.an(PLAN_TECHNISCH)
            .kommentar(21, 2, "## Plan-Review, Runde 2\n", um(5))
            .kommentar(22, 2, "## Einarbeitung, Runde 1\n", um(6))
            .kommentar(23, 3, "## Einarbeitung, Runde 2\n", um(7))
            .rechne();

    Pruefung pruefung = abschnitt(kette, Dokumentart.PLAN).glieder().get(1).pruefung();
    assertThat(pruefung).isNotNull();
    assertThat(pruefung.einarbeitung().zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(pruefung.einarbeitung().quelle()).isEqualTo(Verweis.KEINER);
  }

  @Test
  void pruefrunde_nachZeitpunktUndId() {
    Entscheidungskette kette =
        KettenAufbau.an(PLAN_TECHNISCH)
            .kommentar(31, 2, "## Plan-Review, Runde 2\n", um(9))
            .kommentar(30, 2, "## Plan-Review, Runde 1\n", um(5))
            .kommentar(29, 2, "## Plan-Review, Runde 3\n", um(9))
            .rechne();

    assertThat(abschnitt(kette, Dokumentart.PLAN).glieder().subList(1, 4))
        .extracting(g -> g.quelle().kommentar())
        .containsExactly(30L, 29L, 31L);
  }

  @Test
  void pruefrunde_ausDerOberflaecheOderIssueReview_zaehltNichtAlsDokumentrunde() {
    Entscheidungskette kette =
        KettenAufbau.an(PLAN_TECHNISCH)
            .kommentar(
                new KettenEingabe.Kommentar(
                    21, 2, "## Plan-Review, Runde 1\n", um(5), "SESSION", null, null, null, null))
            .kommentar(22, 2, "## Issue-Review, Runde 1\n", um(6))
            .kommentar(23, 2, "## Code-Review (Schritt 6)\n", um(7))
            .kommentar(24, 2, "Normaler Kommentar", um(8))
            .rechne();

    assertThat(abschnitt(kette, Dokumentart.PLAN).glieder()).hasSize(1);
  }

  @Test
  void pruefrunde_vonHandGeaendert_angabenNichtErfasstMitHinweis() {
    Entscheidungskette kette =
        KettenAufbau.an(FACHPLAN)
            .kommentar(
                new KettenEingabe.Kommentar(
                    41,
                    1,
                    "Alles gut.",
                    um(5),
                    TOKEN,
                    ZUGANG,
                    AGENT,
                    um(8),
                    "## Fachplan-Review, Runde 3"))
            .kommentar(42, 1, "## Einarbeitung, Runde 3\n", um(9))
            .rechne();

    Glied runde = abschnitt(kette, Dokumentart.FACHPLAN).glieder().get(1);
    assertThat(runde.art()).isEqualTo(Gliedart.PRUEFRUNDE);
    assertThat(runde.zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(runde.hinweise()).containsExactly(Hinweis.VON_HAND_GEAENDERT);
    assertThat(runde.ausgefuehrtVon()).isNull();
    assertThat(runde.zeitpunkt()).isNull();
    assertThat(runde.quelle().kommentar()).isEqualTo(41L);
    Pruefung pruefung = runde.pruefung();
    assertThat(pruefung).isNotNull();
    assertThat(pruefung.art()).isEqualTo(Meldungsart.FACHPLAN_REVIEW);
    assertThat(pruefung.runde()).isEqualTo(3);
    assertThat(pruefung.pruefer()).isEmpty();
    assertThat(pruefung.funde()).isNull();
    assertThat(pruefung.einarbeitung().zustand()).isEqualTo(Zustand.ERFASST);
  }

  @Test
  void einarbeitung_vonHandGeaendert_nichtErfasstMitHinweis() {
    Entscheidungskette kette =
        KettenAufbau.an(FACHPLAN)
            .kommentar(41, 1, "## Fachplan-Review, Runde 1\n", um(5))
            .kommentar(
                new KettenEingabe.Kommentar(
                    42, 1, "egal", um(6), TOKEN, ZUGANG, AGENT, um(8), "## Einarbeitung, Runde 1"))
            .rechne();

    Pruefung pruefung = abschnitt(kette, Dokumentart.FACHPLAN).glieder().get(1).pruefung();
    assertThat(pruefung).isNotNull();
    Glied einarbeitung = pruefung.einarbeitung();
    assertThat(einarbeitung.zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(einarbeitung.hinweise()).containsExactly(Hinweis.VON_HAND_GEAENDERT);
    assertThat(einarbeitung.quelle().kommentar()).isEqualTo(42L);
    assertThat(einarbeitung.zeitpunkt()).isNull();
  }

  @Test
  void handMarke_kopfzeileKeinerDokumentrunde_zaehltNicht() {
    Entscheidungskette kette =
        KettenAufbau.an(FACHPLAN)
            .kommentar(
                new KettenEingabe.Kommentar(
                    41, 1, "x", um(5), TOKEN, ZUGANG, AGENT, um(8), "## Veröffentlichung"))
            .kommentar(
                new KettenEingabe.Kommentar(42, 1, "y", um(5), TOKEN, ZUGANG, AGENT, um(8), null))
            .kommentar(
                new KettenEingabe.Kommentar(
                    43,
                    1,
                    "## Plan-Review, Runde 1",
                    um(5),
                    "SESSION",
                    null,
                    null,
                    um(8),
                    "## Plan-Review, Runde 1"))
            .rechne();

    assertThat(abschnitt(kette, Dokumentart.FACHPLAN).glieder()).hasSize(1);
  }

  // --- Nachfahren (Kriterium 3, E15) -----------------------------------------------------------

  @Test
  void nachfahren_amFachplan_jeVerweisMitSpalteUndZuletztErreichtemGlied() {
    Karte paketA =
        KettenAufbau.inSpalte(karte(5, 31, Art.ARBEITSPAKET, "A", null), "In review", "in_review");
    Karte paketB = karte(6, 32, Art.ARBEITSPAKET, "B", null);
    Karte paketC = archiviert(karte(7, 33, Art.ARBEITSPAKET, "C", null));

    Entscheidungskette kette =
        KettenAufbau.an(FACHPLAN)
            .nachfahr(vorhanden(PLAN))
            .nachfahr(vorhanden(paketA))
            .nachfahr(vorhanden(paketB))
            .nachfahr(vorhanden(paketC))
            .kommentar(51, 2, "## Plan-Review, Runde 1\n", um(1))
            .kommentar(52, 2, "## Einarbeitung, Runde 1\n", um(2))
            .kommentar(53, 6, "## Plan-Review, Runde 1\n", um(3))
            .rechne();

    assertThat(kette.nachfahren())
        .containsExactly(
            new Nachfahre(
                Zustand.ERFASST,
                new Verweis(2L, 20, "[Plan] Kette", true, null),
                "Ready",
                "ready",
                Gliedart.EINARBEITUNG,
                Set.of()),
            new Nachfahre(
                Zustand.ERFASST,
                new Verweis(5L, 31, "A", true, null),
                "In review",
                "in_review",
                Gliedart.HERKUNFT,
                Set.of()),
            new Nachfahre(
                Zustand.ERFASST,
                new Verweis(6L, 32, "B", true, null),
                "Ready",
                "ready",
                Gliedart.PRUEFRUNDE,
                Set.of()),
            new Nachfahre(
                Zustand.ERFASST,
                new Verweis(7L, 33, "C", false, null),
                "Ready",
                "ready",
                Gliedart.HERKUNFT,
                Set.of()));
  }

  @Test
  void nachfahren_papierkorbUndAnderesProjekt_nichtErfasst() {
    Entscheidungskette kette =
        KettenAufbau.an(PLAN).nachfahr(papierkorb(31)).nachfahr(anderesProjekt()).rechne();

    assertThat(kette.nachfahren())
        .containsExactly(
            new Nachfahre(Zustand.NICHT_ERFASST, Verweis.nurNummer(31), null, null, null, Set.of()),
            new Nachfahre(
                Zustand.NICHT_ERFASST,
                Verweis.KEINER,
                null,
                null,
                null,
                Set.of(Hinweis.ANDERES_PROJEKT)));
  }

  @Test
  void nachfahren_rundeVonHandGeaendert_zaehltNichtAlsErreicht() {
    Karte paket = karte(5, 31, Art.ARBEITSPAKET, "A", null);

    Entscheidungskette kette =
        KettenAufbau.an(PLAN)
            .nachfahr(vorhanden(paket))
            .kommentar(
                new KettenEingabe.Kommentar(
                    61, 5, "x", um(5), TOKEN, ZUGANG, AGENT, um(8), "## Plan-Review, Runde 1"))
            .rechne();

    assertThat(kette.nachfahren().getFirst().zuletztErreicht()).isEqualTo(Gliedart.HERKUNFT);
  }

  @Test
  void nachfahren_amPaket_keine() {
    Entscheidungskette kette =
        KettenAufbau.an(PAKET).nachfahr(vorhanden(karte(5, 31, Art.TASK, "x", null))).rechne();

    assertThat(kette.nachfahren()).isEmpty();
  }
}
