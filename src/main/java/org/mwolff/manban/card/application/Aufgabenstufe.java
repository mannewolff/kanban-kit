package org.mwolff.manban.card.application;

import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Die Aufgabenstufe eines Arbeitspakets (Issue #2001, Plan #1990 E5, fachlich #1954 AK 5 und 7).
 *
 * <p>Das Board speichert die Stufe nicht; das Kit schreibt sie als Zeile {@code Aufgabenstufe:} in
 * die Beschreibung, in beiden Sprachen mit diesem Wortlaut. Gelesen wird darum wie {@code
 * aufgabenStufe} im Kit. Das Enum liegt in {@code card.application} und nicht in {@code
 * card.domain}, weil {@code nightrun} es braucht und {@code card.domain} modulintern ist.
 */
public enum Aufgabenstufe {
  LEICHT,
  MITTEL,
  SCHWER;

  private static final String MARKER = "Aufgabenstufe:";

  /**
   * Die Stufe aus der ersten Zeile, die mit {@code Aufgabenstufe:} beginnt. Ihr Rest muss nach dem
   * Trimmen genau {@code leicht}, {@code mittel} oder {@code schwer} sein, ohne Rücksicht auf Groß-
   * und Kleinschreibung; jede weitere solche Zeile zählt nicht.
   *
   * @return die Stufe; leer ohne Beschreibung, ohne Zeile oder bei einem anderen Rest
   */
  public static Optional<Aufgabenstufe> ausBeschreibung(@Nullable String beschreibung) {
    if (beschreibung == null) {
      return Optional.empty();
    }
    return beschreibung
        .lines()
        .filter(zeile -> zeile.startsWith(MARKER))
        .findFirst()
        .flatMap(zeile -> stufe(zeile.substring(MARKER.length()).trim()));
  }

  private static Optional<Aufgabenstufe> stufe(String wert) {
    return switch (wert.toLowerCase(Locale.ROOT)) {
      case "leicht" -> Optional.of(LEICHT);
      case "mittel" -> Optional.of(MITTEL);
      case "schwer" -> Optional.of(SCHWER);
      default -> Optional.empty();
    };
  }
}
