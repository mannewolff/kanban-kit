package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.AGENT;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.FACHPLAN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.LAUF;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.PAKET;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.SESSION;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.TOKEN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.ZUGANG;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.geloescht;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.um;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.vorhanden;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.P;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.einziges;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.label;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.paket;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Aktivitaet;

/** Die Freigabe zur Umsetzung am Paket (Issue #2116, Plan #2108 E11). */
class FreigabenTest {

  // --- Freigabe (E11) --------------------------------------------------------------------------

  @Test
  void freigabe_zugDurchEinenMenschen_istSelbstDieFreigabe() {
    Entscheidungskette kette = paket().zugVonMensch(P, "READY", 77, um(5)).rechne();

    Glied glied = einziges(kette, Gliedart.FREIGABE);
    assertThat(glied.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(glied.freigegebenVon()).isEqualTo(Akteur.mensch(77L));
    assertThat(glied.ausgefuehrtVon()).isEqualTo(Akteur.mensch(77L));
    assertThat(glied.zeitpunkt()).isEqualTo(um(5));
    assertThat(glied.quelle()).isEqualTo(new Verweis(3L, 30, "Paket", true, null));
    assertThat(glied.einzelheiten())
        .isEqualTo(new Freigabeangaben(Freigabefall.ZUG_NACH_READY, um(5)));
  }

  @Test
  void freigabe_zielAmFachplanVorDemZugDesLaufs_giltAlsFreigabe() {
    Entscheidungskette kette =
        paket()
            .label(label(1, "ziel:umsetzung", um(2), 5L, SESSION))
            .zugUeberToken(P, "READY", um(5), LAUF)
            .rechne();

    Glied glied = einziges(kette, Gliedart.FREIGABE);
    assertThat(glied.freigegebenVon()).isEqualTo(Akteur.mensch(5L));
    assertThat(glied.zeitpunkt()).isEqualTo(um(2));
    assertThat(glied.ausgefuehrtVon()).isEqualTo(Akteur.werkzeug(ZUGANG, AGENT));
    assertThat(glied.quelle()).isEqualTo(new Verweis(1L, 10, "[Requirement] Kette", true, null));
    assertThat(glied.einzelheiten())
        .isEqualTo(new Freigabeangaben(Freigabefall.KENNZEICHEN, um(5)));
  }

  @Test
  void freigabe_juengstesKennzeichenBisZumZugGilt() {
    Entscheidungskette kette =
        paket()
            .label(label(1, "goal:push-prepared", um(1), 5L, SESSION))
            .label(label(2, "kit:durchziehen", um(3), 6L, SESSION))
            .label(label(2, "ziel:umsetzung", um(5), 7L, SESSION))
            .label(label(2, "ziel:umsetzung", um(6), 8L, SESSION))
            .label(label(2, "kit:night", um(4), 9L, SESSION))
            .label(label(P, "ziel:umsetzung", um(4), 10L, SESSION))
            .zugUeberToken(P, "READY", um(5), LAUF)
            .rechne();

    Glied glied = einziges(kette, Gliedart.FREIGABE);
    assertThat(glied.freigegebenVon()).isEqualTo(Akteur.mensch(7L));
    assertThat(glied.quelle().karte()).isEqualTo(2L);
  }

  @Test
  void freigabe_kennzeichenUeberToken_nenntDenZugang_ohneHerkunftNichtErfasst() {
    Entscheidungskette ueberToken =
        paket()
            .label(label(2, "kit:carry-through", um(1), null, TOKEN))
            .zugUeberToken(P, "READY", um(5), LAUF)
            .rechne();
    Entscheidungskette ohneHerkunft =
        paket()
            .label(label(2, "kit:carry-through", um(1), 5L, null))
            .zugUeberToken(P, "READY", um(5), LAUF)
            .rechne();
    Entscheidungskette altesKennzeichen =
        paket()
            .label(label(2, "kit:carry-through", um(1), null, SESSION))
            .zugUeberToken(P, "READY", um(5), LAUF)
            .rechne();

    assertThat(einziges(ueberToken, Gliedart.FREIGABE).freigegebenVon())
        .isEqualTo(Akteur.werkzeug(ZUGANG, null));
    assertThat(einziges(ohneHerkunft, Gliedart.FREIGABE).freigegebenVon()).isNull();
    assertThat(einziges(altesKennzeichen, Gliedart.FREIGABE).freigegebenVon())
        .isEqualTo(Akteur.mensch(null));
  }

  @Test
  void freigabe_tokenZugOhneLaufkennung_istSelbstDieFreigabe() {
    Entscheidungskette kette =
        paket()
            .label(label(1, "ziel:umsetzung", um(2), 5L, SESSION))
            .zugUeberToken(P, "READY", um(5), null)
            .rechne();

    Glied glied = einziges(kette, Gliedart.FREIGABE);
    assertThat(glied.freigegebenVon()).isEqualTo(Akteur.werkzeug(ZUGANG, AGENT));
    assertThat(((Freigabeangaben) glied.einzelheiten()).fall())
        .isEqualTo(Freigabefall.ZUG_NACH_READY);
  }

  @Test
  void freigabe_menschlicherZugMitLaufkennung_istSelbstDieFreigabe() {
    Entscheidungskette kette =
        paket()
            .label(label(1, "ziel:umsetzung", um(2), 5L, SESSION))
            .aktivitaet(new Aktivitaet(P, 77L, "MOVED", um(5), SESSION, null, null, "READY", LAUF))
            .rechne();

    assertThat(einziges(kette, Gliedart.FREIGABE).freigegebenVon()).isEqualTo(Akteur.mensch(77L));
  }

  @Test
  void freigabe_laufOhneKennzeichen_nenntDenZugangAlsWerkzeug() {
    Entscheidungskette kette = paket().zugUeberToken(P, "READY", um(5), LAUF).rechne();

    Glied glied = einziges(kette, Gliedart.FREIGABE);
    assertThat(glied.freigegebenVon()).isEqualTo(Akteur.werkzeug(ZUGANG, AGENT));
    assertThat(((Freigabeangaben) glied.einzelheiten()).fall())
        .isEqualTo(Freigabefall.ZUG_NACH_READY);
  }

  @Test
  void freigabe_nichtFreigebendesLabelAmFachplan_zaehltNicht() {
    Entscheidungskette kette =
        paket()
            .label(label(1, "kit:night", um(2), 5L, SESSION))
            .zugUeberToken(P, "READY", um(5), LAUF)
            .rechne();

    Glied glied = einziges(kette, Gliedart.FREIGABE);
    assertThat(glied.freigegebenVon()).isEqualTo(Akteur.werkzeug(ZUGANG, AGENT));
    assertThat(((Freigabeangaben) glied.einzelheiten()).fall())
        .isEqualTo(Freigabefall.ZUG_NACH_READY);
  }

  @Test
  void freigabe_kennzeichenAnGeloeschtemPlan_zaehltNicht() {
    Entscheidungskette kette =
        KettenAufbau.an(PAKET)
            .vorfahr(geloescht(20))
            .vorfahr(vorhanden(FACHPLAN))
            .label(label(2, "ziel:umsetzung", um(2), 5L, SESSION))
            .zugUeberToken(P, "READY", um(5), LAUF)
            .rechne();

    assertThat(((Freigabeangaben) einziges(kette, Gliedart.FREIGABE).einzelheiten()).fall())
        .isEqualTo(Freigabefall.ZUG_NACH_READY);
  }

  @Test
  void freigabe_rueckzugAusInProgressNachReady_istKeineFreigabe() {
    Entscheidungskette kette =
        paket()
            .zugVonMensch(P, "READY", 77, um(1))
            .zugUeberToken(P, "IN_PROGRESS", um(2), LAUF)
            .zugUeberToken(P, "READY", um(3), LAUF)
            .zugUeberToken(P, null, um(4), LAUF)
            .rechne();

    Glied glied = einziges(kette, Gliedart.FREIGABE);
    assertThat(glied.freigegebenVon()).isEqualTo(Akteur.mensch(77L));
    assertThat(glied.zeitpunkt()).isEqualTo(um(1));
  }

  @Test
  void freigabe_zurueckNachBacklogUndWiederNachReady_derSpaetereZugGilt() {
    Entscheidungskette kette =
        paket()
            .zugVonMensch(P, "READY", 77, um(1))
            .zugVonMensch(P, "BACKLOG", 77, um(2))
            .zugVonMensch(P, "READY", 78, um(3))
            .rechne();

    assertThat(einziges(kette, Gliedart.FREIGABE).freigegebenVon()).isEqualTo(Akteur.mensch(78L));
  }

  @Test
  void freigabe_zugOhneBestimmbareSpalte_istKeineFreigabe() {
    Entscheidungskette kette = paket().zugVonMensch(P, null, 77, um(1)).rechne();

    assertThat(einziges(kette, Gliedart.FREIGABE).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
  }

  @Test
  void freigabe_alterZugOhneHerkunft_akteurNichtErfasst() {
    Entscheidungskette kette =
        paket()
            .aktivitaet(new Aktivitaet(P, 77L, "MOVED", um(1), null, null, null, "READY", null))
            .rechne();

    Glied glied = einziges(kette, Gliedart.FREIGABE);
    assertThat(glied.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(glied.freigegebenVon()).isNull();
  }
}
