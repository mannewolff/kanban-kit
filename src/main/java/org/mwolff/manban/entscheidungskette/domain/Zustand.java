package org.mwolff.manban.entscheidungskette.domain;

/** Ob ein Glied der Kette erfasst ist (Plan #2108, Kriterien 5 und 6). */
public enum Zustand {
  /** Das Glied liegt vor; einzelne Angaben können trotzdem fehlen. */
  ERFASST,
  /** Das Glied fehlt oder lässt sich nicht belegen. */
  NICHT_ERFASST,
  /** Eine feste Angabe der Karte belegt, dass der Weg dieses Glied nicht vorsieht. */
  NICHT_VORGESEHEN
}
