package org.mwolff.manban.nightrun.domain;

/**
 * Die Stufen des Wegs einer Kette in ihrer Reihenfolge (Issue #1374, Plan #1372 E4) — dieselben
 * Namen wie die Stufen des Runners in {@code night.mjs}, groß geschrieben.
 */
public enum ProgressStage {

  /** Plan zur Anforderung anlegen. */
  PLAN,

  /** Plan prüfen. */
  REVIEW,

  /** Arbeitspakete anlegen. */
  PAKETE,

  /** Abdeckung der Pakete gegen die Anforderung prüfen. */
  ABDECKUNG,

  /** Pakete umsetzen — nur in Variante B ({@code kit:durchziehen}). */
  UMSETZUNG
}
