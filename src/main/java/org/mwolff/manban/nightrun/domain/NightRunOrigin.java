package org.mwolff.manban.nightrun.domain;

/** Woher ein Nachtlauf ans Board kam (Issue #944). */
public enum NightRunOrigin {

  /** Ein Mensch hat einen Ergebnisstand im Browser eingelesen und abgeschickt. */
  UPLOAD,

  /** Eine Maschine hat ihn mit einem projektgebundenen Zugriffstoken gemeldet. */
  TOKEN
}
