package org.mwolff.manban.deployment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.Wochenfenster;
import org.mwolff.manban.deployment.domain.Auslieferungskennzahlen.Woche;

/** Die Rechnung der Platte „Auslieferung“ (Issue #1832, Plan #1831 E6, E8–E11). */
class AuslieferungsErmittlungTest {

  /** Ein Donnerstag. */
  private static final Instant JETZT = Instant.parse("2026-10-15T12:00:00Z");

  private static final long STUNDE = Duration.ofHours(1).toSeconds();
  private static final long TAG = Duration.ofDays(1).toSeconds();

  private static Instant vor(Duration abstand) {
    return JETZT.minus(abstand);
  }

  private static Instant tageZurueck(int tage) {
    return vor(Duration.ofDays(tage));
  }

  private record Commit(String sha, Instant committedAt) {}

  private static Commit commit(String sha, Instant committedAt) {
    return new Commit(sha, committedAt);
  }

  /** Die Eingaben einer Lage; die interne Kennung steigt mit dem Eingang. */
  private static final class Lage {
    private final List<Auslieferungsversuch> versuche = new ArrayList<>();
    private final List<GemeldeterCommit> commits = new ArrayList<>();

    Lage erfolg(Instant finishedAt, Commit... gemeldet) {
      return versuch(Ausgang.SUCCESS, finishedAt, gemeldet);
    }

    Lage fehlschlag(Instant finishedAt, Commit... gemeldet) {
      return versuch(Ausgang.FAILURE, finishedAt, gemeldet);
    }

    private Lage versuch(Ausgang ausgang, Instant finishedAt, Commit... gemeldet) {
      long id = versuche.size() + 1L;
      versuche.add(new Auslieferungsversuch(id, "lauf-" + id, ausgang, finishedAt, "rev-" + id));
      for (Commit c : gemeldet) {
        commits.add(new GemeldeterCommit(id, c.sha(), c.committedAt()));
      }
      return this;
    }

    Lage fremderCommit(long versuchId, Commit c) {
      commits.add(new GemeldeterCommit(versuchId, c.sha(), c.committedAt()));
      return this;
    }

    Auslieferungskennzahlen ermittle() {
      return AuslieferungsErmittlung.ermittle(versuche, commits, JETZT);
    }

    Auslieferungskennzahlen ermittleVertauscht() {
      return AuslieferungsErmittlung.ermittle(versuche.reversed(), commits.reversed(), JETZT);
    }
  }

  @Test
  void leadTime_istDerMedianAusDreiZweiUndEinemTag() {
    Instant donnerstag = vor(Duration.ofHours(1));
    Auslieferungskennzahlen k =
        new Lage()
            .erfolg(
                donnerstag,
                commit("mo", donnerstag.minus(Duration.ofDays(3))),
                commit("di", donnerstag.minus(Duration.ofDays(2))),
                commit("mi", donnerstag.minus(Duration.ofDays(1))))
            .ermittle();

    assertThat(k.deploymentCount()).isEqualTo(1);
    assertThat(k.leadTimeMedianSeconds()).isEqualTo(2 * TAG);
  }

  @Test
  void fehlschlagMitErfolgZweiStundenSpaeter_ergibtFuenfzigProzentUndZweiStunden() {
    Instant scheitern = tageZurueck(2);
    Auslieferungskennzahlen k =
        new Lage().fehlschlag(scheitern).erfolg(scheitern.plus(Duration.ofHours(2))).ermittle();

    assertThat(k.changeFailurePercent()).isEqualTo(50);
    assertThat(k.timeToRestoreMedianSeconds()).isEqualTo(2 * STUNDE);
    assertThat(k.outageSince()).isNull();
    assertThat(k.deploymentCount()).isEqualTo(1);
  }

  @Test
  void laufenderAusfall_stehtInOutageSince_undZaehltNichtImMedian() {
    Instant frueheresScheitern = tageZurueck(5);
    Auslieferungskennzahlen k =
        new Lage()
            .fehlschlag(frueheresScheitern)
            .erfolg(frueheresScheitern.plus(Duration.ofHours(1)))
            .erfolg(tageZurueck(1))
            .fehlschlag(vor(Duration.ofHours(3)))
            .ermittle();

    assertThat(k.outageSince()).isEqualTo(vor(Duration.ofHours(3)));
    assertThat(k.timeToRestoreMedianSeconds()).isEqualTo(STUNDE);
  }

