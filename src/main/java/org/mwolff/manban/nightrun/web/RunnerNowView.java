package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOutcome.Verdict;
import org.mwolff.manban.nightrun.domain.RunnerLage;
import org.mwolff.manban.nightrun.domain.RunnerLage.AktiverLauf;
import org.mwolff.manban.nightrun.domain.RunnerLage.Gesamt;
import org.mwolff.manban.nightrun.domain.RunnerLage.LaufZustand;
import org.mwolff.manban.nightrun.domain.RunnerLage.LetzterLauf;
import org.mwolff.manban.nightrun.domain.RunnerStufe;

/**
 * Antwort von {@code /night-runs/now}: die Lage des Runners (Issue #1727).
 *
 * <p>Eine eigene Datei wie {@link IngestStuckRequest}: Im {@link NightRunNowController} trieben die
 * Sichten die Zahl der Imports über die Grenze von PMD.
 */
@Schema(description = "Was der Runner des Projekts jetzt tut.")
record RunnerNowView(
    @Schema(
            description =
                """
                AKTIV: mindestens ein Lauf läuft oder ist verstummt. KEIN_RUNNER: keiner, \
                aber es gab schon einen. NIE: noch kein Lauf gestartet.""",
            example = "AKTIV")
        Gesamt state,
    @Schema(
            description =
                """
                Die laufenden und verstummten Läufe, jüngster Start zuerst; ein verstummter \
                Lauf, dessen Störung quittiert ist, fehlt.""")
        List<ActiveRunView> runs,
    @Schema(description = "Der zuletzt beendete Lauf; null, wenn keiner beendet ist.")
        @Nullable LastRunView lastRun) {

  static RunnerNowView of(RunnerLage lage) {
    LetzterLauf letzter = lage.letzterLauf();
    return new RunnerNowView(
        lage.gesamt(),
        lage.laeufe().stream().map(ActiveRunView::of).toList(),
        letzter == null ? null : LastRunView.of(letzter));
  }

  /** Ein laufender oder verstummter Lauf. */
  @Schema(description = "Ein Lauf, der läuft oder verstummt ist.")
  record ActiveRunView(
      @Schema(description = "Kennung des Laufs.", example = "312") long runId,
      @Schema(description = "Art des Laufs: CHAIN oder IMPLEMENTATION.", example = "CHAIN")
          NightRunMode mode,
      @Schema(description = "Beginn des Laufs.", example = "2026-10-13T22:39:12Z")
          Instant startedAt,
      @Schema(
              description =
                  "Das Token, unter dem der Lauf gemeldet wurde; null, wenn er keines trägt.",
              example = "runner")
          @Nullable String tokenName,
      @Schema(
              description = "Letztes Lebenszeichen: die letzte Meldung, ohne sie der Beginn.",
              example = "2026-10-13T23:05:00Z")
          Instant lastReportAt,
      @Schema(description = "LAEUFT oder VERSTUMMT.", example = "LAEUFT") LaufZustand state,
      @Schema(description = "Die laufenden Stufen, wie der Runner sie meldet.")
          List<StageView> stages,
      @Schema(
              description =
                  """
                  Nur bei VERSTUMMT: die zuletzt begonnene Stufe; null, wenn der Lauf läuft \
                  oder keine Stufe gemeldet hat.""")
          @Nullable StageView lastStage) {

    static ActiveRunView of(AktiverLauf lauf) {
      RunnerStufe letzte = lauf.letzteStufe();
      return new ActiveRunView(
          lauf.runId(),
          lauf.mode(),
          lauf.startedAt(),
          lauf.tokenName(),
          lauf.letzteMeldung(),
          lauf.zustand(),
          lauf.stufen().stream().map(StageView::of).toList(),
          letzte == null ? null : StageView.of(letzte));
    }
  }

  /** Eine laufende Stufe (Vertrag V1). */
  @Schema(description = "Eine Stufe, die der Lauf gerade fährt.")
  record StageView(
      @Schema(description = "Die Karte, an der die Stufe arbeitet.", example = "1665")
          int cardNumber,
      @Schema(
              description =
                  """
                  Die Stufe: heute PLAN, REVIEW, PAKETE, ABDECKUNG, UMSETZUNG oder \
                  VORBEREITUNG; ein anderer Wert wird wörtlich durchgereicht.""",
              example = "VORBEREITUNG")
          String stage,
      @Schema(description = "Beginn der Stufe.", example = "2026-10-13T22:41:07Z")
          Instant startedAt,
      @Schema(
              description = "Ende der Frist der Stufe; null heißt: keine Frist vorgesehen.",
              example = "2026-10-14T00:41:07Z")
          @Nullable Instant deadline,
      @Schema(
              description =
                  """
                  Der Teilschritt: heute SESSION, WAITING_FOR_IMPLEMENTATION oder \
                  RELEASE_CHECKS; ein anderer Wert wird wörtlich durchgereicht.""",
              example = "WAITING_FOR_IMPLEMENTATION")
          String step,
      @Schema(description = "Ob die Stufe die Umsetzung hält.", example = "false")
          boolean holdsImplementation) {

    static StageView of(RunnerStufe stufe) {
      return new StageView(
          stufe.cardNumber(),
          stufe.stage(),
          stufe.startedAt(),
          stufe.deadline(),
          stufe.step(),
          stufe.holdsImplementation());
    }
  }

  /** Der zuletzt beendete Lauf. */
  @Schema(description = "Der zuletzt beendete Lauf.")
  record LastRunView(
      @Schema(description = "Kennung des Laufs.", example = "311") long runId,
      @Schema(description = "Art des Laufs: CHAIN oder IMPLEMENTATION.", example = "IMPLEMENTATION")
          NightRunMode mode,
      @Schema(description = "Beginn des Laufs.", example = "2026-10-13T22:39:12Z")
          Instant startedAt,
      @Schema(
              description = "Ende: von Hand beendet die Beendigung, sonst Beginn plus Dauer.",
              example = "2026-10-13T22:39:14Z")
          Instant endedAt,
      @Schema(
              description =
                  """
                  Ausgang des Laufs, wie in der Auswertung der Läufe: SUCCEEDED, FAILED, \
                  WAITING, CLOSED, NO_WORK oder NOT_STARTED.""",
              example = "NO_WORK")
          Verdict outcome,
      @Schema(
              description = "Grund eines Laufs ohne Arbeit; null, wenn er keinen meldet.",
              example = "Umsetzung belegt")
          @Nullable String noWorkReason) {

    static LastRunView of(LetzterLauf lauf) {
      return new LastRunView(
          lauf.runId(),
          lauf.mode(),
          lauf.startedAt(),
          lauf.ende(),
          lauf.ergebnis(),
          lauf.noWorkReason());
    }
  }
}
