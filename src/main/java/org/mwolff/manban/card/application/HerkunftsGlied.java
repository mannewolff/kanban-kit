package org.mwolff.manban.card.application;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Ein Vorfahr in der Herkunft einer Karte der Board-Liste (Issue #1930): der Hinweis an der
 * Kartennummer zeigt je Glied Nummer und Titel, nächster Vorfahr zuerst.
 *
 * @param number projektweite Nummer des Vorfahren
 * @param title Titel samt Präfix, wie er ist; {@code null} bei einer Lücke
 * @param archived ob der Vorfahr archiviert ist; bei einer Lücke {@code false}
 * @param luecke {@code null} für einen sichtbaren Vorfahren, sonst die Art der Lücke
 */
@Schema(description = "Ein Vorfahr in der Herkunft einer Karte.")
public record HerkunftsGlied(
    @Schema(description = "Projektweite Nummer des Vorfahren.", example = "1914") int number,
    @Schema(
            description = "Titel samt Präfix; leer bei einer Lücke.",
            example = "[Plan] Ansicht als XY")
        @Nullable String title,
    @Schema(description = "Ob der Vorfahr archiviert ist.", example = "false") boolean archived,
    @Schema(
            description =
                "Leer für einen sichtbaren Vorfahren; PAPIERKORB, wenn er im Papierkorb liegt"
                    + " (die Kette geht weiter), GELOESCHT, wenn er endgültig gelöscht ist (die"
                    + " Kette endet).",
            example = "PAPIERKORB")
        @Nullable Luecke luecke) {

  /** Art einer Lücke in der Herkunft. */
  public enum Luecke {
    /** Der Vorfahr liegt im Papierkorb; über ihn geht die Kette weiter. */
    PAPIERKORB,
    /** Der Vorfahr ist endgültig gelöscht; nur seine Nummer ist bewahrt, die Kette endet. */
    GELOESCHT
  }
}
