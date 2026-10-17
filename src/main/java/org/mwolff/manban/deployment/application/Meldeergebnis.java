package org.mwolff.manban.deployment.application;

/** Was eine Meldung bewirkt hat (Issue #1833, Plan #1831 E3). */
public enum Meldeergebnis {
  /** Der Versuch ist neu und gespeichert. */
  CREATED,
  /** Der Versuch lag mit gleichem Inhalt schon vor; nichts hat sich geändert. */
  UNCHANGED
}
