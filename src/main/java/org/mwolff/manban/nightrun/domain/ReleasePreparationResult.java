package org.mwolff.manban.nightrun.domain;

/**
 * Ausgang der Veröffentlichungsvorbereitung eines Laufs (Issue #1456, Plan #1447 E12).
 *
 * <p>Die vier Werte legt der Kit-Vertrag fest (Kit A7); das Board deutet sie, es erfindet keine
 * eigenen.
 */
public enum ReleasePreparationResult {

  /** Der vorbereitete Stand ist grün und kann von Hand veröffentlicht werden. */
  GREEN,

  /** Grün, aber eine Prüfung steht noch aus — welche, nennen die offenen Einträge. */
  GREEN_PENDING,

  /** Eine Prüfung des vorbereiteten Stands ist fehlgeschlagen. */
  RED,

  /** Der Lauf hat keine Veröffentlichung vorbereitet. */
  NOT_PREPARED
}
