package org.mwolff.manban.card.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Art;

/**
 * Regeln rund um das Arbeitspaket (Plan #1294): welche Karte eines ist, welche Spalte eine
 * Prozessspalte ist und wann eine Karte als erledigt gilt.
 *
 * <p>Die Spaltenregel {@link #statusVonSpalte} ist auch die der Kanban-kompatiblen Strecke: {@code
 * kanbancompat} erreicht sie über {@code CardIngestService.spaltenSchluessel}, weil es nicht auf
 * {@code card.domain} zugreifen darf ({@code ArchitectureTest.CARD_DOMAIN_IST_MODULINTERN}, Issue
 * #1495).
 */
public final class Arbeitspaket {

  /**
   * Die drei Dokumentarten (E3), deren Präfixe {@link KitNamen#artAusTitel} in beiden Sprachen
   * liest (Issue #1587). {@code MENSCH} fehlt mit Absicht: Eine Menschenkarte ist ein Arbeitspaket
   * (E4).
   */
  private static final Set<Art> DOKUMENTARTEN = Set.of(Art.IDEE, Art.FACHLICH, Art.PLAN);

  private static final Pattern KEIN_BUCHSTABE = Pattern.compile("[^a-z]");

  private Arbeitspaket() {}

  /** Eine Karte vom Typ {@code CARD}, deren Titel nicht mit einem Dokumentpräfix beginnt. */
  public static boolean istArbeitspaket(CardType type, String titel) {
    return type == CardType.CARD
        && KitNamen.artAusTitel(titel).filter(DOKUMENTARTEN::contains).isEmpty();
  }

  /**
   * Der Status, den eine Prozessspalte dieses Namens bedeutet; leer bei jeder eigenen Spalte (E5).
   * Kleinschreibung, alles außer {@code a-z} entfernen, dann exakter Abgleich — „Wartet auf
   * Zulieferung" bleibt so zuverlässig draußen.
   */
  public static Optional<CardStatus> statusVonSpalte(@Nullable String name) {
    if (name == null) {
      return Optional.empty();
    }
    String n = KEIN_BUCHSTABE.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("");
    return switch (n) {
      case "backlog" -> Optional.of(CardStatus.BACKLOG);
      case "ready" -> Optional.of(CardStatus.READY);
      case "inprogress" -> Optional.of(CardStatus.IN_PROGRESS);
      case "inreview" -> Optional.of(CardStatus.IN_REVIEW);
      case "done" -> Optional.of(CardStatus.DONE);
      default -> Optional.empty();
    };
  }

  /**
   * Der Status nach der Lage der Zielspalte (Issue #1474): Die Liste nennt die Spalten des Boards
   * von links bis einschließlich der Zielspalte. Den Status gibt die nächste Prozessspalte von
   * rechts vor — die Zielspalte selbst, wenn sie eine ist, sonst die nächste links davon. Gibt es
   * keine, gilt {@code BACKLOG}.
   */
  public static CardStatus statusNachLage(List<String> spaltenBisZiel) {
    for (int i = spaltenBisZiel.size() - 1; i >= 0; i--) {
      Optional<CardStatus> status = statusVonSpalte(spaltenBisZiel.get(i));
      if (status.isPresent()) {
        return status.get();
      }
    }
    return CardStatus.BACKLOG;
  }

  /**
   * Ob die Karte als erledigt gilt (E7): Trägt sie einen Status, entscheidet er; sonst wie bisher
   * die Spalte über die Substring-Regel {@code contains("done")}, die für Vorhaben und
   * Dokumentarten unverändert bleibt (E6).
   */
  public static boolean effektivDone(Card card, @Nullable String spaltenname) {
    CardStatus status = card.status();
    if (status != null) {
      return status == CardStatus.DONE;
    }
    return spaltenname != null && spaltenname.toLowerCase(Locale.ROOT).contains("done");
  }
}
