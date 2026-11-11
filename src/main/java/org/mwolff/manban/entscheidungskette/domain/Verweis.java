package org.mwolff.manban.entscheidungskette.domain;

import org.jspecify.annotations.Nullable;

/**
 * Die Quelle eines Glieds: eine Karte oder ein Kommentar an ihr (Kriterien 5 und 14, Plan #2108
 * E12, E16). Ein Verweis erweitert keine Berechtigung.
 *
 * @param karte die ID der Karte; {@code null}, wenn sie nicht mehr im Bestand ist
 * @param nummer die Nummer der Karte; {@code null} bei einem anderen Projekt
 * @param titel der Titel; {@code null} bei Papierkorb, gelöscht und anderem Projekt
 * @param oeffenbar ob die Oberfläche die Karte öffnen darf; nie bei einer archivierten
 * @param kommentar die ID des Kommentars, wenn das Glied aus einem Kommentar stammt
 */
public record Verweis(
    @Nullable Long karte,
    @Nullable Integer nummer,
    @Nullable String titel,
    boolean oeffenbar,
    @Nullable Long kommentar) {

  /** Kein Verweis: ein Glied aus einem anderen Projekt oder ohne jede Angabe. */
  public static final Verweis KEINER = new Verweis(null, null, null, false, null);

  /** Nur die bewahrte Nummer einer Karte, die nicht mehr im Bestand ist. */
  static Verweis nurNummer(@Nullable Integer nummer) {
    return new Verweis(null, nummer, null, false, null);
  }
}
