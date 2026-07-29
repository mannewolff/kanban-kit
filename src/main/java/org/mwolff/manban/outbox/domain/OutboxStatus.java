package org.mwolff.manban.outbox.domain;

/**
 * Zustand eines Outbox-Eintrags (Issue #501).
 *
 * <p>{@link #PENDING} und {@link #FAILED} unterscheiden sich fachlich deutlich: ein {@code
 * PENDING}-Eintrag wird vom Worker erneut versucht, ein {@code FAILED}-Eintrag nie wieder. {@code
 * FAILED} ist deshalb kein „weg", sondern ein dauerhaft sichtbarer Rückstand — die Aufbewahrung
 * räumt bewusst nur {@link #DONE} ab.
 */
public enum OutboxStatus {

  /** Offen: fällig ab {@code nextAttemptAt}, wird vom Worker abgearbeitet. */
  PENDING,

  /** Erfolgreich abgearbeitet. */
  DONE,

  /** Endgültig gescheitert (Versuchsobergrenze erreicht); wird nicht mehr versucht. */
  FAILED
}
