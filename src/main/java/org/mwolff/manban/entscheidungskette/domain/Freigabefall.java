package org.mwolff.manban.entscheidungskette.domain;

/** Welcher Fall der Freigabe zur Umsetzung galt (Kriterium 8, Plan #2108 E11). */
public enum Freigabefall {
  /** Der Zug nach Ready ist selbst die Freigabe. */
  ZUG_NACH_READY,
  /**
   * Ein Ziel ab „Umsetzung“ oder das Durchziehen-Kennzeichen am Fachplan oder Plan ist die
   * Freigabe, der Zug eines Laufs nach Ready ihre Ausführung.
   */
  KENNZEICHEN
}
