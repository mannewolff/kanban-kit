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
import org.mwolff.manban.common.automation.AutomationStatusContributor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Hält die Schalternamen aus dem Startprotokoll an {@code docs/betrieb.md} gekoppelt (Issue #909).
 *
 * <p>Ohne diesen Test entstünde neben der Anleitung und der {@code docker-compose.yml} eine
 * <b>dritte</b> Liste von Variablennamen — die im Protokoll genannten. Sie würde driften, und
 * gerade sie liest der Betreiber im Störungsfall: Ein Schaltername, den die Anleitung nicht führt,
 * schickt ihn an eine Stelle, die es nicht gibt.
 *
 * <p>Geprüft wird die Richtung <b>Protokoll → Anleitung</b> — die Gegenrichtung (Anleitung →
 * Compose) sichert {@code BetriebsvariablenDocumentationTest} aus Issue #905. Gesammelt wird wie
 * dort nur aus Tabellenzeilen: Eine Erwähnung im Fließtext ist keine Zusicherung.
 *
 * <p>Die Mindestzahl der gemeldeten Zustände ist Absicht: Fiele ein Contributor versehentlich weg,
 * wäre die Menge der Schalternamen leer — und ein Test über eine leere Menge ist immer grün.
 */
@SpringBootTest
class AutomationSchalterDocumentationTest extends AbstractIntegrationTest {

  private static final Path ANLEITUNG = Path.of("docs", "betrieb.md");
  private static final Pattern VARIABLE = Pattern.compile("MANBAN_[A-Z0-9_]+");

  /** card meldet zwei Automatiken, outbox zwei — weniger wäre ein weggefallener Beitrag. */
  private static final int MINDESTENS = 4;

  @Autowired private List<AutomationStatusContributor> contributors;

  @Test
  void jederGemeldeteSchalterStehtInDerAnleitung() throws IOException {
    List<String> schalter =
        contributors.stream()
            .flatMap(contributor -> contributor.statuses().stream())
            .map(status -> status.schalterVariable())
            .distinct()
            .toList();

    Set<String> dokumentiert =
        ausTabellenzeilen(Files.readAllLines(ANLEITUNG, StandardCharsets.UTF_8));

    assertThat(schalter)
        .as("Im Startprotokoll genannt, aber nicht in den Tabellen von %s", ANLEITUNG)
        .isSubsetOf(dokumentiert);
  }

  @Test
  void alleContributorenMeldenSich() {
    long gemeldet = contributors.stream().mapToLong(c -> c.statuses().size()).sum();

    assertThat(gemeldet)
        .as(
            "Gemeldete Automatiken — weniger als %d heisst, ein Beitrag ist weggefallen",
            MINDESTENS)
        .isGreaterThanOrEqualTo(MINDESTENS);
  }

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
}
