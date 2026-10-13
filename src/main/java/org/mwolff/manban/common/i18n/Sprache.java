package org.mwolff.manban.common.i18n;

import java.util.Locale;

/**
 * Die Sprachen, in denen der Leitstand erscheint (Issue #1639, Plan #1638). Der Code ist die Form,
 * in der die Sprache gespeichert und über die API übertragen wird; die Locale bestimmt Meldungen,
 * Mails und die Schreibweise von Datum und Zahlen.
 */
public enum Sprache {
  /** Deutsch — die Vorgabe jeder Installation. */
  DE("de", Locale.forLanguageTag("de-DE")),
  /** Englisch in US-Schreibweise. */
  EN("en", Locale.forLanguageTag("en-US"));

  private final String kennung;
  private final Locale gebiet;

  Sprache(String kennung, Locale gebiet) {
    this.kennung = kennung;
    this.gebiet = gebiet;
  }

  /** Der Code, wie er gespeichert und übertragen wird ({@code de}, {@code en}). */
  public String code() {
    return kennung;
  }

  /** Die Locale für Meldungen und Formatierung. */
  public Locale locale() {
    return gebiet;
  }

  /**
   * Die Sprache zum Code; nur die exakten Codes {@code de} und {@code en} gelten.
   *
   * @throws IllegalArgumentException bei jedem anderen Wert
   */
  public static Sprache ausCode(String code) {
    for (Sprache sprache : values()) {
      if (sprache.kennung.equals(code)) {
        return sprache;
      }
    }
    throw new IllegalArgumentException("Unbekannte Sprache: " + code);
  }
}
