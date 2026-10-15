package org.mwolff.manban.card.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Die Platte „Fluss“ eines Boards (Plan #1765, E9, E13): je Kalendertag in der Zone des Lesers die
 * fünf Bänder, der älteste Tag zuerst, heute zuletzt.
 */
@Schema(
    description =
        "Die Tage der Platte „Fluss“: je Kalendertag die Zahl der Arbeitspakete je Zustand, der"
            + " älteste Tag zuerst.")
public record BoardFlowView(
    @Schema(description = "Die 84 Kalendertage bis einschließlich heute, der älteste zuerst.")
        List<Day> days,
    @Schema(
            description =
                "Der erste Tag mit sicher bestimmbarem Stand, wenn er nach dem ersten Tag des"
                    + " Zeitraums liegt; sonst null.",
            example = "2026-09-01")
        @Nullable LocalDate dataSince) {

  /** Ein Tag; ist sein Stand unbekannt, sind alle fünf Zahlen null. */
  @Schema(description = "Ein Kalendertag; bei unbekanntem Stand sind die fünf Zahlen null.")
  public record Day(
      @Schema(description = "Kalendertag in der Zone des Lesers.", example = "2026-10-15")
          LocalDate date,
      @Schema(description = "Ob der Stand am Tagesende sicher bestimmbar ist.", example = "true")
          boolean known,
      @Schema(description = "Arbeitspakete im Zustand Backlog am Tagesende.", example = "12")
          @Nullable Integer backlog,
      @Schema(description = "Arbeitspakete im Zustand Ready am Tagesende.", example = "4")
          @Nullable Integer ready,
      @Schema(description = "Arbeitspakete im Zustand In Progress am Tagesende.", example = "1")
          @Nullable Integer inProgress,
      @Schema(description = "Arbeitspakete im Zustand In Review am Tagesende.", example = "3")
          @Nullable Integer inReview,
      @Schema(
              description = "Seit Beginn des Zeitraums fertig gewordene Arbeitspakete.",
              example = "9")
          @Nullable Integer done) {}
}
