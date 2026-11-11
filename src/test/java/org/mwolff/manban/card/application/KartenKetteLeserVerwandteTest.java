package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.FREMD;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.imProjekt;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.neu;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.card.application.KartenKetteLeser.GliedZustand;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenGlied;

/**
 * Vorfahren und Nachfahren der Lese-Fassade (Issue #2114, Plan #2108 E12) gegen einen Bestand im
 * Speicher: wie die Kette aufsteigt und absteigt und was an den Grenzen des Projekts, im Papierkorb
 * und nach dem endgültigen Löschen sichtbar bleibt. Die Abfragen belegt {@code KartenKetteLeserIT}.
 */
class KartenKetteLeserVerwandteTest {

  private final KartenKetteLeserAufbau bau = new KartenKetteLeserAufbau();

  // --- Vorfahren über die Herkunft ------------------------------------------------------------

  @Test
  void vorfahrenStehenNaechsterZuerst() {
    long fachplan = bau.lege(neu(1, "Fachplan", null, null));
    long plan = bau.lege(neu(2, "Plan", null, fachplan));
    long paket = bau.lege(neu(3, "Paket", null, plan));

    assertThat(bau.leser.lade(paket).vorfahren())
        .extracting(KettenGlied::zustand, KettenGlied::nummer)
        .containsExactly(tuple(GliedZustand.VORHANDEN, 2), tuple(GliedZustand.VORHANDEN, 1));
  }

  @Test
  void dieHerkunftGehtDenZeilenVor() {
    long plan = bau.lege(neu(2, "Plan", null, null));
    long andere = bau.lege(neu(5, "Andere", null, null));
    long paket = bau.lege(neu(3, "Paket", "Plan: Issue #5", plan));

    assertThat(bau.leser.lade(paket).vorfahren())
        .extracting(KettenGlied::nummer)
        .containsExactly(2);
    assertThat(andere).isNotZero();
  }

  @Test
  void einVorfahrInAnderemProjektNenntNurDieseTatsacheUndBeendetDieKette() {
    long weiter = bau.lege(neu(8, "Dahinter", null, null));
    long fremd = bau.lege(imProjekt(neu(9, "Geheim", "Geheim", weiter), FREMD));
    long paket = bau.lege(neu(3, "Paket", null, fremd));

    assertThat(bau.leser.lade(paket).vorfahren())
        .containsExactly(new KettenGlied(GliedZustand.ANDERES_PROJEKT, null, null));
  }

  @Test
  void einVorfahrImPapierkorbNenntNurDieNummerUndDieKetteGehtWeiter() {
    long fachplan = bau.lege(neu(1, "Fachplan", null, null));
    long plan = bau.lege(neu(2, "Plan", null, fachplan), true, null);
    long paket = bau.lege(neu(3, "Paket", null, plan));

    List<KettenGlied> vorfahren = bau.leser.lade(paket).vorfahren();

    assertThat(vorfahren.getFirst()).isEqualTo(new KettenGlied(GliedZustand.PAPIERKORB, 2, null));
    assertThat(vorfahren.get(1).nummer()).isEqualTo(1);
    assertThat(vorfahren).hasSize(2);
  }

  @Test
  void einEndgueltigGeloeschterVorfahrNenntDieBewahrteNummer() {
    long paket = bau.lege(neu(3, "Paket", null, null), false, 2);

    assertThat(bau.leser.lade(paket).vorfahren())
        .containsExactly(new KettenGlied(GliedZustand.GELOESCHT, 2, null));
  }

  @Test
  void einVerschwundenerVorfahrWirdAusgelassen() {
    long paket = bau.lege(neu(3, "Paket", null, 404L));

    assertThat(bau.leser.lade(paket).vorfahren()).isEmpty();
  }

