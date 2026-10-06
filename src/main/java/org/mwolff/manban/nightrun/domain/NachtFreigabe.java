package org.mwolff.manban.nightrun.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Eine zur Übernahme freigegebene Karte mit ihrem kleinen Stufenstand — eine Zeile der Übersicht
 * „Heute Nacht“ (Issue #1454, Plan #1447 E4, E10).
 *
 * <p>Vor der Übernahme gibt es keinen Laufstand; abzuleiten sind allein Start und Ziel. Das Ziel
 * folgt denselben Vorgaben wie die Stufenleiste der Karte: ein {@code ziel:*}-Label, an einem Plan
 * nie {@code ziel:plan}; ohne Label „Arbeitspakete“, und mit {@code kit:durchziehen} mindestens
 * „Umsetzung“ (Kit A3).
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

  /** Die Ziel-Labels des Kit-Vertrags in der Reihenfolge der Stationen (E10). */
  private static final List<Map.Entry<String, ProgressStage>> ZIELE =
      List.of(
          Map.entry("ziel:plan", ProgressStage.PLAN),
          Map.entry("ziel:pakete", ProgressStage.PAKETE),
          Map.entry("ziel:umsetzung", ProgressStage.UMSETZUNG),
          Map.entry("ziel:push-vorbereitet", ProgressStage.VORBEREITUNG));

  private static final Map<String, Integer> PRUEFER = Map.of("planreview:1", 1, "planreview:2", 2);

  /**
   * Ob die Karte eine Kette beginnen kann: Ihr Titel beginnt mit {@code [Fachlich]} oder {@code
   * [Plan]}.
   */
  public static boolean istStartkarte(String titel) {
    return FortschrittErmittlung.FACHLICH_PRAEFIX.matcher(titel).find()
        || FortschrittErmittlung.PLAN_PRAEFIX.matcher(titel).find();
  }

  /**
   * Der kleine Stufenstand einer Startkarte.
   *
   * @param titel ein Titel, für den {@link #istStartkarte} gilt
   * @param labels die Labelnamen der Karte
   */
  public static NachtFreigabe aus(
      int number, String titel, String boardName, Collection<String> labels) {
    boolean plan = !FortschrittErmittlung.FACHLICH_PRAEFIX.matcher(titel).find();
    boolean durchziehen = labels.contains(FortschrittErmittlung.LABEL_DURCHZIEHEN);
    // Wie die Stufenleiste: das erste gesetzte Ziel zählt; mit kit:durchziehen gilt mindestens die
    // Umsetzung. Als Maximum statt als Grenzvergleich (Issue #1475): An der Grenze liefern beide
    // Wege die Umsetzung, ein Vergleich dort wäre durch keinen Test zu unterscheiden.
    ProgressStage gesetzt =
        ZIELE.stream()
            .filter(z -> labels.contains(z.getKey()))
            .map(Map.Entry::getValue)
            .filter(z -> !(plan && z == ProgressStage.PLAN))
            .findFirst()
            .orElse(ProgressStage.PAKETE);
    ProgressStage ziel =
        durchziehen ? Collections.max(List.of(gesetzt, ProgressStage.UMSETZUNG)) : gesetzt;
    Integer pruefer =
        plan
            ? null
            : labels.stream()
                .filter(PRUEFER::containsKey)
                .findFirst()
                .map(PRUEFER::get)
                .orElse(null);
    return new NachtFreigabe(
        number, titel, boardName, plan ? Startstation.PLAN : Startstation.FACHPLAN, ziel, pruefer);
  }
}
