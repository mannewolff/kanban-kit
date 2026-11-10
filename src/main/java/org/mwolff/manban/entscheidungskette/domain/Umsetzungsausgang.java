package org.mwolff.manban.entscheidungskette.domain;

/** Wie ein Versuch der Umsetzung endete (Plan #2108 E10). */
public enum Umsetzungsausgang {
  /** Mit einem Abschlussbericht. */
  ABGESCHLOSSEN,
  /** Mit einer Bewegung zurück nach Backlog oder Ready. */
  ZURUECKGEZOGEN,
  /** Noch ohne Abschlussbericht und ohne Rückzug. */
  OFFEN
}