  @Test
  void einRingInDerHerkunftEndet() {
    long karteA = bau.lege(neu(1, "A", null, 101L)); // 101: die ID von B, angelegt als Zweites
    bau.lege(neu(2, "B", null, karteA));

    assertThat(bau.leser.lade(karteA).vorfahren())
        .extracting(KettenGlied::nummer)
        .containsExactly(2);
  }

  // --- Vorfahren über die Zeilen --------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Plan: Issue #2",
        "Plan: #2",
        "  Plan:\t Issue  #2",
        "Fachliche Quelle: Issue #2",
        "Requirement: Issue #2",
        "Kopf\nPlan: Issue #2\nFuss"
      })
  void dieZeilenGeltenWennDieHerkunftFehlt(String beschreibung) {
    bau.lege(neu(2, "Plan", null, null));
    long paket = bau.lege(neu(3, "Paket", beschreibung, null));

    assertThat(bau.leser.lade(paket).vorfahren())
        .extracting(KettenGlied::nummer)
        .containsExactly(2);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Der Plan: Issue #2",
        "Plan: Issue 2",
        "Plan-Modell: #2",
        "Plan: Issue #12345678901",
        "Plan: siehe #2"
      })
  void andereZeilenNennenKeinenVorfahren(String beschreibung) {
    bau.lege(neu(2, "Plan", null, null));
    long paket = bau.lege(neu(3, "Paket", beschreibung, null));

    assertThat(bau.leser.lade(paket).vorfahren()).isEmpty();
  }

  @Test
  void beideZeilenGeltenUndDerPlanStehtVorDerQuelleAuchInUmgekehrterSchrift() {
    bau.lege(neu(1, "Fachplan", null, null));
    bau.lege(neu(2, "Plan", null, null));
    long paket = bau.lege(neu(3, "Paket", "Fachliche Quelle: Issue #1\nPlan: Issue #2", null));

    assertThat(bau.leser.lade(paket).vorfahren())
        .extracting(KettenGlied::nummer)
        .containsExactly(2, 1);
  }

  @Test
  void eineZeileZaehltNurEinmalWennAuchDerPlanSieNennt() {
    bau.lege(neu(1, "Fachplan", null, null));
    bau.lege(neu(2, "Plan", "Fachliche Quelle: Issue #1", null));
    long paket = bau.lege(neu(3, "Paket", "Plan: Issue #2\nFachliche Quelle: Issue #1", null));

    assertThat(bau.leser.lade(paket).vorfahren())
        .extracting(KettenGlied::nummer)
        .containsExactly(2, 1);
  }

  @Test
  void eineGenannteNummerOhneKarteStehtAlsGeloescht() {
    long paket = bau.lege(neu(3, "Paket", "Plan: Issue #2", null));

    assertThat(bau.leser.lade(paket).vorfahren())
        .containsExactly(new KettenGlied(GliedZustand.GELOESCHT, 2, null));
  }

  @Test
  void ueberEinemGeloeschtenVorfahrenStehenDieHoeherenGliederAusDenZeilen() {
    bau.lege(neu(1, "Fachplan", null, null));
    long paket =
        bau.lege(neu(3, "Paket", "Plan: Issue #2\nFachliche Quelle: Issue #1", null), false, 2);

    List<KettenGlied> vorfahren = bau.leser.lade(paket).vorfahren();

    assertThat(vorfahren)
        .extracting(KettenGlied::zustand, KettenGlied::nummer)
        .containsExactly(tuple(GliedZustand.GELOESCHT, 2), tuple(GliedZustand.VORHANDEN, 1));
  }

  @Test
  void eineBewahrteNummerDieSchonInDerKetteStehtZaehltNurEinmal() {
    bau.lege(neu(2, "Plan", null, null), false, 1);
    long paket = bau.lege(neu(3, "Paket", "Plan: Issue #2\nFachliche Quelle: Issue #1", null));

    assertThat(bau.leser.lade(paket).vorfahren())
        .extracting(KettenGlied::zustand, KettenGlied::nummer)
        .containsExactly(tuple(GliedZustand.VORHANDEN, 2), tuple(GliedZustand.GELOESCHT, 1));
  }

  @Test
  void eineKarteAusAnderemProjektWirdUeberDieZeilenNichtGefunden() {
    bau.lege(imProjekt(neu(2, "Geheim", null, null), FREMD));
    long paket = bau.lege(neu(3, "Paket", "Plan: Issue #2", null));

    assertThat(bau.leser.lade(paket).vorfahren())
        .containsExactly(new KettenGlied(GliedZustand.GELOESCHT, 2, null));
  }

  @Test
  void eineKarteImPapierkorbTraegtIhreZeilenWeiter() {
    bau.lege(neu(1, "Fachplan", null, null));
    bau.lege(neu(2, "Plan", "Fachliche Quelle: Issue #1", null), true, null);
    long paket = bau.lege(neu(3, "Paket", "Plan: Issue #2", null));

    assertThat(bau.leser.lade(paket).vorfahren())
        .extracting(KettenGlied::zustand, KettenGlied::nummer)
        .containsExactly(tuple(GliedZustand.PAPIERKORB, 2), tuple(GliedZustand.VORHANDEN, 1));
  }

  @Test
  void einRingUeberDieZeilenEndet() {
    long karteA = bau.lege(neu(1, "A", "Plan: Issue #2", null));
    bau.lege(neu(2, "B", "Plan: Issue #1", null));

    assertThat(bau.leser.lade(karteA).vorfahren())
        .extracting(KettenGlied::nummer)
        .containsExactly(2);
  }

  @Test
  void eineKarteNenntSichNichtSelbstAlsVorfahr() {
    long karteA = bau.lege(neu(1, "A", "Plan: Issue #1", null));

    assertThat(bau.leser.lade(karteA).vorfahren()).isEmpty();
  }

  // --- Nachfahren -----------------------------------------------------------------------------

  @Test
  void nachfahrenStehenStufenweiseUndJeStufeNachNummer() {
    long plan = bau.lege(neu(1, "Plan", null, null));
    long zweites = bau.lege(neu(5, "Paket 5", null, plan));
    bau.lege(neu(4, "Paket 4", null, plan));
    bau.lege(neu(6, "Folge", null, zweites));

    assertThat(bau.leser.lade(plan).nachfahren())
        .extracting(KettenGlied::nummer)
        .containsExactly(4, 5, 6);
  }

  @Test
  void einNachfahrImPapierkorbNenntNurDieNummerUndDieKetteGehtWeiter() {
    long plan = bau.lege(neu(1, "Plan", null, null));
    long kind = bau.lege(neu(4, "Kind", null, plan), true, null);
    bau.lege(neu(6, "Enkel", null, kind));

    List<KettenGlied> nachfahren = bau.leser.lade(plan).nachfahren();

    assertThat(nachfahren.getFirst()).isEqualTo(new KettenGlied(GliedZustand.PAPIERKORB, 4, null));
    assertThat(nachfahren.get(1).nummer()).isEqualTo(6);
    assertThat(nachfahren).hasSize(2);
  }

  @Test
  void einNachfahrInAnderemProjektNenntNurDieseTatsacheUndEndet() {
    long plan = bau.lege(neu(1, "Plan", null, null));
    long kind = bau.lege(imProjekt(neu(4, "Geheim", null, plan), FREMD));
    bau.lege(imProjekt(neu(6, "Enkel", null, kind), FREMD));

    assertThat(bau.leser.lade(plan).nachfahren())
        .containsExactly(new KettenGlied(GliedZustand.ANDERES_PROJEKT, null, null));
  }

  @Test
  void einRingBeiDenNachfahrenEndet() {
    long karteA = bau.lege(neu(1, "A", null, 101L)); // 101: die ID von B, angelegt als Zweites
    bau.lege(neu(2, "B", null, karteA));

    assertThat(bau.leser.lade(karteA).nachfahren())
        .extracting(KettenGlied::nummer)
        .containsExactly(2);
  }
}
