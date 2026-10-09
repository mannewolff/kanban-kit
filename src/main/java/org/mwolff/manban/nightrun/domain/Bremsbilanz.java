package org.mwolff.manban.nightrun.domain;

import org.jspecify.annotations.Nullable;

/**
 * Was die Bremse des Kits gespart hat (Issue #1546, Plan #1547).
 *
 * <p>Die Rechenregel steht nur hier: Die Lesesicht eines Pakets nutzt sie schon (Issue #1549), die
 * Summe über einen Zeitraum folgt im Verbrauchsabruf.
 */
public final class Bremsbilanz {

  private Bremsbilanz() {}

  /**
   * Die geschätzte gesparte Zeit eines Pakets in Millisekunden: die Zeitgrenze seiner Sitzung
   * abzüglich seiner Laufzeit, nie unter 0 (E7).
   *
   * @return {@code null} für ein Paket ohne {@link NightRunErrorClass#STUCK} und für eine Bremsung
   *     ohne Zeitwert — ohne gemeldete Zeitgrenze oder ohne Laufzeit (E6)
   */
  public static @Nullable Long gespart(NightRunItem item) {
    NightRunStuck stuck = item.stuck();
    Long laufzeit = item.durationMs();
    if (item.errorClass() != NightRunErrorClass.STUCK || stuck == null || laufzeit == null) {
      return null;
    }
    Long zeitgrenze = stuck.sessionLimitMs();
    return zeitgrenze == null ? null : Math.max(0L, zeitgrenze - laufzeit);
  }
}
