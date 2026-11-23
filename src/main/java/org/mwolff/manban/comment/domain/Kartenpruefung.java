package org.mwolff.manban.comment.domain;

import java.util.Optional;
import org.mwolff.manban.common.KitMeldungen.Wert;
import org.mwolff.manban.common.KitMeldungen.Zeile;

/**
 * Der Kommentar {@code ## Kartenprüfung}/{@code ## Card check}, mit dem das Kit das Ergebnis der
 * Prüfung je Karte meldet, und ob er einem Kunden verborgen bleibt (Issue #2214, Plan #2206 E2,
 * Fachplan #2202 Kriterium 5).
 *
 * <p>Verborgen ist eine Kartenprüfung, deren erste Zeile {@code Ergebnis:}/{@code Result:} nicht
 * den Wert {@link Wert#GRUEN} trägt; eine fehlende Zeile verbirgt ebenfalls — was nicht als grün
 * erkannt ist, gilt als Störung. Der Kopf steht allein auf der ersten nichtleeren Zeile, wie {@code
 * istKartenpruefungKommentar} im Kit ihn erkennt; führender Leerraum und die Schreibung zählen
 * nicht.
 */
public final class Kartenpruefung {

  private Kartenpruefung() {}

  /** Ob der Text ein Kommentar {@code ## Kartenprüfung} ist, gleich in welcher Sprache. */
  public static boolean istKartenpruefung(String text) {
    return text.lines()
        .filter(zeile -> !zeile.isBlank())
        .findFirst()
        .flatMap(Zeile.KARTENPRUEFUNG::rest)
        .filter(String::isBlank)
        .isPresent();
  }

  /**
   * Ob der Kommentar einem Aufrufer ohne das Recht auf Störungen verborgen bleibt: eine
   * Kartenprüfung, deren erste Ergebniszeile nicht grün meldet oder fehlt. Jeder andere Kommentar
   * bleibt sichtbar.
   */
  public static boolean istVerborgen(String text) {
    return istKartenpruefung(text) && !Wert.GRUEN.bezeichnet(ergebnis(text).orElse(null));
  }

  /** Der Wert hinter der ersten Zeile {@code Ergebnis:}/{@code Result:}; leer ohne eine solche. */
  private static Optional<String> ergebnis(String text) {
    return text.lines().map(Zeile.ERGEBNIS::rest).flatMap(Optional::stream).findFirst();
  }
}
