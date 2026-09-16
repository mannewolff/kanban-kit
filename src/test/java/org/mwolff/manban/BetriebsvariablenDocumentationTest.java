package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Hält die in {@code docs/betrieb.md} dokumentierten Umgebungsvariablen an den {@code
 * environment:}-Block des Dienstes {@code manban-api} gekoppelt (Issue #905).
 *
 * <p>Der Anlass: Eine Variable, die im Compose-Block fehlt, erreicht den Container nicht. Ein
 * Betreiber, der nach der Anleitung eine Automatik abschaltet, erzielte damit keine Wirkung — und
 * nichts schlug an. Die Anleitung versprach etwas, das der Betrieb nicht einlöste.
 *
 * <p><b>Geprüft wird nur die Richtung Anleitung → Compose</b>, nicht umgekehrt: Im Compose-Block
 * stehen zusätzlich Variablen (etwa die SMTP-Zugangsdaten), die die Tabelle bewusst nicht führt.
 *
 * <p><b>Maßstab ist die dokumentierte Liste selbst</b>, keine im Test wiederholte Aufzählung —
 * sonst wüchse die Leitplanke bei der nächsten Variable nicht mit. Gesammelt wird ausschließlich
 * aus <b>Tabellenzeilen</b>: Der Fließtext nennt einzelne Variablen ebenfalls, und ein dort
 * erwähnter Name ist keine Zusicherung, dass er durchgereicht werden muss.
 */
class BetriebsvariablenDocumentationTest {

  private static final Path ANLEITUNG = Path.of("docs", "betrieb.md");
  private static final Path COMPOSE = Path.of("docker-compose.yml");

  /** Der Dienst, dessen Umgebung die Anwendung liest. */
  private static final String DIENST = "manban-api:";

  private static final Pattern VARIABLE = Pattern.compile("MANBAN_[A-Z0-9_]+");

  @Test
  void jedeDokumentierteVariableErreichtDenContainer() throws IOException {
    Set<String> dokumentiert =
        ausTabellenzeilen(Files.readAllLines(ANLEITUNG, StandardCharsets.UTF_8));
    String umgebung =
        umgebungsblockDesDienstes(Files.readAllLines(COMPOSE, StandardCharsets.UTF_8));

    List<String> fehlend =
        dokumentiert.stream().filter(name -> !umgebung.contains(name + ":")).sorted().toList();

    assertThat(fehlend)
        .as(
            "In %s dokumentiert, aber nicht im environment-Block von %s in %s",
            ANLEITUNG, DIENST, COMPOSE)
        .isEmpty();
  }

  /**
   * Die Variablennamen aus den Tabellenzeilen — alles, was nach {@code strip()} mit {@code |}
   * beginnt.
   */
  private static Set<String> ausTabellenzeilen(List<String> zeilen) {
    Set<String> namen = new LinkedHashSet<>();
    for (String zeile : zeilen) {
      if (!zeile.strip().startsWith("|")) {
        continue;
      }
      Matcher treffer = VARIABLE.matcher(zeile);
      while (treffer.find()) {
        namen.add(treffer.group());
      }
    }
    return namen;
  }

  /**
   * Der Abschnitt des Dienstes {@code manban-api} — vom Dienstschlüssel bis zum nächsten Eintrag
   * auf derselben Einrückung. Ohne diesen Schnitt zählte eine Variable auch dann als vorhanden,
   * wenn sie bei einem anderen Dienst steht.
   */
  private static String umgebungsblockDesDienstes(List<String> zeilen) {
    StringBuilder block = new StringBuilder();
    int einrueckung = -1;
    for (String zeile : zeilen) {
      if (einrueckung < 0) {
        if (DIENST.equals(zeile.strip())) {
          einrueckung = zeile.indexOf(DIENST.charAt(0));
        }
        continue;
      }
      boolean naechsterEintragAufGleicherEbene =
          !zeile.isBlank() && zeile.indexOf(zeile.strip().charAt(0)) <= einrueckung;
      if (naechsterEintragAufGleicherEbene) {
        break;
      }
      block.append(zeile).append('\n');
    }
    assertThat(einrueckung).as("Dienst %s nicht in %s gefunden", DIENST, COMPOSE).isNotNegative();
    return block.toString();
  }
}
