package org.mwolff.manban.nightrun.domain;

/** Ob sich die Karten eines Laufs überhaupt zuordnen lassen (Issue #1374, Plan #1372 E3). */
public enum ProgressAssignment {

  /** Zuordnung möglich; einzelne Karten können trotzdem „unbekannt" sein. */
  OK,

  /** Der Lauf hat keinen Token-Namen (Browser-Upload) — der ganze Fortschritt ist unbekannt. */
  UNBEKANNT
}
