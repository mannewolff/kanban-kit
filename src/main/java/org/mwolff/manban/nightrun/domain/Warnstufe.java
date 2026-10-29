package org.mwolff.manban.nightrun.domain;

/** Warnstufe des Monatsbudgets (Issue #1966, fachliche Quelle #1957 AK 7). */
public enum Warnstufe {
  /** Unter 80 % des Budgets, ohne Budget oder ohne gemeldeten Verbrauch. */
  NONE,
  /** Ab 80 % des Budgets. */
  EIGHTY,
  /** Ab dem vollen Budget. */
  REACHED
}
