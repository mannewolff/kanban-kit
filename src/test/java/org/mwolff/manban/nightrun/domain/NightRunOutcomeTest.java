package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Der Maßstab „nicht vollständig gelungen" (Issue #1078, Plan #1072 E2, AK 5), um die Stillefrist
 * erweitert (Issue #1091, AK 6/8 der fachlichen Quelle #1086).
 *
 * <p>Bis hierher lebte er im Browser ({@code frontend/src/lib/leitstand.ts}). Der
 * Plattform-Leitstand geht über alle Projekte und kann die Läufe nicht einzeln im Browser auswerten
 * — deshalb eine Wahrheit, und die liegt im Server.
 *
 * <p>Die Reihenfolge der Regeln trägt eine Aussage und wird hier Fall für Fall festgehalten: die
 * Stillefrist schlägt alles, danach „läuft noch", danach der Lauf ohne Arbeit, danach rot vor gelb
 * vor grau-mit-Fehlerklasse.
 *
 * <p>Seit Issue #1121 zerfällt der Lauf ohne Arbeit in zwei Fälle: der <b>gemeldete</b> Grund ist
 * der eigene Ausgang {@link NightRunOutcome.Verdict#NO_WORK}, der Rückfall {@link
 * NightRunOutcome#GRUND_UNBEKANNT} bleibt {@code FAILED}.
 */
class NightRunOutcomeTest {

  private static final Instant FIXED = Instant.parse("2026-09-19T22:00:00Z");

  private static final Duration FRIST = Duration.ofMinutes(90);

  /** Ein Grund, wie der Runner ihn meldet (Kit #744) — im Unterschied zum Rückfall des Servers. */
  private static final String GEMELDET = "Ready ist leer — nichts zu tun.";

  /**
   * Die Fälle ohne Zeitbezug messen an einem frischen Lebenszeichen: {@code updatedAt} ist {@code
   * jetzt}, die Frist greift dort nie. So bleiben die Aussagen der Fälle vor Issue #1091
   * unverändert.
   */
  private static NightRunOutcome befund(
      boolean complete, @Nullable String noWorkReason, List<NightRunItem> items) {
    return befund(complete, noWorkReason, NightRunMode.IMPLEMENTATION, items);
  }

  /** Derselbe Fall mit einer anderen Laufart — sie entscheidet unter gleichrangigen Paketen. */
  private static NightRunOutcome befund(
      boolean complete,
      @Nullable String noWorkReason,
      NightRunMode mode,
      List<NightRunItem> items) {
    return NightRunOutcome.of(complete, noWorkReason, mode, items, FIXED, FIXED, FIXED, FRIST);
  }

  private static NightRunItem item(
      int cardNumber, NightRunState state, @Nullable NightRunErrorClass errorClass) {
    return new NightRunItem(
        1L,
        7L,
        3L,
        FIXED,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        cardNumber,
        "Paket " + cardNumber,
        state,
        errorClass,
        1000L,
        null,
        null,
        null,
        List.of());
  }

  @Test
  void einUnabgeschlossenerLaufLaeuftNoch() {
    var outcome = befund(false, null, List.of(item(1, NightRunState.GREEN, null)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.isDisruption()).isFalse();
  }

  /** „Läuft noch" schlägt alles andere — auch ein rotes Paket macht den Lauf nicht zur Störung. */
  @Test
  void einUnabgeschlossenerLaufBleibtLaufendTrotzRotemPaket() {
    var outcome =
        befund(false, null, List.of(item(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
  }

  /**
   * Issue #1121: Der <b>gemeldete</b> Grund ist der eigene Ausgang „nichts zu tun" — und keine
   * Störung. Wer ein Projekt nachts bewusst ruhen lässt, räumte sonst jeden Morgen eine Meldung
   * weg.
   */
  @Test
  void einLaufOhneArbeitMitGemeldetemGrundHatNichtsZuTun() {
    var outcome = befund(true, GEMELDET, List.of());

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.NO_WORK);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.noWorkReason()).isEqualTo(GEMELDET);
    assertThat(outcome.isDisruption()).isFalse();
  }

  /**
   * Der Rückfall des Servers ist <b>kein</b> gemeldeter Grund (Issue #1121): Er steht für einen
   * alten Runner, den Upload-Weg oder einen Lauf, der Pakete hatte und keins bearbeitete — das kann
   * ein echtes Problem verdecken und bleibt rot.
   */
  @Test
  void einLaufOhneArbeitOhneGemeldetenGrundIstGescheitert() {
    var outcome = befund(true, NightRunOutcome.GRUND_UNBEKANNT, List.of());

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.noWorkReason()).isEqualTo(NightRunOutcome.GRUND_UNBEKANNT);
    assertThat(outcome.isDisruption()).isTrue();
  }

  /**
   * Der Grund ist der Text selbst; ein Paket wäre daneben eine zweite Begründung. Beide
   * Grunde-Arten stehen hier: Die Rangfolge gilt für den gemeldeten wie für den Rückfall, nur der
   * Ausgang unterscheidet sie (Issue #1121).
   */
  @Test
  void derGrundOhneArbeitSchlaegtEinRotesPaket() {
    var rot = List.of(item(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT));

    var gemeldet = befund(true, GEMELDET, rot);
    var rueckfall = befund(true, NightRunOutcome.GRUND_UNBEKANNT, rot);

    assertThat(gemeldet.verdict()).isEqualTo(NightRunOutcome.Verdict.NO_WORK);
    assertThat(gemeldet.decisiveItem()).isNull();
    assertThat(gemeldet.noWorkReason()).isEqualTo(GEMELDET);
    assertThat(rueckfall.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(rueckfall.decisiveItem()).isNull();
  }

  /** „Läuft noch" schlägt auch den gemeldeten Grund — der Ausgang steht erst am Ende fest. */
  @Test
  void einUnabgeschlossenerLaufLaeuftNochTrotzGemeldetemGrund() {
    var outcome = befund(false, GEMELDET, List.of());

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
    assertThat(outcome.noWorkReason()).isNull();
  }

  @Test
  void einLeererGrundZaehltNichtAlsLaufOhneArbeit() {
    var outcome = befund(true, "   ", List.of(item(1, NightRunState.GREEN, null)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.SUCCEEDED);
    assertThat(outcome.noWorkReason()).isNull();
  }

  @Test
  void einRotesPaketLaesstDenLaufScheitern() {
    var outcome =
        befund(true, null, List.of(item(4, NightRunState.RED, NightRunErrorClass.CHECKS_RED)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem())
        .isEqualTo(
            new NightRunOutcome.DecisiveItem(4, NightRunState.RED, NightRunErrorClass.CHECKS_RED));
  }

  @Test
  void einGelbesPaketLaesstDenLaufScheitern() {
    var outcome =
        befund(true, null, List.of(item(5, NightRunState.YELLOW, NightRunErrorClass.CHECKS_RED)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(5);
  }

  /** Zurückgestellt ist kein Scheitern, aber auch kein Gelingen — es wartet auf etwas. */
  @Test
  void einGrauesPaketMitFehlerklasseWartet() {
    var outcome =
        befund(
            true, null, List.of(item(6, NightRunState.GREY, NightRunErrorClass.DEPENDENCY_UNMET)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.WAITING);
    assertThat(outcome.decisiveItem().errorClass()).isEqualTo(NightRunErrorClass.DEPENDENCY_UNMET);
    assertThat(outcome.isDisruption()).isTrue();
  }

  /** Grau ohne Fehlerklasse ist ein übergangenes Paket — der Lauf hat es nicht angefasst. */
  @Test
  void grauOhneFehlerklasseIstKeineStoerung() {
    var outcome =
        befund(
            true,
            null,
            List.of(item(1, NightRunState.GREEN, null), item(2, NightRunState.GREY, null)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.SUCCEEDED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.isDisruption()).isFalse();
  }

  @Test
  void einLaufGanzOhnePaketIstGelungen() {
    var outcome = befund(true, null, List.of());

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.SUCCEEDED);
    assertThat(outcome.decisiveItem()).isNull();
  }

  @Test
  void rotSchlaegtGelb() {
    var outcome =
        befund(
            true,
            null,
            List.of(
                item(1, NightRunState.YELLOW, NightRunErrorClass.CHECKS_RED),
                item(2, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(2);
    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
  }

  @Test
  void gelbSchlaegtGrauMitFehlerklasse() {
    var outcome =
        befund(
            true,
            null,
            List.of(
                item(1, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION),
                item(2, NightRunState.YELLOW, NightRunErrorClass.CHECKS_RED)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(2);
    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
  }

  /** Innerhalb einer Farbe entscheidet die Laufreihenfolge, nicht die Kartennummer. */
  @Test
  void innerhalbEinerFarbeGiltDasErsteInLaufreihenfolge() {
    var outcome =
        befund(
            true,
            null,
            List.of(
                item(9, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                item(2, NightRunState.RED, NightRunErrorClass.CHECKS_RED)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(9);
  }

  // --- Die abgebrochene Kette (Issue #1123) -------------------------------------------------

  /**
   * Der Anlass: Die Kette zu #993 riss in der Runde zu Paket #1112. Beide Einheiten sind rot und
   * gleichrangig, die Ketten-Einheit steht zuerst — sie hat ihren Abbruch aber nur geerbt („harter
   * Stopp in der Runde zu Paket #1112"). Maßgeblich ist das Paket, an dem die Kette tatsächlich
   * riss.
   */
  @Test
  void beiEinerKetteGiltDasLetzteGleichrangigePaket() {
    var outcome =
        befund(
            true,
            null,
            NightRunMode.CHAIN,
            List.of(
                item(993, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                item(1112, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(1112);
    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
  }

  /**
   * Dieselben Pakete in einem Implementierungs-Lauf: Dort gibt es keine erbende Ketten-Einheit, und
   * das erste in Laufreihenfolge bleibt maßgeblich (Issue #1078, unverändert).
   */
  @Test
  void ausserhalbEinerKetteBleibtDasErsteGleichrangigePaketMassgeblich() {
    var outcome =
        befund(
            true,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(
                item(993, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                item(1112, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(993);
  }

  /**
   * Riss die Kette schon in der Planung, ist die Ketten-Einheit das einzige rote Paket — die
   * umgekehrte Reihenfolge wählt dann sie, und der Befund zeigt auf die Kette selbst.
   */
  @Test
  void eineInDerPlanungGerisseneKetteBleibtBeiDerKettenEinheit() {
    var outcome =
        befund(
            true,
            null,
            NightRunMode.CHAIN,
            List.of(
                item(993, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                item(1112, NightRunState.GREEN, null)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(993);
  }

  /**
   * Die Laufart dreht nur die Reihenfolge, nicht die Rangfolge: Rot schlägt Gelb schlägt
   * Grau-mit-Fehlerklasse auch in einer Kette — sonst wäre das zuletzt gelaufene Paket maßgeblich
   * statt das schwerste.
   */
  @Test
  void inEinerKetteEntscheidetDerRangWeiterVorDerReihenfolge() {
    var rotVorGelb =
        befund(
            true,
            null,
            NightRunMode.CHAIN,
            List.of(
                item(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                item(2, NightRunState.YELLOW, NightRunErrorClass.CHECKS_RED)));
    var gelbVorGrau =
        befund(
            true,
            null,
            NightRunMode.CHAIN,
            List.of(
                item(3, NightRunState.YELLOW, NightRunErrorClass.CHECKS_RED),
                item(4, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION)));

    assertThat(rotVorGelb.decisiveItem().cardNumber()).isEqualTo(1);
    assertThat(gelbVorGrau.decisiveItem().cardNumber()).isEqualTo(3);
  }

  // --- Stillefrist (Issue #1091, AK 6/8 der fachlichen Quelle #1086) -------------------------

  /**
   * Gemessen wird am letzten Lebenszeichen, nicht am Start: Der Lauf läuft seit vier Stunden — weit
   * über der Frist —, hat aber vor einer Minute gemeldet. Ein langer Lauf, der sich regelmäßig
   * meldet, ist genau der Normalfall einer Nacht.
   */
  @Test
  void einFrischesLebenszeichenHaeltDenLaufLaufend() {
    var outcome =
        NightRunOutcome.of(
            false,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(),
            FIXED,
            FIXED.plus(Duration.ofHours(4)),
            FIXED.plus(Duration.ofHours(4)).plus(Duration.ofMinutes(1)),
            FRIST);

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
    assertThat(outcome.isDisruption()).isFalse();
  }

  /** Genau auf der Frist ist der Lauf noch nicht tot — erst darüber. */
  @Test
  void genauAufDerFristLaeuftDerLaufNoch() {
    var outcome =
        NightRunOutcome.of(
            false,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(),
            FIXED,
            FIXED,
            FIXED.plus(FRIST),
            FRIST);

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
  }

  @Test
  void eineSekundeUeberDerFristIstDerLaufGescheitert() {
    var outcome =
        NightRunOutcome.of(
            false,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(),
            FIXED,
            FIXED,
            FIXED.plus(FRIST).plusSeconds(1),
            FRIST);

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.noWorkReason()).isNull();
    assertThat(outcome.isDisruption()).isTrue();
  }

  /**
   * Ohne Lebenszeichen zählt der Startzeitpunkt — der Upload-Weg lässt {@code updatedAt} bewusst
   * leer. Beide Richtungen stehen hier: Ein Lauf, der {@code startedAt} ignorierte, wäre in der
   * einen Richtung nie und in der anderen immer tot.
   */
  @Test
  void ohneLebenszeichenZaehltDerStartzeitpunkt() {
    var innerhalb =
        NightRunOutcome.of(
            false,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(),
            FIXED,
            null,
            FIXED.plus(FRIST),
            FRIST);
    var darueber =
        NightRunOutcome.of(
            false,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(),
            FIXED,
            null,
            FIXED.plus(FRIST).plusSeconds(1),
            FRIST);

    assertThat(innerhalb.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
    assertThat(darueber.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
  }

  /**
   * Der abgeschlossene Lauf hat sein Ergebnis gemeldet; danach schweigt er zu Recht. Die Frist
   * fragt nur, ob ein <em>unfertiger</em> Lauf noch lebt.
   */
  @Test
  void einAbgeschlossenerLaufBleibtVonDerFristUnberuehrt() {
    var outcome =
        NightRunOutcome.of(
            true,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(item(1, NightRunState.GREEN, null)),
            FIXED,
            FIXED,
            FIXED.plus(Duration.ofDays(30)),
            FRIST);

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.SUCCEEDED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.isDisruption()).isFalse();
  }

  /**
   * Die Frist schlägt die Paketauswahl <em>und</em> den Grund ohne Arbeit: Ein verstummter Lauf hat
   * sein Ergebnis nie gemeldet, also trägt sein Befund auch keines.
   */
  @Test
  void beiEinemVerstummtenLaufSchlaegtDieFristPaketUndGrund() {
    var outcome =
        NightRunOutcome.of(
            false,
            "Ready war leer",
            NightRunMode.IMPLEMENTATION,
            List.of(item(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT)),
            FIXED,
            FIXED,
            FIXED.plus(Duration.ofHours(4)),
            FRIST);

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.noWorkReason()).isNull();
  }
}
