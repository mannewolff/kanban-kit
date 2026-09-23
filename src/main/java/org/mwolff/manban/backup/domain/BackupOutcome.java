package org.mwolff.manban.backup.domain;

/** Ausgang eines Sicherungslaufs (Issue #826). */
public enum BackupOutcome {

  /** Der Lauf ist durchgelaufen. */
  ERFOLG,

  /** Der Lauf ist abgebrochen; der Grund steht in {@link BackupRun#detail()}. */
  FEHLSCHLAG
}