  @Test
  void laufenderAusfall_ausMehrerenFehlschlaegen_beginntBeimErsten() {
    Auslieferungskennzahlen k =
        new Lage()
            .fehlschlag(vor(Duration.ofHours(3)))
            .fehlschlag(vor(Duration.ofHours(1)))
            .ermittle();

    assertThat(k.outageSince()).isEqualTo(vor(Duration.ofHours(3)));
    assertThat(k.timeToRestoreMedianSeconds()).isNull();
  }

  @Test
  void commitsEinesGescheitertenVersuchs_zaehlenBeimNaechstenErfolgGenauEinmal() {
    Instant scheitern = tageZurueck(2);
    Auslieferungskennzahlen k =
        new Lage()
            // ein früherer Erfolg liefert den später gemeldeten Commit nicht aus
            .erfolg(tageZurueck(10))
            .fehlschlag(scheitern, commit("a", scheitern.minus(Duration.ofDays(1))))
            // der nächste Erfolg meldet a nicht erneut
            .erfolg(
                scheitern.plus(Duration.ofHours(1)),
                commit("b", scheitern.plus(Duration.ofMinutes(30))))
            // ein späterer Erfolg meldet a noch einmal; a zählt trotzdem nur einmal
            .erfolg(
                scheitern.plus(Duration.ofHours(2)),
                commit("a", scheitern.minus(Duration.ofDays(1))))
            .ermittle();

    long leadA = TAG + STUNDE;
    long leadB = Duration.ofMinutes(30).toSeconds();
    assertThat(k.leadTimeMedianSeconds()).isEqualTo((leadA + leadB) / 2);
  }

  @Test
  void commitMehrererVersuche_haengtAmVersuchMitDemFruehestenFinishedAt() {
    Instant t = tageZurueck(3);
    Auslieferungskennzahlen k =
        new Lage()
            // zuerst eingegangen, aber später beendet
            .erfolg(t.plus(Duration.ofHours(1)), commit("x", t.minus(Duration.ofHours(5))))
            .erfolg(t, commit("x", t.minus(Duration.ofHours(2))))
            .ermittle();

    assertThat(k.leadTimeMedianSeconds()).isEqualTo(2 * STUNDE);
  }

  @Test
  void nichtGemeldeterVersuch_zaehltNicht_seineCommitsZaehlenBeimNaechstenGemeldetenErfolg() {
    Instant erster = tageZurueck(4);
    Instant naechster = tageZurueck(2);
    // Der Versuch am Tag 3 meldete nichts; der nächste gemeldete Erfolg nennt seine Commits mit.
    Auslieferungskennzahlen k =
        new Lage()
            .erfolg(erster, commit("a", erster.minus(Duration.ofHours(1))))
            .erfolg(
                naechster,
                commit("b", tageZurueck(3).minus(Duration.ofHours(1))),
                commit("c", naechster.minus(Duration.ofHours(1))))
            .ermittle();

    assertThat(k.deploymentCount()).isEqualTo(2);
    assertThat(k.changeFailurePercent()).isZero();
    assertThat(k.leadTimeMedianSeconds()).isEqualTo(STUNDE);
  }

  @Test
  void aufeinanderfolgendeFehlschlaege_sindEinAusfall() {
    Instant t = tageZurueck(6);
    Auslieferungskennzahlen k =
        new Lage()
            .fehlschlag(t)
            .fehlschlag(t.plus(Duration.ofHours(1)))
            .erfolg(t.plus(Duration.ofHours(3)))
            .ermittle();

    assertThat(k.timeToRestoreMedianSeconds()).isEqualTo(3 * STUNDE);
    assertThat(k.changeFailurePercent()).isEqualTo(67);
  }

  @Test
  void changeFailureRate_rundetAufGanzeProzent() {
    Instant t = tageZurueck(6);
    Auslieferungskennzahlen k =
        new Lage().erfolg(t).erfolg(t.plusSeconds(60)).fehlschlag(t.plusSeconds(120)).ermittle();

    assertThat(k.changeFailurePercent()).isEqualTo(33);
  }

