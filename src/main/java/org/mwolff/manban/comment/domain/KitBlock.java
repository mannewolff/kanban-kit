package org.mwolff.manban.comment.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Ein Codeblock eines Kommentars mit der Infozeile {@code json <kennung>} (Issue #2210, Vertrag
 * V1). Der Block endet an einer Zeile aus mindestens so vielen Backticks, wie er geöffnet wurde,
 * sonst am Textende.
 */
final class KitBlock {

  private static final char BACKTICK = '`';
  private static final int MIN_ZAUN = 3;
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern LEERRAUM = Pattern.compile("\\s+");

  private KitBlock() {}

  /**
   * Der erste Block mit dieser Kennung ab Zeile {@code ab}.
   *
   * @param zeilen die Zeilen des Kommentars
   * @param ab Index der ersten Zeile, die gesucht wird
   * @param kennung Kennung hinter {@code json}, etwa {@code kit-vorlage}
   */
  static Optional<Fund> finde(List<String> zeilen, int ab, String kennung) {
    for (int i = ab; i < zeilen.size(); i++) {
      String zeile = zeilen.get(i).strip();
      int zaun = zaunLaenge(zeile);
      if (zaun >= MIN_ZAUN && istKennung(zeile.substring(zaun), kennung)) {
        StringBuilder inhalt = new StringBuilder();
        for (int j = i + 1; j < zeilen.size() && !schliesst(zeilen.get(j), zaun); j++) {
          inhalt.append(zeilen.get(j)).append('\n');
        }
        return Optional.of(new Fund(i, inhalt.toString()));
      }
    }
    return Optional.empty();
  }

  /** Der Inhalt als JSON; leer, wenn er kein gültiges JSON ist. */
  static Optional<JsonNode> alsJson(String inhalt) {
    try {
      return Optional.of(MAPPER.readTree(inhalt));
    } catch (JsonProcessingException e) {
      return Optional.empty();
    }
  }

  private static boolean istKennung(String info, String kennung) {
    return LEERRAUM.matcher(info.strip()).replaceAll(" ").equals("json " + kennung);
  }

  private static boolean schliesst(String zeile, int zaun) {
    String roh = zeile.strip();
    return roh.length() >= zaun && zaunLaenge(roh) == roh.length();
  }

  private static int zaunLaenge(String zeile) {
    int n = 0;
    while (n < zeile.length() && zeile.charAt(n) == BACKTICK) {
      n++;
    }
    return n;
  }

  /**
   * Ein gefundener Block.
   *
   * @param beginn Index der öffnenden Zeile
   * @param inhalt die Zeilen zwischen öffnender und schließender Zeile
   */
  record Fund(int beginn, String inhalt) {}
}
