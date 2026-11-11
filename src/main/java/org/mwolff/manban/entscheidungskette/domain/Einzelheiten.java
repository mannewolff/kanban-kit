package org.mwolff.manban.entscheidungskette.domain;

/**
 * Die Angaben eines Glieds am Paket (Issue #2116): Freigabe, Umsetzung, Code-Review, Übertragung
 * oder Veröffentlichung.
 */
public sealed interface Einzelheiten
    permits Freigabeangaben, Umsetzungsangaben, CodeReview, Uebertragung, Veroeffentlichung {

  /** Ob das Glied als erreicht zählt (Plan #2108 E15); ohne Gegenangabe ja. */
  default boolean erreicht() {
    return true;
  }
}
