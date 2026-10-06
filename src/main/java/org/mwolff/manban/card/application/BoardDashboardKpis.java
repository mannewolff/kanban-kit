package org.mwolff.manban.card.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Kennzahlen eines Boards für das Kennzahl-Dashboard. Dauern durchgängig in Sekunden; die
 * Formatierung übernimmt das Frontend. {@code null} bei einer Kennzahl bedeutet „keine Datenbasis"
 * (z. B. noch keine abgeschlossene Karte oder keine „In-Progress"-artige Spalte).
 *
 * <p>{@code leadTimeSampleCount} und {@code implementationSampleCount} sind die Anzahl der Karten,
 * aus denen der jeweilige Durchschnitt gemittelt wurde — nicht die Anzahl aller Karten und nicht
 * die Summe des Durchsatzes (der ist auf zwölf Wochen gefenstert). Ohne diese Zahl ließe sich der
 * Durchschnitt nicht einordnen. Beide Zählungen weichen voneinander ab: die Implementierungszeit
 * setzt zusätzlich einen abgeschlossenen Aufenthalt in einer „In-Progress"-artigen Spalte voraus.
 *
 * <p>Durchschnitt und Stichprobengröße gehören zusammen: ein Durchschnitt ohne Messung ist ebenso
 * widersprüchlich wie Messungen ohne Durchschnitt. Der Kompaktkonstruktor sichert das zu, statt
 * sich darauf zu verlassen, dass die einzige aufrufende Berechnung es schon richtig macht — das
 * Frontend leitet die Leerwert-Optik allein aus der Stichprobengröße ab.
 */
@Schema(description = "Kennzahlen eines Boards; Dauern in Sekunden, null heißt keine Datenbasis.")
public record BoardDashboardKpis(
    @Schema(description = "Durchschnittliche Verweildauer je Spalte.")
        List<ColumnDwell> columnDwell,
    @Schema(description = "Abgeschlossene Karten je Woche, die letzten zwölf Wochen.")
        List<WeeklyThroughput> throughput,
    @Schema(
            description =
                "Durchschnittliche Durchlaufzeit abgeschlossener Karten (Anlage bis Done).",
            example = "432000")
        @Nullable Long avgLeadTimeSeconds,
    @Schema(
            description = "Zahl der Karten, über die die Durchlaufzeit gemittelt ist.",
            example = "42")
        int leadTimeSampleCount,
    @Schema(
            description =
                "Durchschnittliche Zeit in Spalten, in denen an der Karte gearbeitet wird.",
            example = "86400")
        @Nullable Long avgImplementationSeconds,
    @Schema(
            description = "Zahl der Karten, über die die Umsetzungszeit gemittelt ist.",
            example = "37")
        int implementationSampleCount,
    @Schema(description = "Karten, die ungewöhnlich lange in einer Spalte lagen.")
        List<OutlierCard> outliers) {

  public BoardDashboardKpis {
    requireSampleBasis(avgLeadTimeSeconds, leadTimeSampleCount, "leadTime");
    requireSampleBasis(avgImplementationSeconds, implementationSampleCount, "implementation");
  }

  private static void requireSampleBasis(
      @Nullable Long averageSeconds, int sampleCount, String metric) {
    if (sampleCount < 0) {
      throw new IllegalArgumentException(
          metric + "SampleCount darf nicht negativ sein, war: " + sampleCount);
    }
    if ((averageSeconds == null) != (sampleCount == 0)) {
      throw new IllegalArgumentException(
          metric
              + ": Durchschnitt und Stichprobengröße widersprechen sich (Durchschnitt="
              + averageSeconds
              + ", Messungen="
              + sampleCount
              + ")");
    }
  }

  /** Durchschnittliche Verweildauer in einer Spalte (nur abgeschlossene Aufenthalte). */
  @Schema(description = "Durchschnittliche Verweildauer in einer Spalte.")
  public record ColumnDwell(
      @Schema(description = "Interne ID der Spalte.", example = "17") long columnId,
      @Schema(description = "Name der Spalte.", example = "In Arbeit") String columnName,
      @Schema(description = "Durchschnittliche Verweildauer; null ohne Messung.", example = "7200")
          @Nullable Long avgDwellSeconds,
      @Schema(description = "Zahl der gemessenen Aufenthalte.", example = "12") int sampleCount) {}

  /** Abgeschlossene Karten in einem Wochenfenster (Beginn des 7-Tage-Fensters). */
  @Schema(description = "Abgeschlossene Karten in einem Wochenfenster.")
  public record WeeklyThroughput(
      @Schema(description = "Beginn des Sieben-Tage-Fensters.") Instant weekStart,
      @Schema(description = "Zahl der in diesem Fenster abgeschlossenen Karten.", example = "9")
          long doneCount) {}

  /** Eine Karte, die ungewöhnlich lange in einer Spalte lag (über der Schwelle). */
  @Schema(description = "Eine Karte, die ungewöhnlich lange in einer Spalte lag.")
  public record OutlierCard(
      @Schema(description = "Interne ID der Karte.", example = "812") long cardId,
      @Schema(description = "Projektweite Nummer.", example = "1404") int number,
      @Schema(description = "Titel.", example = "Export als CSV") String title,
      @Schema(description = "Name der Spalte.", example = "In Arbeit") String columnName,
      @Schema(description = "Verweildauer in der Spalte.", example = "1209600")
          long dwellSeconds) {}
}
