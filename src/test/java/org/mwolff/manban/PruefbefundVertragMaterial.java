package org.mwolff.manban;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Zugriff auf die kanonischen Testdatensätze zum Prüfbefund-Vertrag Fassung 1 (Issue #585).
 *
 * <p>Bewusst getrennt von {@link PruefbefundVertragV1Test}: Dort stehen die Aussagen, hier das
 * Lesen. Das hält die Testklasse bei den Behauptungen, die sie belegt.
 *
 * <p>Das Lösen eines Blocks aus dem Kommentartext ist <strong>kein</strong> Validieren der
 * Grammatik — ein Block wird hier nie gültig oder ungültig, er wird nur auffindbar. Ohne diesen
 * Zugriff ließen sich Kennungen nicht vergleichen.
 */
final class PruefbefundVertragMaterial {

  static final Path DATEN_PFAD = Path.of("src", "test", "resources", "pruefbefund-vertrag", "v1");

  static final Path MANIFEST_PFAD = DATEN_PFAD.resolve("manifest.json");

  static final String START_VORSCHLAG = "```pruefbefund-vorschlaege";
  static final String START_PROTOKOLL = "```pruefbefund-protokoll";

  /** Endkennung beider Blockarten und zugleich der kürzeste zulässige Markdown-Fence. */
  private static final String ENDE = "```";

  private static final Pattern FENCE = Pattern.compile("^(`{3,}|~{3,})");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private PruefbefundVertragMaterial() {}

  /** Ein Datensatz des Manifests: eine Kommentardatei mit ihrer Erwartung. */
  record Fall(String datei, String blockart, String zieltext, String erwartet) {}

  static List<Fall> faelle() {
    List<Fall> faelle = new ArrayList<>();
    for (JsonNode knoten : manifest().path("faelle")) {
      faelle.add(
          new Fall(
              knoten.path("datei").asText(),
              knoten.path("blockart").asText(),
              knoten.hasNonNull("zieltext") ? knoten.get("zieltext").asText() : null,
              knoten.path("erwartet").asText()));
    }
    return faelle;
  }

  static List<String> zieltexte() {
    List<String> zieltexte = new ArrayList<>();
    for (JsonNode knoten : manifest().path("zieltexte")) {
      zieltexte.add(knoten.asText());
    }
    return zieltexte;
  }

  static Fall fallMit(String erwartet) {
    return faelle().stream()
        .filter(fall -> erwartet.equals(fall.erwartet()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Kein Testdatensatz für " + erwartet));
  }

  static Fall fallMitDatei(String datei) {
    return faelle().stream()
        .filter(fall -> datei.equals(fall.datei()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Kein Manifest-Eintrag für " + datei));
  }

  static String inhalt(String datei) {
    return lies(DATEN_PFAD.resolve(datei));
  }

  static List<String> dateinamenImVerzeichnis() {
    try (Stream<Path> dateien = Files.list(DATEN_PFAD)) {
      return dateien.map(pfad -> pfad.getFileName().toString()).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Der Rumpf eines Blocks, oder {@code null}, wenn kein vollständiges Kennungspaar außerhalb eines
   * umschließenden Codeblocks vorliegt oder der Rumpf kein JSON-Objekt ist.
   */
  static JsonNode rumpfOderNull(Fall fall, String startkennung) {
    String[] zeilen = zeilen(inhalt(fall.datei()));
    int start = startzeileOderMinusEins(zeilen, startkennung);
    if (start < 0 || start + 2 >= zeilen.length || !ENDE.equals(zeilen[start + 2])) {
      return null;
    }
    try {
      return MAPPER.readTree(zeilen[start + 1]);
    } catch (IOException e) {
      return null;
    }
  }

  /** Alle Vorschlagskennungen aller Datensätze, gruppiert nach Lauf-Kennung. */
  static Map<String, Set<String>> vorschlagskennungenJeLauf() {
    Map<String, Set<String>> jeLauf = new LinkedHashMap<>();
    for (Fall fall : faelle()) {
      JsonNode rumpf = rumpfOderNull(fall, START_VORSCHLAG);
      if (rumpf != null) {
        // Bewusst putIfAbsent statt computeIfAbsent: Der Lambda-Parameter wäre unbenutzt, und die
        // beiden Gates widersprechen sich dort — Error-Prone verlangt `_`, Checkstyle verbietet es.
        String lauf = lauf(rumpf);
        jeLauf.putIfAbsent(lauf, new HashSet<>());
        jeLauf.get(lauf).addAll(kennungen(rumpf));
      }
    }
    return jeLauf;
  }

  /** Die Vorschlagskennungen eines Blocks in Reihenfolge — Duplikate bleiben erhalten. */
  static List<String> kennungen(JsonNode vorschlagsblock) {
    List<String> kennungen = new ArrayList<>();
    for (JsonNode vorschlag : vorschlagsblock.path("proposals")) {
      kennungen.add(vorschlag.path("proposalId").asText());
    }
    return kennungen;
  }

  static String lauf(JsonNode block) {
    return block.path("runId").asText();
  }

  static List<String> protokollierteKennungen(JsonNode protokollblock) {
    List<String> kennungen = new ArrayList<>();
    for (JsonNode eintrag : protokollblock.path("entries")) {
      kennungen.add(eintrag.path("proposalId").asText());
    }
    return kennungen;
  }

  static String lies(Path pfad) {
    try {
      return Files.readString(pfad, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Die Zeilennummer der Startkennung, oder -1. Kennungen innerhalb eines umschließenden Fences
   * zählen nicht: Ein Kommentar, der das Format erklärt, wendet es nicht an.
   */
  private static int startzeileOderMinusEins(String[] zeilen, String startkennung) {
    String offenerFence = null;
    for (int i = 0; i < zeilen.length; i++) {
      if (offenerFence != null) {
        if (zeilen[i].startsWith(offenerFence)) {
          offenerFence = null;
        }
      } else if (startkennung.equals(zeilen[i])) {
        return i;
      } else {
        Matcher fence = FENCE.matcher(zeilen[i]);
        if (fence.find()) {
          offenerFence = fence.group(1);
        }
      }
    }
    return -1;
  }

  private static String[] zeilen(String text) {
    return text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
  }

  private static JsonNode manifest() {
    try {
      return MAPPER.readTree(lies(MANIFEST_PFAD));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