  @Test
  void timeToRestore_istDerMedianMehrererAusfaelle() {
    Instant t = tageZurueck(20);
    Auslieferungskennzahlen k =
        new Lage()
            .fehlschlag(t)
            .erfolg(t.plus(Duration.ofHours(1)))
            .fehlschlag(t.plus(Duration.ofDays(1)))
            .erfolg(t.plus(Duration.ofDays(1)).plus(Duration.ofHours(3)))
            .ermittle();

    assertThat(k.timeToRestoreMedianSeconds()).isEqualTo(2 * STUNDE);
  }

  @Test
  void ausfallUeberDieFenstergrenze_zaehltMitVollerDauerImFensterSeinesEndes() {
    Auslieferungskennzahlen k =
        new Lage()
            // endet vor dem Fenster: zählt nicht
            .fehlschlag(tageZurueck(90))
            .erfolg(tageZurueck(89))
            // beginnt vor dem Fenster, endet darin: zählt mit voller Dauer
            .fehlschlag(tageZurueck(85))
            .erfolg(tageZurueck(83))
            .ermittle();

    assertThat(k.timeToRestoreMedianSeconds()).isEqualTo(2 * TAG);
    assertThat(k.changeFailurePercent()).isZero();
    assertThat(k.deploymentCount()).isEqualTo(1);
  }

  @Test
  void fenster_endetGenauZwoelfWochenVorJetzt() {
    Auslieferungskennzahlen k =
        new Lage().erfolg(tageZurueck(84)).erfolg(tageZurueck(84).plusSeconds(1)).ermittle();

    assertThat(k.deploymentCount()).isEqualTo(1);
  }

  @Test
  void vertauschteReihenfolge_ergibtDieselbenKennzahlen() {
    Instant t = tageZurueck(10);
    Lage lage =
        new Lage()
            .erfolg(t.minus(Duration.ofDays(30)), commit("alt", t.minus(Duration.ofDays(31))))
            .fehlschlag(t, commit("a", t.minus(Duration.ofHours(4))))
            // Gleichstand im finishedAt: der zuerst eingegangene gilt als früher
            .erfolg(t, commit("b", t.minus(Duration.ofHours(2))))
            .fehlschlag(t.plus(Duration.ofDays(1)), commit("c", t.plus(Duration.ofHours(20))))
            .erfolg(t.plus(Duration.ofDays(1)).plus(Duration.ofHours(5)), commit("a", t))
            .fehlschlag(vor(Duration.ofHours(2)), commit("d", vor(Duration.ofHours(3))));

    Auslieferungskennzahlen geordnet = lage.ermittle();
    assertThat(lage.ermittleVertauscht()).isEqualTo(geordnet);
    // Ausfälle: 0 s (Gleichstand, Fehlschlag zuerst eingegangen) und 5 Stunden
    assertThat(geordnet.timeToRestoreMedianSeconds()).isEqualTo(5 * STUNDE / 2);
    assertThat(geordnet.outageSince()).isEqualTo(vor(Duration.ofHours(2)));
  }

  @Test
  void committedAtNachDerAuslieferung_ergibtLeadTimeNull() {
    Instant t = tageZurueck(1);
    Auslieferungskennzahlen k =
        new Lage().erfolg(t, commit("spaet", t.plus(Duration.ofMinutes(10)))).ermittle();

    assertThat(k.leadTimeMedianSeconds()).isZero();
  }

  @Test
  void ohneMeldung_istDiePlatteLeer() {
    Auslieferungskennzahlen k = new Lage().ermittle();

    assertThat(k.hasReports()).isFalse();
    assertThat(k.dataSince()).isNull();
    assertThat(k.deploymentCount()).isZero();
    assertThat(k.leadTimeMedianSeconds()).isNull();
    assertThat(k.changeFailurePercent()).isNull();
    assertThat(k.timeToRestoreMedianSeconds()).isNull();
    assertThat(k.outageSince()).isNull();
    assertThat(k.weeks())
        .hasSize(Wochenfenster.ANZAHL)
        .allSatisfy(
            w -> {
              assertThat(w.successCount()).isZero();
              assertThat(w.failureCount()).isZero();
            });
  }

