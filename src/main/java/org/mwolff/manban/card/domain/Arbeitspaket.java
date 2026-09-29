package org.mwolff.manban.card.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Regeln rund um das Arbeitspaket (Plan #1294): welche Karte eines ist, welche Spalte eine
 * Prozessspalte ist und wann eine Karte als erledigt gilt.
 *
 * <p>Die Spaltenregel {@link #statusVonSpalte} ist wortgleich zur kanonischen Regel in {@code
 * KanbanCompatService.canonicalKey}. Die Doppelung ist durch die Modulgrenze erzwungen: {@code
 * kanbancompat} darf nicht auf {@code card.domain} zugreifen ({@code
 * ArchitectureTest.CARD_DOMAIN_IST_MODULINTERN}). Wer die eine Seite ändert, ändert die andere mit.
 */
public final class Arbeitspaket {

  /**
   * Präfixe der drei Dokumentarten (E3) — wortgleich zu {@code .claude/kit/board.mjs}. {@code
   * [Mensch]} fehlt mit Absicht: Eine Menschenkarte ist ein Arbeitspaket (E4).
   */
  private static final Pattern DOKUMENT_PRAEFIX =
      Pattern.compile("^\\s*\\[(idee|fachlich|plan)\\]", Pattern.CASE_INSENSITIVE);

  private static final Pattern KEIN_BUCHSTABE = Pattern.compile("[^a-z]");

  private Arbeitspaket() {}

  /** Eine Karte vom Typ {@code CARD}, deren Titel nicht mit einem Dokumentpräfix beginnt. */
  public static boolean istArbeitspaket(CardType type, String titel) {
    return type == CardType.CARD && !DOKUMENT_PRAEFIX.matcher(titel).find();
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
