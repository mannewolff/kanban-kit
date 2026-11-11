package org.mwolff.manban.entscheidungskette.domain;

/** Die Art eines Glieds der Kette (Kriterium 4), in der Folge des Wegs. */
public enum Gliedart {
  /** Das Ergebnis der Idee, die auf die Kette verweist. */
  IDEE,
  /** Die Herkunft eines Dokuments mit dem Modell, das es geschrieben hat. */
  HERKUNFT,
  /** Eine Prüfrunde eines Dokuments. */
  PRUEFRUNDE,
  /** Die Einarbeitung einer Prüfrunde. */
  EINARBEITUNG,
  /** Eine Runde der Prüfung des Pakets vor Ready. */
  ISSUE_REVIEW,
  /** Die Freigabe zur Umsetzung. */
  FREIGABE,
  /** Ein Versuch der Umsetzung. */
  UMSETZUNG,
  /** Ein Code-Review. */
  CODE_REVIEW,
  /** Ein Versuch der Übertragung auf den Hauptzweig. */
  UEBERTRAGUNG,
  /** Ein Versuch der Veröffentlichung. */
  VEROEFFENTLICHUNG
}
