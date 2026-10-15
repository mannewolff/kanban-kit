package org.mwolff.manban.card.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Die Platte „Alter in Arbeit“ eines Boards (Plan #1765, E8, E10, E13): je Zustand Ready, In
 * Progress und In Review die 85-%-Schwelle und die Karten nach Alter, die älteste zuerst.
 */
@Schema(
    description =
        "Die Platte „Alter in Arbeit“: je Zustand Ready, In Progress und In Review die Karten nach"
            + " Alter, die älteste zuerst.")
public record AgingWipView(
    @Schema(description = "Arbeitspakete im Zustand Ready.") State ready,
    @Schema(description = "Arbeitspakete im Zustand In Progress.") State inProgress,
    @Schema(description = "Arbeitspakete im Zustand In Review.") State inReview) {

  /** Ein Zustand der Platte. */
  @Schema(description = "Ein Zustand: Schwelle, Zahl der Vergleichswerte und die Karten darin.")
  public record State(
      @Schema(
              description =
                  "Zahl der abgeschlossenen Aufenthalte der letzten zwölf Wochen, aus denen die"
                      + " Schwelle stammt.",
              example = "17")
          int comparisonCount,
      @Schema(
              description = "Die 85-%-Schwelle in Sekunden; null unter fünf Vergleichswerten.",
              example = "259200")
          @Nullable Long thresholdSeconds,
      @Schema(
              description =
                  "Die Karten im Zustand, die älteste zuerst; leer, wenn keine dort liegt.")
          List<AgingCard> cards) {}

  /** Eine Karte im Zustand. */
  @Schema(description = "Eine Karte im Zustand mit ihrem Alter seit dem letzten Eintritt.")
  public record AgingCard(
      @Schema(description = "Interne ID der Karte.", example = "42") long id,
      @Schema(description = "Kartennummer im Projekt.", example = "1773") int number,
      @Schema(description = "Titel der Karte.", example = "Endpunkte für den Leitstand")
          String title,
      @Schema(description = "Zeit seit dem letzten Eintritt in den Zustand.", example = "86400")
          long ageSeconds,
      @Schema(
              description =
                  "Ob der Eintritt unbekannt und ageSeconds nur der Mindestwert ab dem frühesten"
                      + " bekannten Zeitpunkt ist.",
              example = "false")
          boolean ageIsMinimum,
      @Schema(description = "Ob das Alter über der Schwelle liegt.", example = "false")
          boolean overThreshold) {}
}
