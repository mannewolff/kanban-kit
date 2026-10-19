package org.mwolff.manban.card.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.VorhabenLage.Lage;

/**
 * Die Prognose der Platte „Vorhaben“ eines Boards (Plan #1819): je sichtbarem Vorhaben Lage,
 * Zählung und die beiden Daten. Die Texte setzt das Frontend zusammen.
 */
@Schema(
    description =
        "Die Prognose der Platte „Vorhaben“: je nicht archiviertem Vorhaben die Lage, die Zählung"
            + " der Arbeitspakete und die Tage, bis zu denen mit 50 % und 85 % alle offenen Pakete"
            + " umgesetzt sind.")
public record EpicForecastView(
    @Schema(
            description =
                "Zahl der Tage der letzten 30 abgeschlossenen Tage (12 bis 12 Uhr), an denen"
                    + " mindestens ein Arbeitspaket fertig wurde.",
            example = "12")
        int throughputDays,
    @Schema(description = "Die nicht archivierten Vorhaben des Boards.") List<Epic> epics) {

  /** Ein Vorhaben; die Daten stehen nur bei der Lage {@code FORECAST}. */
  @Schema(description = "Ein Vorhaben; p50 und p85 stehen nur bei der Lage FORECAST.")
  public record Epic(
      @Schema(description = "Interne ID der Vorhaben-Karte.", example = "42") long id,
      @Schema(description = "Board-weite Nummer des Vorhabens.", example = "1782") int number,
      @Schema(description = "Titel des Vorhabens.", example = "Leitstand-Prognose") String title,
      @Schema(
              description =
                  "Lage in der Rangfolge: NO_PACKAGES (noch keine Pakete geschnitten), ALL_DONE"
                      + " (kein offenes Paket), INSUFFICIENT_THROUGHPUT (zu wenig Durchsatz für"
                      + " eine Prognose), FORECAST (Daten der Prognose).",
              example = "FORECAST")
          Lage state,
      @Schema(description = "Arbeitspakete des Vorhabens ohne Archiv.", example = "14") int total,
      @Schema(description = "Davon in In Review oder Done.", example = "2") int finished,
      @Schema(description = "Davon offen.", example = "12") int open,
      @Schema(
              description =
                  "Tag (benannt nach seinem Ende um 12 Uhr), bis zu dem mit 50 % alle offenen"
                      + " Pakete umgesetzt sind; null außer bei FORECAST.",
              example = "2026-10-21")
          @Nullable LocalDate p50,
      @Schema(
              description =
                  "Tag (benannt nach seinem Ende um 12 Uhr), bis zu dem mit 85 % alle offenen"
                      + " Pakete umgesetzt sind; null außer bei FORECAST.",
              example = "2026-10-24")
          @Nullable LocalDate p85,
      @Schema(
              description =
                  "Ob die Daten nur „frühestens“ gelten, weil noch Pakete oder ein Plan aus einem"
                      + " ausstehenden Dokument folgen; nur bei FORECAST true.",
              example = "false")
          boolean earliest,
      @Schema(
              description =
                  "Pläne ohne Arbeitspaket und Fachpläne ohne Plan, aufsteigend nach Nummer.")
          List<PendingDocument> pendingDocuments,
      @Schema(
              description = "Andere angezeigte Vorhaben mit mindestens einem offenen Paket.",
              example = "2")
          int otherEpicsSharing) {}

  /** Ein Dokument, aus dem noch Pakete oder ein Plan folgen. */
  @Schema(description = "Ein Dokument, aus dem noch Pakete oder ein Plan folgen.")
  public record PendingDocument(
      @Schema(description = "Board-weite Nummer der Karte.", example = "1819") int number,
      @Schema(description = "Titel der Karte.", example = "[Plan] Leitstand-Prognose")
          String title) {}
}
