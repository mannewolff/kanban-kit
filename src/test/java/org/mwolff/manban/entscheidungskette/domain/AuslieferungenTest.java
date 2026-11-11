package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.AGENT;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.ZUGANG;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.erstes;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.um;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.CODE_REVIEW;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.P;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.einziges;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.glieder;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.nachtrag;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.paket;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.uebertragung;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.veroeffentlichung;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.vonHand;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mwolff.manban.common.KitMeldungen.Wert;

/** Code-Review, Übertragung und Veröffentlichung am Paket (Issue #2116, Plan #2108 E15). */
class AuslieferungenTest {

  // --- Code-Review -----------------------------------------------------------------------------

  @Test
  void codeReview_mitZahlDerFunde() {
    Entscheidungskette kette =
        paket()
            .kommentar(70, P, CODE_REVIEW + "\n**WICHTIG — eins**\n\n**HINWEIS — zwei**\n", um(7))
            .kommentar(vonHand(71, "## Code review (step 7)", um(8)))
            .rechne();

    List<Glied> reviews = glieder(kette, Gliedart.CODE_REVIEW);
    assertThat(reviews).hasSize(2);
    Glied erstes = reviews.get(0);
    assertThat(erstes.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(erstes.ausgefuehrtVon()).isEqualTo(Akteur.werkzeug(ZUGANG, AGENT));
    assertThat(erstes.zeitpunkt()).isEqualTo(um(7));
    assertThat(erstes.quelle()).isEqualTo(new Verweis(3L, 30, null, true, 70L));
    assertThat(erstes.einzelheiten()).isEqualTo(new CodeReview(new Fundzahlen(0, 0, 1, 1)));
    assertThat(reviews.get(1).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(reviews.get(1).hinweise()).containsExactly(Hinweis.VON_HAND_GEAENDERT);
    assertThat(reviews.get(1).quelle().kommentar()).isEqualTo(71L);
  }

  // --- Übertragung (Kriterien 10, 11, 12) ------------------------------------------------------

  @ParameterizedTest
  @CsvSource({
    "lokal,LOKAL",
    "build-dienst,BUILD_DIENST",
    "notfall,NOTFALL",
    "vorbereitet,VORBEREITET",
    "emergency,NOTFALL"
  })
  void uebertragung_jederWeg(String weg, Wert erwartet) {
    Entscheidungskette kette =
        paket()
            .kommentar(80, P, uebertragung("u1", weg, "gruen, 2026-11-10T11:05:00Z", "ok"), um(8))
            .rechne();

    Uebertragung u = (Uebertragung) einziges(kette, Gliedart.UEBERTRAGUNG).einzelheiten();
    assertThat(u.weg()).isEqualTo(erwartet);
    assertThat(u.prueflauf()).isEqualTo(new Angabe("gruen", Instant.parse("2026-11-10T09:00:00Z")));
    assertThat(u.quelle()).isEqualTo("Prüflauf 2026-11-10T09:00:00Z");
  }

  @Test
  void uebertragung_getrennteAkteure() {
    Entscheidungskette kette =
        paket()
            .kommentar(
                80,
                P,
                uebertragung("u1", "lokal", "keine, 2026-11-10T11:00:00Z", "übertragen"),
                um(8))
            .rechne();

    Glied glied = einziges(kette, Gliedart.UEBERTRAGUNG);
    assertThat(glied.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(glied.freigegebenVon()).isEqualTo(Akteur.menschGenannt("Erika Muster"));
    assertThat(glied.freigegebenVon())
        .isEqualTo(new Akteur(Akteurtyp.MENSCH, null, "Erika Muster", null));
    assertThat(glied.ausgefuehrtVon()).isEqualTo(Akteur.modell("claude-opus-5-5"));
    assertThat(glied.zeitpunkt()).isEqualTo(Instant.parse("2026-11-10T11:00:00Z"));
    assertThat(glied.ergebnis()).isEqualTo("übertragen");
    assertThat(glied.quelle()).isEqualTo(new Verweis(3L, 30, null, true, 80L));
    assertThat(glied.hinweise()).isEmpty();
    assertThat(((Uebertragung) glied.einzelheiten()).freigegebenVon().zeitpunkt())
        .isEqualTo(Instant.parse("2026-11-10T10:00:00Z"));
  }

  @Test
  void uebertragung_ohneAkteure_nichtErfasstUndZeitpunktDesKommentars() {
    Entscheidungskette kette =
        paket()
            .kommentar(
                80,
                P,
                "## Übertragung auf den Hauptzweig\n\nFreigegeben von: nicht erfasst\n",
                um(8))
            .rechne();

    Glied glied = einziges(kette, Gliedart.UEBERTRAGUNG);
    assertThat(glied.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(glied.freigegebenVon()).isNull();
    assertThat(glied.ausgefuehrtVon()).isNull();
    assertThat(glied.zeitpunkt()).isEqualTo(um(8));
    assertThat(glied.ergebnis()).isNull();
  }

  @Test
  void uebertragung_ausstehendVorDemNachtrag_bleibtAusstehend() {
    Entscheidungskette kette =
        paket()
            .kommentar(80, P, uebertragung("u1", "lokal", "ausstehend", "übertragen"), um(8))
            .kommentar(
                81,
                P,
                nachtrag("Versuch: u2\nCommit: c-u2\nBuild-Dienst: gruen, 2026-11-10T13:00:00Z\n"),
                um(9))
            .rechne();

    Uebertragung u = (Uebertragung) einziges(kette, Gliedart.UEBERTRAGUNG).einzelheiten();
    assertThat(u.buildDienst().ist(Wert.AUSSTEHEND)).isTrue();
    assertThat(u.quelle()).isEqualTo("Prüflauf 2026-11-10T09:00:00Z");
  }

  @Test
  void uebertragung_ausstehendNachDemNachtragUeberDenVersuch_ergebnisDesBuildDiensts() {
    Entscheidungskette kette =
        paket()
            .kommentar(80, P, uebertragung("u1", "lokal", "ausstehend", "übertragen"), um(8))
            .kommentar(
                81,
                P,
                nachtrag(
                    "Versuch: u1\nBuild-Dienst: rot, 2026-11-10T12:00:00Z\nQuelle: https://ci/1\n"),
                um(9))
            .kommentar(
                82,
                P,
                """
                ## Build service recorded

                Attempt: u1
                Build service: gruen, 2026-11-10T13:00:00Z
                """,
                um(10))
            .rechne();

    Uebertragung u = (Uebertragung) einziges(kette, Gliedart.UEBERTRAGUNG).einzelheiten();
    assertThat(u.buildDienst())
        .isEqualTo(new Angabe("gruen", Instant.parse("2026-11-10T13:00:00Z")));
    assertThat(u.quelle()).isEqualTo("Prüflauf 2026-11-10T09:00:00Z");
    assertThat(u.versuch()).isEqualTo("u1");
    assertThat(u.commit()).isEqualTo("c-u1");
    assertThat(u.basis()).isEqualTo("b-u1");
    assertThat(u.weg()).isEqualTo(Wert.LOKAL);
    assertThat(u.ausgang()).isEqualTo("übertragen");
    assertThat(u.freigegebenVon().wert()).isEqualTo("Erika Muster");
    assertThat(u.ausgefuehrtVon().wert()).isEqualTo("claude-opus-5-5");
    assertThat(u.prueflauf().wert()).isEqualTo("gruen");
  }

  @Test
  void uebertragung_nachtragUeberDenCommit_mitSeinerQuelle() {
    Entscheidungskette kette =
        paket()
            .kommentar(
                80,
                P,
                "## Übertragung auf den Hauptzweig\n\nCommit: c-9\nBuild-Dienst: ausstehend\n",
                um(8))
            .kommentar(
                81,
                P,
                nachtrag(
                    "Commit: c-9\nBuild-Dienst: gruen, 2026-11-10T13:00:00Z\nQuelle: https://ci/9\n"),
                um(9))
            .kommentar(vonHand(83, "## Build-Dienst nachgetragen", um(10)))
            .rechne();

    Uebertragung u = (Uebertragung) einziges(kette, Gliedart.UEBERTRAGUNG).einzelheiten();
    assertThat(u.buildDienst().wert()).isEqualTo("gruen");
    assertThat(u.quelle()).isEqualTo("https://ci/9");
  }

  @Test
  void uebertragung_nachtragOhneVersuchUndCommit_passtNicht() {
    Entscheidungskette kette =
        paket()
            .kommentar(
                80, P, "## Übertragung auf den Hauptzweig\n\nBuild-Dienst: ausstehend\n", um(8))
            .kommentar(81, P, nachtrag("Build-Dienst: gruen, 2026-11-10T13:00:00Z\n"), um(9))
            .rechne();

    Uebertragung u = (Uebertragung) einziges(kette, Gliedart.UEBERTRAGUNG).einzelheiten();
    assertThat(u.buildDienst().ist(Wert.AUSSTEHEND)).isTrue();
  }

  @Test
  void uebertragung_nichtAusstehend_nachtragAendertNichts() {
    Entscheidungskette kette =
        paket()
            .kommentar(
                80,
                P,
                uebertragung("u1", "build-dienst", "rot, 2026-11-10T11:00:00Z", "nicht übertragen"),
                um(8))
            .kommentar(
                81, P, nachtrag("Versuch: u1\nBuild-Dienst: gruen, 2026-11-10T13:00:00Z\n"), um(9))
            .rechne();

    Uebertragung u = (Uebertragung) einziges(kette, Gliedart.UEBERTRAGUNG).einzelheiten();
    assertThat(u.buildDienst().wert()).isEqualTo("rot");
  }

  @Test
  void uebertragung_spaetererErfolgVerdecktKeinenFehlschlag() {
    Entscheidungskette kette =
        paket()
            .kommentar(
                80,
                P,
                uebertragung("u1", "build-dienst", "rot, 2026-11-10T11:00:00Z", "nicht übertragen"),
                um(8))
            .kommentar(
                81,
                P,
                uebertragung("u2", "build-dienst", "gruen, 2026-11-10T12:00:00Z", "übertragen"),
                um(9))
            .kommentar(vonHand(82, "## Push to main", um(10)))
            .rechne();

    List<Glied> versuche = glieder(kette, Gliedart.UEBERTRAGUNG);
    assertThat(versuche).hasSize(3);
    assertThat(versuche.subList(0, 2))
        .extracting(Glied::ergebnis)
        .containsExactly("nicht übertragen", "übertragen");
    assertThat(versuche.subList(0, 2))
        .extracting(g -> ((Uebertragung) g.einzelheiten()).buildDienst().wert())
        .containsExactly("rot", "gruen");
    assertThat(versuche.subList(0, 2))
        .extracting(g -> ((Uebertragung) g.einzelheiten()).commit())
        .containsExactly("c-u1", "c-u2");
    assertThat(versuche.get(2).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(versuche.get(2).hinweise()).containsExactly(Hinweis.VON_HAND_GEAENDERT);
  }

  // --- Veröffentlichung (E15) ------------------------------------------------------------------

  @Test
  void veroeffentlichung_jeVersuchMitVersionUndAusgangWoertlich() {
    Entscheidungskette kette =
        paket()
            .kommentar(90, P, veroeffentlichung("3.18.0", "Pull Request angelegt"), um(10))
            .kommentar(91, P, veroeffentlichung("3.18.1", "not released"), um(11))
            .kommentar(vonHand(92, "## Release", um(12)))
            .rechne();

    List<Glied> versuche = glieder(kette, Gliedart.VEROEFFENTLICHUNG);
    assertThat(versuche).hasSize(3);
    Glied erstes = versuche.get(0);
    assertThat(erstes.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(erstes.ergebnis()).isEqualTo("Pull Request angelegt");
    assertThat(erstes.freigegebenVon()).isEqualTo(Akteur.menschGenannt("Erika Muster"));
    assertThat(erstes.ausgefuehrtVon()).isEqualTo(Akteur.modell("claude-opus-5-5"));
    assertThat(erstes.zeitpunkt()).isEqualTo(Instant.parse("2026-11-10T12:30:00Z"));
    assertThat(erstes.quelle()).isEqualTo(new Verweis(3L, 30, null, true, 90L));
    Veroeffentlichung v = (Veroeffentlichung) erstes.einzelheiten();
    assertThat(v.version()).isEqualTo("3.18.0");
    assertThat(v.veroeffentlicht()).isFalse();
    assertThat(versuche.get(1).ergebnis()).isEqualTo("not released");
    assertThat(((Veroeffentlichung) versuche.get(1).einzelheiten()).veroeffentlicht()).isFalse();
    assertThat(versuche.get(2).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(versuche.get(2).hinweise()).containsExactly(Hinweis.VON_HAND_GEAENDERT);
  }

  @Test
  void veroeffentlichung_andererAusgangGiltAlsVeroeffentlicht_ohneAusgangNicht() {
    Veroeffentlichung anderer =
        new Veroeffentlichung(
            null, "1", null, null, "veröffentlicht", null, Angabe.LEER, Angabe.LEER);
    Veroeffentlichung ohne =
        new Veroeffentlichung(null, "1", null, null, null, null, Angabe.LEER, Angabe.LEER);

    assertThat(anderer.veroeffentlicht()).isTrue();
    assertThat(ohne.veroeffentlicht()).isFalse();
  }
}
