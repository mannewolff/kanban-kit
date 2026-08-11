package org.mwolff.manban.common;

/**
 * Längengrenzen für lange Freitexte — Karten-Beschreibungen und Kommentare (Issue #572).
 *
 * <p>Modulübergreifend, weil {@code card} und {@code comment} getrennte Module sind und dieselbe
 * Grenze tragen müssen: Zwei Konstanten mit demselben Zahlenwert laufen bei der nächsten Änderung
 * auseinander, und genau das war der Zustand davor — Kommentare waren auf 10.000 begrenzt,
 * Beschreibungen serverseitig gar nicht, und die Oberfläche kappte still bei 10.000.
 *
 * <p><strong>Zählweise:</strong> Ein „Zeichen" ist eine UTF-16-Codeeinheit — die Einheit, die
 * {@code String.length()} in Java und {@code String.prototype.length} in JavaScript ohne Zusatzcode
 * teilen, und die Bean Validation über {@code @Size} ohnehin prüft. Ein Emoji außerhalb der BMP
 * zählt damit als zwei. Das ist bewusst nicht die PostgreSQL-Zählweise ({@code length()} zählt
 * Codepoints); wer den Bestand gegen diese Grenze misst, nimmt {@code octet_length}, das als
 * UTF-8-Byte-Zahl immer mindestens so groß ist wie die UTF-16-Länge.
 */
public final class TextLimits {

  /**
   * Obergrenze für Beschreibungen und Kommentare.
   *
   * <p>Die frühere Grenze von 10.000 war an der Größenordnung „ein Block Fließtext" bemessen. Das
   * trug, solange Menschen die Texte tippten; seit der Tracker seine eigenen Issue-Bodies,
   * Reviewberichte und Abschlussberichte aufnimmt, sind fünfstellige Längen der Normalfall.
   */
  public static final int MAX_TEXT = 50_000;

  private TextLimits() {}
}
