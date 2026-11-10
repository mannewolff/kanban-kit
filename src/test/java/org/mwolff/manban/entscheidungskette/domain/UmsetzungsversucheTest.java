package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.AGENT;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.LAUF;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.SESSION;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.ZUGANG;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.erstes;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.um;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.BERICHT;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.P;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.einziges;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.glieder;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.paket;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.vonHand;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Kommentar;

/** Die Versuche der Umsetzung am Paket (Issue #2116, Plan #2108 E10). */
class UmsetzungsversucheTest {

  // --- Umsetzung (E10) -------------------------------------------------------------------------

  @Test
  void umsetzung_mitBericht_abgeschlossenMitCommitUndPrueflauf() {
    Entscheidungskette kette =
        paket()
            .zugUeberToken(P, "IN_PROGRESS", um(5), LAUF)
            .kommentar(60, P, BERICHT, um(9))
            .rechne();

    Glied glied = einziges(kette, Gliedart.UMSETZUNG);
    assertThat(glied.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(glied.ausgefuehrtVon()).isEqualTo(Akteur.modell("claude-opus-5-5"));
    assertThat(glied.zeitpunkt()).isEqualTo(um(5));
    assertThat(glied.quelle()).isEqualTo(new Verweis(3L, 30, null, true, 60L));
    assertThat(glied.hinweise()).isEmpty();
    assertThat(glied.einzelheiten())
        .isEqualTo(
            new Umsetzungsangaben(
                Umsetzungsausgang.ABGESCHLOSSEN,
                um(9),
                "abc1234",
                new Angabe("gruen", Instant.parse("2026-11-10T09:00:00Z"))));
  }

  @Test
  void umsetzung_zurueckgezogenUndWiederholt_beideVersucheStehen() {
    Entscheidungskette kette =
        paket()
            .zugUeberToken(P, "IN_PROGRESS", um(1), LAUF)
            .zugUeberToken(P, "BACKLOG", um(2), LAUF)
            .zugUeberToken(P, "READY", um(3), null)
            .zugUeberToken(P, "IN_PROGRESS", um(4), LAUF)
            .zugUeberToken(P, "IN_REVIEW", um(6), LAUF)
            .kommentar(60, P, BERICHT, um(6))
            .rechne();

    List<Glied> versuche = glieder(kette, Gliedart.UMSETZUNG);
    assertThat(versuche).hasSize(2);
    Glied zurueck = versuche.get(0);
    assertThat(zurueck.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(zurueck.ausgefuehrtVon()).isEqualTo(Akteur.werkzeug(ZUGANG, AGENT));
    assertThat(zurueck.zeitpunkt()).isEqualTo(um(1));
    assertThat(zurueck.quelle()).isEqualTo(new Verweis(3L, 30, "Paket", true, null));
    assertThat(zurueck.einzelheiten())
        .isEqualTo(
            new Umsetzungsangaben(Umsetzungsausgang.ZURUECKGEZOGEN, um(2), null, Angabe.LEER));
    assertThat(((Umsetzungsangaben) versuche.get(1).einzelheiten()).ausgang())
        .isEqualTo(Umsetzungsausgang.ABGESCHLOSSEN);
    assertThat(versuche.get(1).zeitpunkt()).isEqualTo(um(4));
  }

  @Test
  void umsetzung_rueckzugNachReady_beendetDenVersuch() {
    Entscheidungskette kette =
        paket()
            .zugUeberToken(P, "IN_PROGRESS", um(1), LAUF)
            .zugUeberToken(P, "READY", um(2), LAUF)
            .rechne();

    assertThat(((Umsetzungsangaben) einziges(kette, Gliedart.UMSETZUNG).einzelheiten()).ausgang())
        .isEqualTo(Umsetzungsausgang.ZURUECKGEZOGEN);
  }

  @Test
  void umsetzung_ohneBericht_akteurUndZeitpunktAusDemZugCommitNichtErfasst() {
    Entscheidungskette kette =
        paket()
            .zugVonMensch(P, "READY", 77, um(1))
            .zugVonMensch(P, "IN_PROGRESS", 78, um(2))
            .zugVonMensch(P, "IN_REVIEW", 78, um(3))
            .rechne();

    Glied glied = einziges(kette, Gliedart.UMSETZUNG);
    assertThat(glied.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(glied.ausgefuehrtVon()).isEqualTo(Akteur.mensch(78L));
    assertThat(glied.zeitpunkt()).isEqualTo(um(2));
    assertThat(glied.einzelheiten())
        .isEqualTo(new Umsetzungsangaben(Umsetzungsausgang.OFFEN, null, null, Angabe.LEER));
    assertThat(einziges(kette, Gliedart.FREIGABE).zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(erstes(kette, Dokumentart.PLAN).zustand()).isEqualTo(Zustand.ERFASST);
  }

  @Test
  void umsetzung_zugOhneBestimmbareSpalte_beginntUndBeendetKeinenVersuch() {
    Entscheidungskette kette =
        paket()
            .zugUeberToken(P, null, um(1), LAUF)
            .zugUeberToken(P, "IN_PROGRESS", um(2), LAUF)
            .zugUeberToken(P, null, um(3), LAUF)
            .rechne();

    Glied glied = einziges(kette, Gliedart.UMSETZUNG);
    assertThat(glied.zeitpunkt()).isEqualTo(um(2));
    assertThat(((Umsetzungsangaben) glied.einzelheiten()).ausgang())
        .isEqualTo(Umsetzungsausgang.OFFEN);
  }

  @Test
  void umsetzung_zweimalNachInProgress_derErsteBleibtOffen() {
    Entscheidungskette kette =
        paket()
            .zugUeberToken(P, "IN_PROGRESS", um(1), LAUF)
            .zugUeberToken(P, "IN_PROGRESS", um(2), LAUF)
            .kommentar(60, P, BERICHT, um(3))
            .rechne();

    assertThat(glieder(kette, Gliedart.UMSETZUNG))
        .extracting(g -> ((Umsetzungsangaben) g.einzelheiten()).ausgang())
        .containsExactly(Umsetzungsausgang.OFFEN, Umsetzungsausgang.ABGESCHLOSSEN);
  }

  @Test
  void umsetzung_berichtOhneZugNachInProgress_eigenerVersuch() {
    String ohneModell = BERICHT.replace("Umgesetzt von: claude-opus-5-5\n", "");
    Entscheidungskette kette =
        paket()
            .kommentar(60, P, BERICHT, um(3))
            .zugUeberToken(P, "IN_REVIEW", um(4), LAUF)
            .kommentar(61, P, ohneModell, um(5))
            .rechne();

    List<Glied> versuche = glieder(kette, Gliedart.UMSETZUNG);
    assertThat(versuche).extracting(Glied::zeitpunkt).containsExactly(um(3), um(5));
    assertThat(versuche)
        .extracting(Glied::ausgefuehrtVon)
        .containsExactly(Akteur.modell("claude-opus-5-5"), Akteur.werkzeug(ZUGANG, AGENT));
  }

  @Test
  void umsetzung_berichtOhneModell_akteurAusDemZug() {
    String ohneModell = BERICHT.replace("Umgesetzt von: claude-opus-5-5\n", "");
    Entscheidungskette kette =
        paket()
            .zugVonMensch(P, "IN_PROGRESS", 78, um(2))
            .kommentar(60, P, ohneModell, um(3))
            .rechne();

    assertThat(einziges(kette, Gliedart.UMSETZUNG).ausgefuehrtVon()).isEqualTo(Akteur.mensch(78L));
  }

  @Test
  void umsetzung_berichtVonHandGeaendert_angabenNichtErfasst() {
    Entscheidungskette mitZug =
        paket()
            .zugVonMensch(P, "IN_PROGRESS", 78, um(2))
            .kommentar(vonHand(60, "## Abschlussbericht Issue #30", um(3)))
            .rechne();
    Entscheidungskette ohneZug =
        paket().kommentar(vonHand(60, "## Completion report issue #30", um(3))).rechne();

    Glied glied = einziges(mitZug, Gliedart.UMSETZUNG);
    assertThat(glied.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(glied.hinweise()).containsExactly(Hinweis.VON_HAND_GEAENDERT);
    assertThat(glied.ausgefuehrtVon()).isEqualTo(Akteur.mensch(78L));
    assertThat(glied.einzelheiten())
        .isEqualTo(
            new Umsetzungsangaben(Umsetzungsausgang.ABGESCHLOSSEN, um(3), null, Angabe.LEER));
    Glied ohne = einziges(ohneZug, Gliedart.UMSETZUNG);
    assertThat(ohne.zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(ohne.hinweise()).containsExactly(Hinweis.VON_HAND_GEAENDERT);
    assertThat(ohne.quelle().kommentar()).isEqualTo(60L);
    assertThat(ohne.einzelheiten()).isNull();
  }

  @Test
  void umsetzung_berichtAusDerOberflaecheUndFremderKopf_zaehlenNicht() {
    Entscheidungskette kette =
        paket()
            .kommentar(new Kommentar(60, P, BERICHT, um(3), SESSION, null, null, null, null))
            .kommentar(vonHand(61, "## Plan-Review, Runde 1", um(4)))
            .kommentar(62, P, "## Issue-Review, Runde 1\n", um(5))
            .rechne();

    assertThat(einziges(kette, Gliedart.UMSETZUNG).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
  }
}
