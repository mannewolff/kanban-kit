package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Der Maßstab „nicht vollständig gelungen" (Issue #1078, Plan #1072 E2, AK 5).
 *
 * <p>Bis hierher lebte er im Browser ({@code frontend/src/lib/leitstand.ts}). Der
 * Plattform-Leitstand geht über alle Projekte und kann die Läufe nicht einzeln im Browser auswerten
 * — deshalb eine Wahrheit, und die liegt im Server.
 *
 * <p>Die Reihenfolge der Regeln trägt eine Aussage und wird hier Fall für Fall festgehalten: „läuft
 * noch" schlägt alles, danach der Lauf ohne Arbeit, danach rot vor gelb vor grau-mit-Fehlerklasse.
 */
class NightRunOutcomeTest {

  private static final Instant FIXED = Instant.parse("2026-09-19T22:00:00Z");

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
        null);
  }

  @Test
  void einUnabgeschlossenerLaufLaeuftNoch() {
    var outcome = NightRunOutcome.of(false, null, List.of(item(1, NightRunState.GREEN, null)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.isDisruption()).isFalse();
  }

  /** „Läuft noch" schlägt alles andere — auch ein rotes Paket macht den Lauf nicht zur Störung. */
  @Test
  void einUnabgeschlossenerLaufBleibtLaufendTrotzRotemPaket() {
    var outcome =
        NightRunOutcome.of(
            false, null, List.of(item(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
  }

  @Test
  void einLaufOhneArbeitIstGescheitert() {
    var outcome = NightRunOutcome.of(true, "Ready war leer", List.of());

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.noWorkReason()).isEqualTo("Ready war leer");
    assertThat(outcome.isDisruption()).isTrue();
  }

  /** Der Grund ist der Text selbst; ein Paket wäre daneben eine zweite Begründung. */
  @Test
  void derGrundOhneArbeitSchlaegtEinRotesPaket() {
    var outcome =
        NightRunOutcome.of(
            true,
            "Ready war leer",
            List.of(item(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.noWorkReason()).isEqualTo("Ready war leer");
  }

  @Test
  void einLeererGrundZaehltNichtAlsLaufOhneArbeit() {
    var outcome = NightRunOutcome.of(true, "   ", List.of(item(1, NightRunState.GREEN, null)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.SUCCEEDED);
    assertThat(outcome.noWorkReason()).isNull();
  }

  @Test
  void einRotesPaketLaesstDenLaufScheitern() {
    var outcome =
        NightRunOutcome.of(
            true, null, List.of(item(4, NightRunState.RED, NightRunErrorClass.CHECKS_RED)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem())
        .isEqualTo(
            new NightRunOutcome.DecisiveItem(4, NightRunState.RED, NightRunErrorClass.CHECKS_RED));
  }

  @Test
  void einGelbesPaketLaesstDenLaufScheitern() {
    var outcome =
        NightRunOutcome.of(
            true, null, List.of(item(5, NightRunState.YELLOW, NightRunErrorClass.CHECKS_RED)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(5);
  }

  /** Zurückgestellt ist kein Scheitern, aber auch kein Gelingen — es wartet auf etwas. */
  @Test
  void einGrauesPaketMitFehlerklasseWartet() {
    var outcome =
        NightRunOutcome.of(
            true, null, List.of(item(6, NightRunState.GREY, NightRunErrorClass.DEPENDENCY_UNMET)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.WAITING);
    assertThat(outcome.decisiveItem().errorClass()).isEqualTo(NightRunErrorClass.DEPENDENCY_UNMET);
    assertThat(outcome.isDisruption()).isTrue();
  }

  /** Grau ohne Fehlerklasse ist ein übergangenes Paket — der Lauf hat es nicht angefasst. */
  @Test
  void grauOhneFehlerklasseIstKeineStoerung() {
    var outcome =
        NightRunOutcome.of(
            true,
            null,
            List.of(item(1, NightRunState.GREEN, null), item(2, NightRunState.GREY, null)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.SUCCEEDED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.isDisruption()).isFalse();
  }

  @Test
  void einLaufGanzOhnePaketIstGelungen() {
    var outcome = NightRunOutcome.of(true, null, List.of());

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.SUCCEEDED);
    assertThat(outcome.decisiveItem()).isNull();
  }

  @Test
  void rotSchlaegtGelb() {
    var outcome =
        NightRunOutcome.of(
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
        NightRunOutcome.of(
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
        NightRunOutcome.of(
            true,
            null,
            List.of(
                item(9, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                item(2, NightRunState.RED, NightRunErrorClass.CHECKS_RED)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(9);
  }
}
