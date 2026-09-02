package org.mwolff.manban.nightrun.domain;

/**
 * Ausgang eines Arbeitspakets in einem Nachtlauf.
 *
 * <p>Übernommen aus dem Parser {@code frontend/src/lib/nightRunLog.ts} (Plan #718, A13), nicht neu
 * erfunden; {@code NightRunErrorClassSyncTest} hält die Werte an der Migration gegen.
 */
public enum NightRunState {

  /** Erfolgreich abgeschlossen; trägt keine Fehlerklasse. */
  GREEN,

  /** Abgeschlossen, aber mit Vorbehalt — etwa rote Pflichtchecks nach erfolgreicher Session. */
  YELLOW,

  /** Nicht abgeschlossen. */
  RED,

  /** Übergangen: Der Lauf hat das Paket nicht angefasst. */
  GREY
}
