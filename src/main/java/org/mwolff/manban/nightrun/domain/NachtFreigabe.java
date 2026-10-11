package org.mwolff.manban.nightrun.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Art;
import org.mwolff.manban.common.KitNamen.Sache;

/**
 * Eine zur Übernahme freigegebene Karte mit ihrem kleinen Stufenstand — eine Zeile der Übersicht
 * „Heute Nacht“ (Issue #1454, Plan #1447 E4, E10).
 *
 * <p>Vor der Übernahme gibt es keinen Laufstand; abzuleiten sind allein Start und Ziel. Das Ziel
 * folgt denselben Vorgaben wie die Stufenleiste der Karte: ein {@code ziel:*}-Label, an einem Plan
 * nie {@code ziel:plan}; ohne Label „Arbeitspakete“, und mit {@code kit:durchziehen} mindestens
 * „Umsetzung“ (Kit A3). Ziel, Prüferzahl und Durchziehen gelten unter jedem Namen ihrer Sache in
 * {@link KitNamen} (Issue #1591).
 *
 * @param start wo die Kette beginnt — beim Fachplan einer fachlichen Anforderung, beim Plan eines
 *     Plandokuments
 * @param ziel die Zielstation: {@code PLAN}, {@code PAKETE}, {@code UMSETZUNG} oder {@code
 *     VORBEREITUNG}
 * @param pruefer die gewählte Prüferzahl aus {@code planreview:*}; {@code null} ohne Wahl und an
 *     einem Plan, der seine Prüfung schon hinter sich hat (Kit A1)
 */
public record NachtFreigabe(
    int number,
    String title,
    String boardName,
    Startstation start,
    ProgressStage ziel,
    @Nullable Integer pruefer) {

  /** Wo die Kette einer freigegebenen Karte beginnt. */
  public enum Startstation {
    /** Eine fachliche Anforderung — die Kette beginnt mit dem Plan. */
    FACHPLAN,
    /** Ein Plandokument — Plan und Prüfung sind vor dem Lauf erbracht. */
    PLAN
  }

  /**
   * Die Ziel-Sachen des Kit-Vertrags in der Reihenfolge der Stationen (E10); ihre Namen kommen aus
   * dem Register (Issue #1591, Plan #1582 A3).
   */
  private static final List<Map.Entry<Sache, ProgressStage>> ZIELE =
      List.of(
          Map.entry(Sache.ZIEL_PLAN, ProgressStage.PLAN),
          Map.entry(Sache.ZIEL_PAKETE, ProgressStage.PAKETE),
          Map.entry(Sache.ZIEL_UMSETZUNG, ProgressStage.UMSETZUNG),
          Map.entry(Sache.ZIEL_PUSH_VORBEREITET, ProgressStage.VORBEREITUNG));

  /** Die Arten, mit denen eine Kette beginnt; ihre Präfixe in beiden Sprachen (Issue #1587). */
  private static final Set<Art> STARTARTEN = Set.of(Art.FACHLICH, Art.PLAN);

  /** Die Prüferzahl je {@code planreview:*}-Sache in der Reihenfolge der Wahl. */
  private static final List<Map.Entry<Sache, Integer>> PRUEFER =
      List.of(Map.entry(Sache.PLANREVIEW_1, 1), Map.entry(Sache.PLANREVIEW_2, 2));

  /**
   * Ob die Karte eine Kette beginnen kann: Ihr Titel beginnt mit {@code [Fachlich]}, {@code
   * [Requirement]} oder {@code [Plan]}.
   */
  public static boolean istStartkarte(String titel) {
    return KitNamen.artAusTitel(titel).filter(STARTARTEN::contains).isPresent();
  }

  /**
   * Der kleine Stufenstand einer Startkarte.
   *
   * @param titel ein Titel, für den {@link #istStartkarte} gilt
   * @param labels die Labelnamen der Karte
   */
  public static NachtFreigabe aus(
      int number, String titel, String boardName, Collection<String> labels) {
    return aus(number, titel, boardName, labels, KitNamen.STANDARD);
  }

  /**
   * Der kleine Stufenstand einer Startkarte, ihre Labels gelesen über das Register {@code namen}
   * (E13): Jeder Name einer Sache wirkt gleich.
   *
   * @param titel ein Titel, für den {@link #istStartkarte} gilt
   * @param labels die Labelnamen der Karte
   */
  public static NachtFreigabe aus(
      int number, String titel, String boardName, Collection<String> labels, KitNamen namen) {
    Predicate<Sache> traegt = sache -> namen.namen(sache).stream().anyMatch(labels::contains);
    boolean plan = KitNamen.artAusTitel(titel).filter(Art.FACHLICH::equals).isEmpty();
    boolean durchziehen = traegt.test(Sache.DURCHZIEHEN);
    // Wie die Stufenleiste: das erste gesetzte Ziel zählt; mit kit:durchziehen gilt mindestens die
    // Umsetzung. Als Maximum statt als Grenzvergleich (Issue #1475): An der Grenze liefern beide
    // Wege die Umsetzung, ein Vergleich dort wäre durch keinen Test zu unterscheiden.
    ProgressStage gesetzt =
        ZIELE.stream()
            .filter(z -> traegt.test(z.getKey()))
            .map(Map.Entry::getValue)
            .filter(z -> !(plan && z == ProgressStage.PLAN))
            .findFirst()
            .orElse(ProgressStage.PAKETE);
    ProgressStage ziel =
        durchziehen ? Collections.max(List.of(gesetzt, ProgressStage.UMSETZUNG)) : gesetzt;
    Integer pruefer =
        plan
            ? null
            : PRUEFER.stream()
                .filter(p -> traegt.test(p.getKey()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(null);
    return new NachtFreigabe(
        number, titel, boardName, plan ? Startstation.PLAN : Startstation.FACHPLAN, ziel, pruefer);
  }
}
