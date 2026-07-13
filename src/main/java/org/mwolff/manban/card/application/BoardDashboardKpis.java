package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Kennzahlen eines Boards für das Zykluszeit-Dashboard. Dauern durchgängig in Sekunden; die
 * Formatierung übernimmt das Frontend. {@code null} bei einer Kennzahl bedeutet „keine Datenbasis"
 * (z. B. noch keine abgeschlossene Karte oder keine „Ready"-artige Spalte).
 */
public record BoardDashboardKpis(
    List<ColumnDwell> columnDwell,
    List<WeeklyThroughput> throughput,
    @Nullable Long avgLeadTimeSeconds,
    @Nullable Long avgCycleTimeSeconds,
    List<OutlierCard> outliers) {

  /** Durchschnittliche Verweildauer in einer Spalte (nur abgeschlossene Aufenthalte). */
  public record ColumnDwell(
      long columnId, String columnName, @Nullable Long avgDwellSeconds, int sampleCount) {}

  /** Abgeschlossene Karten in einem Wochenfenster (Beginn des 7-Tage-Fensters). */
  public record WeeklyThroughput(Instant weekStart, long doneCount) {}

  /** Eine Karte, die ungewöhnlich lange in einer Spalte lag (über der Schwelle). */
  public record OutlierCard(
      long cardId, int number, String title, String columnName, long dwellSeconds) {}
}
