package org.mwolff.manban.backup.domain;

/**
 * Das Gesamturteil über den Stand der Sicherung (Issue #826, Plan #825 E6).
 *
 * <p>{@link #ABGESCHALTET} steht bewusst neben {@link #FEHLGESCHLAGEN} und ist kein Spezialfall
 * davon: Die Oberfläche soll „läuft nicht" von „ist kaputt" unterscheiden können, ohne den
 * Unterschied zu erraten. Ausgeliefert wird ohne eingeschaltete Sicherung — niemand zahlt ungefragt
 * Rechenlast, und niemand wiegt sich versehentlich in Sicherheit.
 */
public enum BackupVerdict {

  /** Jede Art hat einen gelungenen Lauf innerhalb ihrer Warnfrist. */
  OK,

  /** Mindestens eine Art schweigt länger als ihre aus dem Rhythmus abgeleitete Warnfrist. */
  VERALTET,

  /** Der jüngste Lauf mindestens einer Art ist gescheitert. */
  FEHLGESCHLAGEN,

  /** Es läuft keine Sicherung — {@code manban.backup.enabled} ist aus. */
  ABGESCHALTET
}
