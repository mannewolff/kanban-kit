package org.mwolff.manban.deployment.domain;

import java.util.Arrays;

/**
 * Art einer gemeldeten Auslieferung (Issue #2215, Plan #2206 E10, Fachplan #2202 Kriterium 4). Eine
 * Meldung ohne Art ist eine Veröffentlichung, wie vor der Unterscheidung.
 */
public enum Auslieferungsart {
  /** Eine Vorschau: zählt in keiner Kennzahl und verschiebt den letzten gemeldeten Stand nicht. */
  VORSCHAU("vorschau"),
  /** Eine Veröffentlichung: zählt wie eine Auslieferung. */
  VEROEFFENTLICHUNG("veroeffentlichung");

  private final String text;

  Auslieferungsart(String text) {
    this.text = text;
  }

  /** Der Wert in Meldung und Datenbank. */
  public String wert() {
    return text;
  }

  /**
   * Die Art zum Wert aus Meldung oder Datenbank.
   *
   * @throws IllegalArgumentException bei einem unbekannten Wert
   */
  public static Auslieferungsart aus(String wert) {
    return Arrays.stream(values())
        .filter(art -> art.text.equals(wert))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unbekannte Auslieferungsart: " + wert));
  }
}
