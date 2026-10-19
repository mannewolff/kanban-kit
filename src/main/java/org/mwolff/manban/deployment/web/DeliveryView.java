package org.mwolff.manban.deployment.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.deployment.domain.Auslieferungskennzahlen;

/**
 * Die Platte „Auslieferung“ als Antwort von {@code GET /api/boards/{boardId}/dashboard/delivery}
 * (Issue #1835, Plan #1831 E5). Spiegelt {@link Auslieferungskennzahlen} Feld für Feld; Dauern in
 * Sekunden, {@code null} heißt „keine Grundlage“ (E9).
 */
@Schema(
    description =
        "Die vier DORA-Kennzahlen des Projekts über die zwölf rollenden Wochenfenster bis jetzt,"
            + " gezählt allein aus den gemeldeten Auslieferungsversuchen. Dauern in Sekunden; null"
            + " heißt „keine Grundlage“.")
record DeliveryView(
    @Schema(
            description =
                "false, solange für das Projekt nie ein Auslieferungsversuch gemeldet wurde.",
            example = "true")
        boolean hasReports,
    @Schema(
            description =
                "Ausgang der ersten gemeldeten Auslieferung, wenn er im Fenster liegt; sonst null.",
            example = "2026-09-26T10:00:00Z")
        @Nullable Instant dataSince,
    @Schema(description = "Erfolgreiche Auslieferungen im Fenster.", example = "4")
        int deploymentCount,
    @Schema(
            description =
                "Median Commit → erfolgreiche Auslieferung über die im Fenster ausgelieferten"
                    + " Commits; null ohne ausgelieferten Commit.",
            example = "172800")
        @Nullable Long leadTimeMedianSeconds,
    @Schema(
            description =
                "Gescheiterte an allen Versuchen im Fenster in ganzen Prozent; null ohne Versuch im"
                    + " Fenster.",
            example = "20")
        @Nullable Integer changeFailurePercent,
    @Schema(
            description =
                "Median der im Fenster abgeschlossenen Ausfälle; null ohne abgeschlossenen"
                    + " Ausfall.",
            example = "7200")
        @Nullable Long timeToRestoreMedianSeconds,
    @Schema(
            description =
                "Erster gescheiterter Versuch des noch laufenden Ausfalls; null ohne laufenden"
                    + " Ausfall.",
            example = "2026-10-17T09:00:00Z")
        @Nullable Instant outageSince,
    @Schema(description = "Die zwölf rollenden Wochenfenster bis jetzt, das älteste zuerst.")
        List<WeekView> weeks) {

  DeliveryView {
    weeks = List.copyOf(weeks);
  }

  static DeliveryView of(Auslieferungskennzahlen kennzahlen) {
    return new DeliveryView(
        kennzahlen.hasReports(),
        kennzahlen.dataSince(),
        kennzahlen.deploymentCount(),
        kennzahlen.leadTimeMedianSeconds(),
        kennzahlen.changeFailurePercent(),
        kennzahlen.timeToRestoreMedianSeconds(),
        kennzahlen.outageSince(),
        kennzahlen.weeks().stream()
            .map(w -> new WeekView(w.weekStart(), w.successCount(), w.failureCount()))
            .toList());
  }

  /** Ein Wochenfenster der Leiste. */
  @Schema(description = "Ein rollendes 7-Tage-Fenster der Wochenleiste.")
  record WeekView(
      @Schema(description = "Beginn des Fensters.", example = "2026-10-10T12:00:00Z")
          Instant weekStart,
      @Schema(description = "Erfolgreiche Auslieferungen im Fenster.", example = "3")
          int successCount,
      @Schema(description = "Gescheiterte Auslieferungen im Fenster.", example = "1")
          int failureCount) {}
}
