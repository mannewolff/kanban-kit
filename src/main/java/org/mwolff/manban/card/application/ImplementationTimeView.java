package org.mwolff.manban.card.application;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Durchschnittliche Umsetzungszeit der Karten eines Boards, die in einem Zeitraum fertig wurden
 * (Plan #1539). Ohne Zeitraum gilt sie über alle gemessenen Karten und gleicht dann dem Wert des
 * Dashboard-Abrufs. Durchschnitt und Datenbasis gehören zusammen wie in {@link BoardDashboardKpis}.
 */
@Schema(
    description =
        "Durchschnittliche Umsetzungszeit in einem Zeitraum; null heißt keine Datenbasis.")
public record ImplementationTimeView(
    @Schema(
            description =
                "Durchschnittliche Zeit in Spalten, in denen an der Karte gearbeitet wird.",
            example = "86400")
        @Nullable Long avgImplementationSeconds,
    @Schema(
            description = "Zahl der Karten, über die die Umsetzungszeit gemittelt ist.",
            example = "7")
        int implementationSampleCount) {

  public ImplementationTimeView {
    BoardDashboardKpis.requireSampleBasis(
        avgImplementationSeconds, implementationSampleCount, "implementation");
  }
}