  @Test
  void nurMeldungenVorDemFenster_ergebenEchteNullUndKeineGrundlage() {
    Instant t = tageZurueck(100);
    Auslieferungskennzahlen k =
        new Lage()
            .fehlschlag(t)
            .erfolg(t.plus(Duration.ofHours(1)), commit("a", t.minus(Duration.ofHours(1))))
            .ermittle();

    assertThat(k.hasReports()).isTrue();
    assertThat(k.deploymentCount()).isZero();
    assertThat(k.leadTimeMedianSeconds()).isNull();
    assertThat(k.changeFailurePercent()).isNull();
    assertThat(k.timeToRestoreMedianSeconds()).isNull();
    assertThat(k.outageSince()).isNull();
  }

  @Test
  void nurEinFehlschlag_hatKeineLeadTimeAberEineFehlerrate() {
    Instant t = tageZurueck(1);
    Auslieferungskennzahlen k =
        new Lage().fehlschlag(t, commit("a", t.minus(Duration.ofHours(1)))).ermittle();

    assertThat(k.hasReports()).isTrue();
    assertThat(k.deploymentCount()).isZero();
    assertThat(k.leadTimeMedianSeconds()).isNull();
    assertThat(k.changeFailurePercent()).isEqualTo(100);
    assertThat(k.timeToRestoreMedianSeconds()).isNull();
    assertThat(k.outageSince()).isEqualTo(t);
  }

  @Test
  void commitEinerAuslieferungVorDemFenster_zaehltNichtInDerLeadTime() {
    Instant alt = tageZurueck(90);
    Instant neu = tageZurueck(1);
    Auslieferungskennzahlen k =
        new Lage()
            .erfolg(alt, commit("alt", alt.minus(Duration.ofDays(5))))
            .erfolg(neu, commit("neu", neu.minus(Duration.ofHours(1))))
            .ermittle();

    assertThat(k.leadTimeMedianSeconds()).isEqualTo(STUNDE);
  }

  @Test
  void commitEinesUnbekanntenVersuchs_zaehltNicht() {
    Instant t = tageZurueck(1);
    Auslieferungskennzahlen k =
        new Lage()
            .erfolg(t, commit("a", t.minus(Duration.ofHours(1))))
            .fremderCommit(99, commit("b", t.minus(Duration.ofDays(5))))
            .ermittle();

    assertThat(k.leadTimeMedianSeconds()).isEqualTo(STUNDE);
  }

  @Test
  void dataSince_istDieErsteMeldung_wennSieImFensterLiegt() {
    Auslieferungskennzahlen k =
        new Lage().erfolg(tageZurueck(14)).fehlschlag(tageZurueck(21)).ermittle();

    assertThat(k.dataSince()).isEqualTo(tageZurueck(21));
  }

  @Test
  void dataSince_istLeer_wennDieErsteMeldungVorDemFensterLiegt() {
    Auslieferungskennzahlen k =
        new Lage().erfolg(tageZurueck(100)).erfolg(tageZurueck(1)).ermittle();

    assertThat(k.dataSince()).isNull();
  }

  @Test
  void wochenleiste_zaehltJeFensterErfolgeUndFehlschlaege() {
    Auslieferungskennzahlen k =
        new Lage()
            .erfolg(tageZurueck(100))
            .fehlschlag(tageZurueck(80))
            .erfolg(vor(Duration.ofHours(5)))
            .erfolg(vor(Duration.ofHours(4)))
            .fehlschlag(vor(Duration.ofHours(3)))
            .ermittle();

    assertThat(k.weeks())
        .extracting(Woche::weekStart)
        .containsExactlyElementsOf(
            IntStream.range(0, Wochenfenster.ANZAHL)
                .mapToObj(i -> Wochenfenster.beginn(i, JETZT))
                .toList());
    assertThat(k.weeks().getFirst()).isEqualTo(new Woche(Wochenfenster.beginn(0, JETZT), 0, 1));
    assertThat(k.weeks().getLast())
        .isEqualTo(new Woche(Wochenfenster.beginn(Wochenfenster.ANZAHL - 1, JETZT), 2, 1));
    assertThat(k.weeks().subList(1, Wochenfenster.ANZAHL - 1))
        .allSatisfy(w -> assertThat(w.successCount() + w.failureCount()).isZero());
  }

  @Test
  void wochen_sindUnveraenderlich() {
    Auslieferungskennzahlen k = new Lage().ermittle();

    assertThat(k.weeks()).isUnmodifiable();
  }
}
