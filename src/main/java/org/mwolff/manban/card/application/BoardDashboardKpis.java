package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Kennzahlen eines Boards für das Zykluszeit-Dashboard. Dauern durchgängig in Sekunden; die
 * Formatierung übernimmt das Frontend. {@code null} bei einer Kennzahl bedeutet „keine Datenbasis"
 * (z. B. noch keine abgeschlossene Karte oder keine „Ready"-artige Spalte).
 *
 * <p>{@code leadTimeSampleCount} und {@code cycleTimeSampleCount} sind die Anzahl der Karten, aus
 * denen der jeweilige Durchschnitt gemittelt wurde — nicht die Anzahl aller Karten und nicht die
 * Summe des Durchsatzes (der ist auf zwölf Wochen gefenstert). Ohne diese Zahl ließe sich der
 * Durchschnitt nicht einordnen. Beide Zählungen weichen voneinander ab: die Cycle Time setzt
 * zusätzlich einen Eintritt in eine „Ready"-artige Spalte voraus.
 *
 * <p>Durchschnitt und Stichprobengröße gehören zusammen: ein Durchschnitt ohne Messung ist ebenso
 * widersprüchlich wie Messungen ohne Durchschnitt. Der Kompaktkonstruktor sichert das zu, statt
 * sich darauf zu verlassen, dass die einzige aufrufende Berechnung es schon richtig macht — das
 * Frontend leitet die Leerwert-Optik allein aus der Stichprobengröße ab.
 */
public record BoardDashboardKpis(
    List<ColumnDwell> columnDwell,
    List<WeeklyThroughput> throughput,
    @Nullable Long avgLeadTimeSeconds,
    int leadTimeSampleCount,
    @Nullable Long avgCycleTimeSeconds,
    int cycleTimeSampleCount,
    List<OutlierCard> outliers) {

  public BoardDashboardKpis {
    requireSampleBasis(avgLeadTimeSeconds, leadTimeSampleCount, "leadTime");
    requireSampleBasis(avgCycleTimeSeconds, cycleTimeSampleCount, "cycleTime");
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
  public record ColumnDwell(
      long columnId, String columnName, @Nullable Long avgDwellSeconds, int sampleCount) {}

  /** Abgeschlossene Karten in einem Wochenfenster (Beginn des 7-Tage-Fensters). */
  public record WeeklyThroughput(Instant weekStart, long doneCount) {}

  /** Eine Karte, die ungewöhnlich lange in einer Spalte lag (über der Schwelle). */
  public record OutlierCard(
      long cardId, int number, String title, String columnName, long dwellSeconds) {}
}
