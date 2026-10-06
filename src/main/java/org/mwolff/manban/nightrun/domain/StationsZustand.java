package org.mwolff.manban.nightrun.domain;

/** Zustand einer Station der Stufenleiste (Issue #1451, Plan #1447 E5). */
public enum StationsZustand {

  /** Die Station ist abgeschlossen. */
  ERLEDIGT,

  /** Die Kette arbeitet gerade an dieser Station. */
  LAEUFT,

  /** Die Kette steht an dieser Station und wartet — der Grund steht daneben. */
  WARTET,

  /** Die Kette ist an dieser Station abgebrochen. */
  ABGEBROCHEN,

  /** Die Station liegt auf dem Weg, ist aber noch nicht erreicht. */
  STEHT_AUS,

  /** Die Station liegt hinter dem Ziel der Kette. */
  NICHT_VORGESEHEN,

  /** Die Station war schon vor dem Lauf erbracht — Plan und Prüfung bei einem Plan als Start. */
  VOR_DEM_LAUF_ERBRACHT
}
