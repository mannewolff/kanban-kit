package org.mwolff.manban.nightrun.domain;

/**
 * Grund, aus dem ein Arbeitspaket eines Nachtlaufs nicht grün abgeschlossen hat (Plan #718, A13).
 *
 * <p>Verbindliche Fassung dieser Liste ist der Parser {@code frontend/src/lib/nightRunLog.ts}; der
 * Java-Typ spiegelt sie. {@code NightRunErrorClassSyncTest} hält beide gleich — ohne diesen Wächter
 * liefe die Werteliste unbemerkt auseinander, und ein hochgeladener Auszug fiele erst an der {@code
 * CHECK}-Constraint der Datenbank auf.
 */
public enum NightRunErrorClass {

  /** Die Pflichtchecks des Pakets sind rot gelaufen. */
  CHECKS_RED,

  /** Die Pflichtchecks kamen nicht zum Laufen — die Session hat nichts hinterlassen. */
  CHECKS_NOT_STARTED,

  /** Eine Abhängigkeit des Pakets war nicht erfüllt; der Runner hat es zurückgestellt. */
  DEPENDENCY_UNMET,

  /** Das Paket steht nach der Session in einem Zustand, den der Runner nicht erwartet hat. */
  UNEXPECTED_STATE,

  /** Harter Abbruch: Der Lauf hat die Runde nicht sauber beenden können. */
  HARD_ABORT,

  /**
   * Das Paket trägt eine offene Entscheidung ({@code kit:klaeren}) und wartet auf einen Menschen.
   */
  AWAITING_DECISION,

  /** Die Prüf-Session ist gescheitert, ohne ein verwertbares Ergebnis zu hinterlassen. */
  REVIEWER_FAILED
}
