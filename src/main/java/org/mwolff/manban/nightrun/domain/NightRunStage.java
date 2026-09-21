package org.mwolff.manban.nightrun.domain;

/**
 * Die Stufe der Nacht-Kette, in der ein Vorgang gelaufen ist (Issue #1112, Plan #1110).
 *
 * <p>Die vier Namen sind die der Zeitvorgaben aus {@code workflow.config.json} unter {@code
 * night.kette} — {@code planMin}, {@code reviewMin}, {@code paketeMin}, {@code abdeckungMin}: Wer
 * eine Kennzahl je Stufe neben ihre Vorgabe stellen will, braucht beide unter demselben Namen.
 */
public enum NightRunStage {

  /** Der Fachplan der Kette. */
  PLAN,

  /** Das Review des Plans. */
  REVIEW,

  /** Die Zerlegung in Arbeitspakete. */
  PAKETE,

  /** Die Abdeckung — der abschließende Durchgang über die Pakete. */
  ABDECKUNG
}
