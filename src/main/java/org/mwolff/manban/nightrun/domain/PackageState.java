package org.mwolff.manban.nightrun.domain;

/**
 * Zustand eines Arbeitspakets im Lauf (Issue #1374, Plan #1372 E6): aus dem heutigen Status der
 * Karte, sofern der Lauf sie bewegt hat.
 */
public enum PackageState {

  /** Vom Lauf nur angelegt, nicht bewegt. */
  ANGELEGT,

  /** Nach Ready gezogen — der Runner zieht jedes Paket vor seiner Session selbst dorthin. */
  GEZOGEN,

  /** In Umsetzung (In progress). */
  IN_UMSETZUNG,

  /** Fertig umgesetzt (In review oder Done). */
  FERTIG,

  /** Zurück ins Backlog gestellt — ein Fehlschlag. */
  ZURUECKGESTELLT
}
