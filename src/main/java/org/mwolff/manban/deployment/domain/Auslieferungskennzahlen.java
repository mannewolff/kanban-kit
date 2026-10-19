package org.mwolff.manban.deployment.domain;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Die vier DORA-Kennzahlen der Platte „Auslieferung“ (Issue #1832, Plan #1831 E5, E8–E11). Gebildet
 * von {@link AuslieferungsErmittlung}.
 *
 * @param hasReports ob je ein Versuch gemeldet wurde; ohne zeigt die Platte „noch keine
 *     Auslieferung“ (E9)
 * @param dataSince erste Meldung, wenn sie im Fenster liegt; sonst {@code null} (E10)
 * @param deploymentCount erfolgreiche Versuche im Fenster, auch eine echte 0
 * @param leadTimeMedianSeconds Median Commit → Auslieferung; {@code null} ohne ausgelieferten
 *     Commit im Fenster
 * @param changeFailurePercent gescheiterte an allen Versuchen im Fenster in ganzen Prozent; {@code
 *     null} ohne Versuch im Fenster
 * @param timeToRestoreMedianSeconds Median der im Fenster endenden Ausfälle; {@code null} ohne
 *     abgeschlossenen Ausfall
 * @param outageSince erster gescheiterter Versuch der laufenden Folge; {@code null} ohne laufenden
 *     Ausfall
 * @param weeks die zwölf Wochenfenster, das älteste zuerst (E11)
 */
public record Auslieferungskennzahlen(
    boolean hasReports,
    @Nullable Instant dataSince,
    int deploymentCount,
    @Nullable Long leadTimeMedianSeconds,
    @Nullable Integer changeFailurePercent,
    @Nullable Long timeToRestoreMedianSeconds,
    @Nullable Instant outageSince,
    List<Woche> weeks) {

  public Auslieferungskennzahlen {
    weeks = List.copyOf(weeks);
  }

  /**
   * Ein Wochenfenster der Leiste.
   *
   * @param weekStart Beginn des Fensters aus {@code Wochenfenster}
   */
  public record Woche(Instant weekStart, int successCount, int failureCount) {}
}
