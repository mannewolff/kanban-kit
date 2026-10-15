package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.Nacharbeit;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Zustand;

/**
 * Die Nacharbeit der Runs an einem Board als Antwort von {@code GET
 * /api/boards/{boardId}/night-run-rework} (Issue #1757, Plan #1752).
 *
 * <p>Spiegelt {@link Nacharbeit} Feld für Feld, Enums als Namen. Eine Karte trägt nur Nummer, Titel
 * und Anzahl, nie Beschreibung oder Kommentare.
 *
 * @param dataSince Start des ältesten aufbewahrten NIGHT-Laufs; {@code null} ohne Lauf
 * @param runsInPeriod ob in den zwölf Wochenfenstern mindestens ein Run startete
 * @param packages Zeile „Pakete zurückgezogen“
 * @param documents Zeile „Dokumente angehalten“
 * @param weeks die zwölf Wochenfenster, das älteste zuerst
 */
@Schema(
    description =
        "Nacharbeit der Runs an einem Board: zurückgezogene Arbeitspakete und angehaltene"
            + " Dokumente über die zwölf Wochenfenster bis jetzt. Je Karte nur Nummer, Titel und"
            + " Anzahl.")
record NightRunReworkView(
    @Schema(
            description =
                "Start des ältesten aufbewahrten Nachtlaufs; null, wenn das Projekt keinen hat.",
            example = "2026-08-01T22:00:00Z")
        @Nullable Instant dataSince,
    @Schema(
            description = "true, wenn in den zwölf Wochenfenstern mindestens ein Run startete.",
            example = "true")
        boolean runsInPeriod,
    @Schema(
            description =
                "Zeile „Pakete zurückgezogen“: Arbeitspakete, die ein Run aus In Progress nach"
                    + " Ready oder Backlog zurückzog.")
        LineView packages,
    @Schema(
            description =
                "Zeile „Dokumente angehalten“: Fachpläne und Pläne, an die ein Run kit:clarify"
                    + " oder run:waiting hängte.")
        LineView documents,
    @Schema(description = "Die zwölf rollenden Wochenfenster bis jetzt, das älteste zuerst.")
        List<WeekView> weeks) {

  static NightRunReworkView of(Nacharbeit n) {
    return new NightRunReworkView(
        n.pakete().datenbeginn(),
        n.runsImZeitraum(),
        LineView.of(n.pakete()),
        LineView.of(n.dokumente()),
        n.wochen().stream().map(WeekView::of).toList());
  }

  /** Eine Zeile der Platte — siehe {@link Nacharbeit.Zeile}. */
  @Schema(description = "Eine Zeile der Platte über alle Wochen mit Daten.")
  record LineView(
      @Schema(
              description = "Ab wann die Gruppe Daten hat; null ohne aufbewahrten Lauf.",
              example = "2026-08-01T22:00:00Z")
          @Nullable Instant dataSince,
      @Schema(description = "Zahl der Karten mit Signal, jede höchstens einmal.", example = "1")
          int affected,
      @Schema(description = "Zahl der von Runs bearbeiteten Karten.", example = "14") int processed,
      @Schema(description = "Die Karten mit Signal, nach Anzahl absteigend, dann nach Nummer.")
          List<CardView> cards) {

    static LineView of(Nacharbeit.Zeile z) {
      return new LineView(
          z.datenbeginn(),
          z.betroffen(),
          z.bearbeitet(),
          z.karten().stream().map(CardView::of).toList());
    }
  }

  /** Eine Karte der Kartenliste — siehe {@link Nacharbeit.Karteneintrag}. */
  @Schema(description = "Eine Karte der Kartenliste.")
  record CardView(
      @Schema(description = "Projektweite Nummer der Karte.", example = "1712") int number,
      @Schema(description = "Titel der Karte.", example = "Endpunkt bereitstellen") String title,
      @Schema(description = "Wie oft der Rückzug bzw. Halt geschah.", example = "2") int count) {

    static CardView of(Nacharbeit.Karteneintrag k) {
      return new CardView(k.nummer(), k.titel(), k.anzahl());
    }
  }

  /** Ein Wochenfenster — siehe {@link Nacharbeit.Woche}. */
  @Schema(description = "Ein Wochenfenster des Verlaufs.")
  record WeekView(
      @Schema(description = "Beginn des Fensters.", example = "2026-10-08T06:00:00Z")
          Instant weekStart,
      @Schema(description = "Wert der Arbeitspakete in dieser Woche.") WeekValueView packages,
      @Schema(description = "Wert der Dokumente in dieser Woche.") WeekValueView documents) {

    static WeekView of(Nacharbeit.Woche w) {
      return new WeekView(
          w.beginn(), WeekValueView.of(w.pakete()), WeekValueView.of(w.dokumente()));
    }
  }

  /** Wert einer Gruppe in einer Woche — siehe {@link Nacharbeit.Wochenwert}. */
  @Schema(description = "Wert einer Gruppe in einer Woche.")
  record WeekValueView(
      @Schema(
              description =
                  "KEINE_DATEN: Das Fenster endet vor dem Datenbeginn der Gruppe. KEINE_KARTE:"
                      + " Daten vorhanden, aber keine bearbeitete Karte. QUOTE: affected von"
                      + " processed.",
              example = "QUOTE")
          Zustand state,
      @Schema(description = "Zahl der Karten mit Signal in der Woche.", example = "1") int affected,
      @Schema(description = "Zahl der bearbeiteten Karten in der Woche.", example = "14")
          int processed) {

    static WeekValueView of(Nacharbeit.Wochenwert w) {
      return new WeekValueView(w.zustand(), w.betroffen(), w.bearbeitet());
    }
  }
}
