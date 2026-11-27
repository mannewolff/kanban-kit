package org.mwolff.manban.codeanalyse.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte.Summe;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte.Woche;

/**
 * Die Platte „Codeanalyse“ als Antwort von {@code GET
 * /api/boards/{boardId}/dashboard/code-analysis} (Issue #2276, Plan #2272 E9). Spiegelt {@link
 * Wochenwerte} Feld für Feld; {@code null} heißt „keine Grundlage“ und zeigt eine Lücke, keine Null
 * (Fachplan #2271 AK 6).
 */
@Schema(
    description =
        "Die Befunddichte der Codeanalyse des Projekts über zwölf Kalenderwochen in UTC: die"
            + " laufende Woche und die elf davor. Gezählt wird allein, was das Workflow-Kit je"
            + " Veröffentlichung gemeldet hat; maßgeblich ist der Zeitpunkt der Veröffentlichung."
            + " null heißt „keine Grundlage“.")
record CodeAnalysisView(
    @Schema(
            description =
                "false, solange für das Projekt keine Messung angekommen ist, zählend oder nicht.",
            example = "true")
        boolean hasMeasurements,
    @Schema(
            description =
                "Veröffentlichung der ersten angekommenen Messung, zählend oder nicht, wenn sie"
                    + " nach dem Beginn des Fensters liegt; sonst null.",
            example = "2026-10-14T08:30:00Z")
        @Nullable Instant dataSince,
    @Schema(description = "Die zwölf Kalenderwochen des Fensters, die älteste zuerst.")
        List<WeekView> weeks) {

  CodeAnalysisView {
    weeks = List.copyOf(weeks);
  }

  static CodeAnalysisView of(Wochenwerte werte) {
    return new CodeAnalysisView(
        werte.hatMessungen(),
        werte.dataSince(),
        werte.wochen().stream().map(WeekView::of).toList());
  }

  /** Eine Kalenderwoche der Leiste. */
  @Schema(
      description =
          "Eine Kalenderwoche von Montag 00:00 UTC bis Sonntag. Ohne zählende Messung sind cards,"
              + " before, final und reductionPercent null; die nicht zählenden Messungen stehen"
              + " trotzdem da.")
  record WeekView(
      @Schema(description = "Beginn der Woche, Montag 00:00 UTC.", example = "2026-11-23T00:00:00Z")
          Instant weekStart,
      @Schema(description = "Zahl der zählenden Messungen der Woche.", example = "2")
          int countingMeasurements,
      @Schema(
              description =
                  "Summe der Kartenzahlen der zählenden Messungen; eine Karte in zwei"
                      + " Veröffentlichungen zählt zweimal. null ohne zählende Messung.",
              example = "5")
          @Nullable Long cards,
      @Schema(
              description =
                  "Summen vor der Codeanalyse über die zählenden Messungen; null ohne zählende"
                      + " Messung.")
          @Nullable FindingsView before,
      @Schema(
              description =
                  "Summen des veröffentlichten Stands über die zählenden Messungen; null ohne"
                      + " zählende Messung.")
          @JsonProperty("final")
          @Nullable FindingsView finalState,
      @Schema(
              description =
                  "Rückgang der Funde in Prozent: (Funde vorher − Funde Endzustand) / Funde vorher"
                      + " × 100, negativ, wenn die Funde steigen; null ohne Fund vorher oder ohne"
                      + " zählende Messung.",
              example = "75.0")
          @Nullable Double reductionPercent,
      @Schema(
              description =
                  "Die nicht zählenden Messungen der Woche je Grund, nach Grund geordnet.")
          List<NotCountingView> notCounting) {

    WeekView {
      notCounting = List.copyOf(notCounting);
    }

    static WeekView of(Woche woche) {
      return new WeekView(
          woche.wochenbeginn(),
          woche.zaehlendeMessungen(),
          woche.karten(),
          FindingsView.of(woche.vorher()),
          FindingsView.of(woche.endzustand()),
          woche.rueckgangProzent(),
          woche.nichtZaehlend().stream()
              .map(n -> new NotCountingView(n.grund(), n.anzahl()))
              .toList());
    }
  }

  /** Die Wochensummen eines Zustands. */
  @Schema(
      description =
          "Die Wochensummen eines Zustands über alle zählenden Messungen. Jede Dichte ist Funde je"
              + " 1.000 geänderte Zeilen aus den Summen, kein Mittelwert einzelner Dichten; ohne"
              + " geänderte Zeilen ist sie null.")
  record FindingsView(
      @Schema(description = "Funde der Art Fehler.", example = "2") long bugs,
      @Schema(description = "Funde der Art Sicherheitslücke.", example = "1") long vulnerabilities,
      @Schema(description = "Funde der Art Wartbarkeitsmangel.", example = "5") long codeSmells,
      @Schema(description = "Summe der Funde aller drei Arten.", example = "8") long findings,
      @Schema(description = "Summe der geänderten Zeilen.", example = "2000") long changedLines,
      @Schema(
              description = "Fehler je 1.000 geänderte Zeilen; null ohne geänderte Zeilen.",
              example = "1.0")
          @Nullable Double bugsDensity,
      @Schema(
              description =
                  "Sicherheitslücken je 1.000 geänderte Zeilen; null ohne geänderte Zeilen.",
              example = "0.5")
          @Nullable Double vulnerabilitiesDensity,
      @Schema(
              description =
                  "Wartbarkeitsmängel je 1.000 geänderte Zeilen; null ohne geänderte Zeilen.",
              example = "2.5")
          @Nullable Double codeSmellsDensity,
      @Schema(
              description =
                  "Funde aller Arten je 1.000 geänderte Zeilen; null ohne geänderte Zeilen.",
              example = "4.0")
          @Nullable Double findingsDensity) {

    static @Nullable FindingsView of(@Nullable Summe summe) {
      if (summe == null) {
        return null;
      }
      return new FindingsView(
          summe.fehler(),
          summe.sicherheitsluecken(),
          summe.wartbarkeitsmaengel(),
          summe.funde(),
          summe.zeilen(),
          summe.dichteFehler(),
          summe.dichteSicherheitsluecken(),
          summe.dichteWartbarkeitsmaengel(),
          summe.dichte());
    }
  }

  /** Nicht zählende Messungen einer Woche mit demselben Grund. */
  @Schema(description = "Nicht zählende Messungen einer Woche mit demselben Grund.")
  record NotCountingView(
      @Schema(
              description =
                  "Der Grund, wie ihn das Kit meldete, etwa no-measurement, missing-before,"
                      + " missing-final, cards-removed, incomplete oder not-attributable; ein"
                      + " unbekannter Grund steht, wie er gemeldet wurde.",
              example = "missing-final")
          String reason,
      @Schema(description = "Zahl dieser Messungen in der Woche.", example = "1") int count) {}
}
