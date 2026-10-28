package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.Modellvergleich;
import org.mwolff.manban.nightrun.domain.NightRunAttemptStage;

/**
 * Der Modellvergleich der Runs an einem Board als Antwort von {@code GET
 * /api/boards/{boardId}/night-run-models} (Issue #1947, Plan #1905).
 *
 * <p>Spiegelt {@link Modellvergleich} Feld für Feld, den Kopf flach, Enums als Namen.
 *
 * @param dataSince frühester Laufstart eines gezählten Vorgangs mit Modell; {@code null} ohne einen
 * @param lastTwelveWeeks ob der Datenbeginn im ältesten der zwölf Wochenfenster liegt
 * @param withoutModel gezählte Vorgänge ohne Modell ab dem Datenbeginn
 * @param attemptsInPeriod gezählte Vorgänge mit Modell
 * @param stages die Stufen in der Reihenfolge der Kette
 */
@Schema(
    description =
        "Modellvergleich der Runs an einem Board: je Stufe der Kette eine Zeile je Modell und"
            + " Denktiefe über die zwölf Wochenfenster bis jetzt.")
record NightRunModelsView(
    @Schema(
            description =
                "Frühester Laufstart eines gezählten Vorgangs mit Modell; null, wenn es keinen"
                    + " gibt.",
            example = "2026-08-01T22:00:00Z")
        @Nullable Instant dataSince,
    @Schema(
            description =
                "true, wenn der Datenbeginn im ältesten der zwölf Wochenfenster liegt (Kopf"
                    + " „letzte 12 Wochen“), sonst „seit dataSince“.",
            example = "false")
        boolean lastTwelveWeeks,
    @Schema(
            description = "Gezählte Vorgänge ohne gemeldetes Modell ab dem Datenbeginn.",
            example = "3")
        int withoutModel,
    @Schema(description = "Gezählte Vorgänge mit gemeldetem Modell.", example = "42")
        int attemptsInPeriod,
    @Schema(description = "Die Stufen der Kette in ihrer Reihenfolge, auch ohne Vorgang.")
        List<StageView> stages) {

  static NightRunModelsView of(Modellvergleich m) {
    Modellvergleich.Kopf kopf = m.kopf();
    return new NightRunModelsView(
        kopf.datenbeginn(),
        kopf.letzte12Wochen(),
        kopf.ohneModell(),
        kopf.vorgaengeImZeitraum(),
        m.stufen().stream().map(StageView::of).toList());
  }

  /** Eine Stufe — siehe {@link Modellvergleich.Stufe}. */
  @Schema(description = "Eine Stufe der Kette mit ihren Zeilen.")
  record StageView(
      @Schema(description = "Die Stufe.", example = "UMSETZUNG") NightRunAttemptStage stage,
      @Schema(description = "Je Modell und Denktiefe eine Zeile, nach Namen; leer ohne Vorgang.")
          List<RowView> rows) {

    static StageView of(Modellvergleich.Stufe s) {
      return new StageView(s.stufe(), s.zeilen().stream().map(RowView::of).toList());
    }
  }

  /** Eine Zeile — siehe {@link Modellvergleich.Zeile}. */
  @Schema(description = "Ein Modell mit einer Denktiefe in einer Stufe.")
  record RowView(
      @Schema(description = "Das Modell, wie gemeldet.", example = "claude-opus-5-5") String model,
      @Schema(description = "Die Denktiefe, wie gemeldet; null ohne Denktiefe.", example = "medium")
          @Nullable String effort,
      @Schema(description = "Zahl der Vorgänge.", example = "12") int attempts,
      @Schema(description = "Zahl der erfolgreichen Vorgänge.", example = "9") int succeeded,
      @Schema(
              description =
                  "true ab fünf Vorgängen; sonst steht „zu wenig Daten“ statt der Erfolgsquote.",
              example = "true")
          boolean enoughData,
      @Schema(
              description =
                  "Median der Kosten je Vorgang in USD; null, wenn kein Vorgang sie trägt.",
              example = "1.25")
          @Nullable BigDecimal costMedianUsd,
      @Schema(description = "Zahl der Vorgänge, die Kosten tragen.", example = "9") int costFrom,
      @Schema(
              description =
                  "Median der Modellzeit je Vorgang in Millisekunden; null, wenn kein Vorgang sie"
                      + " trägt.",
              example = "95000")
          @Nullable Long modelTimeMedianMs,
      @Schema(description = "Zahl der Vorgänge, die eine Modellzeit tragen.", example = "12")
          int modelTimeFrom) {

    static RowView of(Modellvergleich.Zeile z) {
      return new RowView(
          z.model(),
          z.effort(),
          z.vorgaenge(),
          z.erfolgreich(),
          z.enoughData(),
          z.kostenMedian(),
          z.kostenAus(),
          z.modellzeitMedianMs(),
          z.modellzeitAus());
    }
  }
}
