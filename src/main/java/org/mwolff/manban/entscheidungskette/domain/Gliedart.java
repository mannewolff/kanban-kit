package org.mwolff.manban.entscheidungskette.domain;

/** Die Art eines Glieds der Kette (Kriterium 4). */
public enum Gliedart {
  /** Das Ergebnis der Idee, die auf die Kette verweist. */
  IDEE,
  /** Die Herkunft eines Dokuments mit dem Modell, das es geschrieben hat. */
  HERKUNFT,
  /** Eine Prüfrunde eines Dokuments. */
  PRUEFRUNDE,
  /** Die Einarbeitung einer Prüfrunde. */
  EINARBEITUNG
}
