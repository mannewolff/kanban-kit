package org.mwolff.manban.nightrun.domain;

/**
 * Betriebsart, in der ein Nachtlauf gestartet wurde.
 *
 * <p>Übernommen aus dem Parser {@code frontend/src/lib/nightRunLog.ts} (Plan #718, A13), nicht neu
 * erfunden; {@code NightRunErrorClassSyncTest} hält die Werte an der Migration gegen.
 */
public enum NightRunMode {

  /** Implementierungs-Lauf: Arbeitspakete aus Ready werden umgesetzt. */
  IMPLEMENTATION,

  /** Prüf-Lauf: Kandidaten aus dem Backlog werden begutachtet. */
  REVIEW
}
