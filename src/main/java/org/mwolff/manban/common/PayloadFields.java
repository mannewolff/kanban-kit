package org.mwolff.manban.common;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Verlustfreie Kodierung einer festen Feldliste in einen einzelnen Text und zurück — für
 * Outbox-Payloads (Issue #502).
 *
 * <p>Bewusst URL-Encoding je Feld plus Zeilenumbruch als Trenner statt JSON: Die Payloads sind
 * kleine, positionsfeste String-Tupel, und dieses Format hat keinen Fehlerpfad beim Kodieren —
 * jeder Zweig ist testbar, nichts muss einen nie eintretenden Serialisierungsfehler behandeln.
 * {@link URLEncoder} erzeugt selbst nie einen Zeilenumbruch, der Trenner ist damit kollisionsfrei.
 */
public final class PayloadFields {

  private PayloadFields() {}

  /** Kodiert die Felder in einen einzelnen Text; Reihenfolge ist Teil des Vertrags. */
  public static String join(String... fields) {
    return Arrays.stream(fields)
        .map(field -> URLEncoder.encode(field, StandardCharsets.UTF_8))
        .collect(Collectors.joining("\n"));
  }

  /**
   * Dekodiert einen mit {@link #join} erzeugten Text.
   *
   * @throws IllegalArgumentException wenn die Feldzahl nicht stimmt — eine kaputte Payload soll als
   *     Fehlversuch enden, nicht als stilles Falsch-Parsen
   */
  public static List<String> split(String payload, int expectedCount) {
    String[] parts = payload.split("\n", -1);
    if (parts.length != expectedCount) {
      throw new IllegalArgumentException(
          "Payload hat " + parts.length + " Felder, erwartet " + expectedCount);
    }
    return Arrays.stream(parts)
        .map(part -> URLDecoder.decode(part, StandardCharsets.UTF_8))
        .toList();
  }
}
