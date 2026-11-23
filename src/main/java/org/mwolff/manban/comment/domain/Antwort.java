package org.mwolff.manban.comment.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitMeldungen;

/**
 * Eine Antwort auf eine Rückfrage (Issue #2210, Plan #2206 E8, E9; Vertrag V1): ein Kommentar mit
 * dem Kopf {@code ## Antwort} bzw. {@code ## Answer} und einem Block {@code json kit-antwort}.
 *
 * @param vorlage Kennung des Kommentars der Rückfrage
 * @param ja ob alle Empfehlungen übernommen werden; {@code antworten} nennt dann nur Abweichungen
 * @param antworten die Einzelantworten
 * @param weg Nummer des gewählten Wegs; {@code null} ohne
 * @param commit der freigegebene Stand bei einer Freigabe; sonst {@code null}
 */
public record Antwort(
    long vorlage,
    boolean ja,
    List<Einzelantwort> antworten,
    @Nullable Integer weg,
    @Nullable String commit) {

  private static final String KENNUNG = "kit-antwort";
  private static final Pattern KENNUNG_ALS_TEXT = Pattern.compile("\\d{1,18}");
  private static final String NR = "nr";
  private static final String WAHL = "wahl";
  private static final String TEXT = "text";
  private static final String ZURUECKSTELLEN = "zurueckstellen";

  public Antwort {
    antworten = List.copyOf(antworten);
  }

  /**
   * Die Antwort auf eine Frage: genau eines von gewählter Möglichkeit, eigenem Text und
   * Zurückstellen.
   *
   * @param nr Nummer des Eintrags der Rückfrage
   * @param wahl Nummer der gewählten Möglichkeit, ab 1; sonst {@code null}
   * @param text eigene Antwort; sonst {@code null}
   * @param zurueckstellen ob die Frage zurückgestellt ist
   */
  public record Einzelantwort(
      int nr, @Nullable Integer wahl, @Nullable String text, boolean zurueckstellen) {

    public Einzelantwort {
      int angaben = (wahl != null ? 1 : 0) + (text != null ? 1 : 0) + (zurueckstellen ? 1 : 0);
      if (angaben != 1 || (text != null && text.isBlank())) {
        throw new IllegalArgumentException(
            "Eine Einzelantwort trägt genau eines von Wahl, eigenem Text und Zurückstellen");
      }
    }

    /** Die Möglichkeit {@code wahl} ist gewählt. */
    public static Einzelantwort gewaehlt(int nr, int wahl) {
      return new Einzelantwort(nr, wahl, null, false);
    }

    /** Eine eigene Antwort. */
    public static Einzelantwort eigene(int nr, String text) {
      return new Einzelantwort(nr, null, text, false);
    }

    /** Die Frage ist zurückgestellt („Später“). */
    public static Einzelantwort zurueckgestellt(int nr) {
      return new Einzelantwort(nr, null, null, true);
    }

    private ObjectNode alsJson() {
      ObjectNode knoten = JsonNodeFactory.instance.objectNode().put(NR, nr);
      if (zurueckstellen) {
        return knoten.put(ZURUECKSTELLEN, true);
      }
      if (wahl != null) {
        return knoten.put(WAHL, wahl);
      }
      return knoten.put(TEXT, text);
    }
  }

  /**
   * Liest die Antwort aus dem Text eines Kommentars. Leer, wenn die erste nichtleere Zeile nicht
   * der Kopf ist, der Block fehlt oder kein Objekt mit lesbarer {@code vorlage} ist; die Kennung
   * darf als Zahl oder als Text aus Ziffern stehen. Einzelantworten ohne ganzzahlige Nummer oder
   * ohne Angabe fallen weg.
   */
  public static Optional<Antwort> lies(String text) {
    List<String> zeilen = text.lines().toList();
    int kopf = 0;
    while (kopf < zeilen.size() && zeilen.get(kopf).isBlank()) {
      kopf++;
    }
    if (kopf == zeilen.size()
        || Kopfsprache.vonKopf(KitMeldungen.Zeile.ANTWORT, zeilen.get(kopf)).isEmpty()) {
      return Optional.empty();
    }
    return KitBlock.finde(zeilen, kopf + 1, KENNUNG)
        .flatMap(fund -> KitBlock.alsJson(fund.inhalt()))
        .flatMap(Antwort::ausJson);
  }

  private static Optional<Antwort> ausJson(JsonNode block) {
    JsonNode vorlage = block.path("vorlage");
    String alsText = vorlage.textValue();
    Long kennung = null;
    if (vorlage.isIntegralNumber() && vorlage.canConvertToLong()) {
      kennung = vorlage.longValue();
    } else if (alsText != null && KENNUNG_ALS_TEXT.matcher(alsText).matches()) {
      kennung = Long.parseLong(alsText);
    }
    if (kennung == null) {
      return Optional.empty();
    }
    List<Einzelantwort> antworten = new ArrayList<>();
    for (JsonNode knoten : block.path("antworten")) {
      einzelantwort(knoten).ifPresent(antworten::add);
    }
    JsonNode weg = block.path("weg");
    JsonNode commit = block.path("commit");
    return Optional.of(
        new Antwort(
            kennung,
            block.path("ja").booleanValue(),
            antworten,
            weg.isInt() ? weg.intValue() : null,
            commit.isTextual() && !commit.asText().isBlank() ? commit.asText() : null));
  }

  private static Optional<Einzelantwort> einzelantwort(JsonNode knoten) {
    JsonNode nr = knoten.path(NR);
    if (!nr.isInt()) {
      return Optional.empty();
    }
    if (knoten.path(ZURUECKSTELLEN).booleanValue()) {
      return Optional.of(Einzelantwort.zurueckgestellt(nr.intValue()));
    }
    JsonNode wahl = knoten.path(WAHL);
    if (wahl.isInt()) {
      return Optional.of(Einzelantwort.gewaehlt(nr.intValue(), wahl.intValue()));
    }
    JsonNode text = knoten.path(TEXT);
    if (text.isTextual() && !text.asText().isBlank()) {
      return Optional.of(Einzelantwort.eigene(nr.intValue(), text.asText()));
    }
    return Optional.empty();
  }

  /**
   * Der Text des Kommentars: Kopf in der Sprache der Rückfrage (E9), darunter der Block in einer
   * Zeile. Leere Felder ({@code weg}, {@code commit}) fehlen.
   */
  public String alsKommentar(Kopfsprache sprache) {
    ObjectNode block = JsonNodeFactory.instance.objectNode();
    block.put("vorlage", vorlage);
    block.put("ja", ja);
    ArrayNode liste = block.putArray("antworten");
    antworten.forEach(a -> liste.add(a.alsJson()));
    if (weg != null) {
      block.put("weg", weg);
    }
    if (commit != null) {
      block.put("commit", commit);
    }
    return sprache.kopf(KitMeldungen.Zeile.ANTWORT)
        + "\n\n```json "
        + KENNUNG
        + "\n"
        + block
        + "\n```\n";
  }
}
