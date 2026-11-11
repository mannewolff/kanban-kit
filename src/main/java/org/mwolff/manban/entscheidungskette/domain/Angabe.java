package org.mwolff.manban.entscheidungskette.domain;

import java.time.DateTimeException;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitMeldungen.Wert;

/**
 * Eine Angabe der Form {@code <wert>, <zeitpunkt>}, etwa {@code gruen, 2026-11-10T10:00:00Z}.
 *
 * @param wert der Text; {@code null} ohne Angabe oder bei {@code nicht erfasst}
 * @param zeitpunkt der Zeitpunkt; {@code null} ohne lesbaren Zeitpunkt
 */
public record Angabe(@Nullable String wert, @Nullable Instant zeitpunkt) {

  /** Keine Angabe. */
  public static final Angabe LEER = new Angabe(null, null);

  private static final String TRENNER = ", ";

  /** Ob der Text der Angabe dieser feste Wert ist, gleich in welcher Sprache. */
  public boolean ist(Wert fest) {
    return fest.bezeichnet(wert);
  }

  /** Trennt am letzten Komma, weil ein Name selbst eines tragen kann. */
  static Angabe aus(@Nullable String roh) {
    if (roh == null) {
      return LEER;
    }
    int komma = roh.lastIndexOf(TRENNER);
    Instant zeitpunkt = komma < 0 ? null : zeitpunkt(roh.substring(komma + TRENNER.length()));
    return zeitpunkt == null
        ? new Angabe(text(roh), null)
        : new Angabe(text(roh.substring(0, komma)), zeitpunkt);
  }

  /** Der Text selbst; {@code null}, wenn er leer ist oder {@code nicht erfasst} sagt. */
  static @Nullable String text(@Nullable String roh) {
    return roh == null || roh.isBlank() || Wert.NICHT_ERFASST.bezeichnet(roh) ? null : roh;
  }

  private static @Nullable Instant zeitpunkt(String text) {
    try {
      return Instant.parse(text);
    } catch (DateTimeException keinZeitpunkt) {
      return null;
    }
  }
}
