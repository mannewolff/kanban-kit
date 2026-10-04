package org.mwolff.manban.nightrun.domain;

/** Zustand einer Stufe im Weg einer Kette (Issue #1374, Plan #1372 E4). */
public enum StageState {

  /** Noch nicht erreicht und nicht die aktuelle Stelle. */
  OFFEN,

  /** Die aktuelle Stelle des Wegs, noch nicht erreicht. */
  LAEUFT,

  /** Erreicht. */
  ERREICHT
}
