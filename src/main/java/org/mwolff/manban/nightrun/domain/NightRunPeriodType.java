package org.mwolff.manban.nightrun.domain;

/**
 * Die Arten eines Auswertungszeitraums (Issue #934, Plan #933 E18).
 *
 * <p>Genau drei. Quartal und Jahr sind ausdrücklich verworfen: Die Auswertung schaut auf Nächte und
 * ihre unmittelbare Umgebung, und die Aufbewahrung der Läufe reicht nicht über zwei Monate hinaus.
 */
public enum NightRunPeriodType {
  /** Ein Kalendertag in der gewählten Zone. */
  DAY,
  /** Eine Kalenderwoche nach ISO-8601, beginnend am Montag (Plan #933 E19). */
  WEEK,
  /** Ein Kalendermonat. */
  MONTH
}
