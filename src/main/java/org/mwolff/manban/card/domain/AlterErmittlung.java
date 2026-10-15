package org.mwolff.manban.card.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;

/**
 * Ermittelt die Platte „Alter in Arbeit“ aus den Aufenthalten eines Boards (Plan #1765, E8, E10,
 * E11, E12): je Zustand Ready, In Progress und In Review die 85-%-Schwelle, die Zahl der
 * Vergleichswerte und die offenen Karten nach Alter, die älteste oben. Rein, ohne Datenbank.
 *
 * <p>Vergleichswerte sind die Aufenthalte des Boards im Zustand, die mit einem Zustandswechsel
 * endeten, einen bekannten Eintritt haben und in den letzten {@link #VERGLEICHSZEITRAUM} endeten.
 * Die Schwelle ist der Wert an Rang ⌈0,85 · n⌉ der sortierten Dauern; unter {@link
 * #MINDESTVERGLEICHE} Werten gibt es keine (AK 7, AK 8).
 */
public final class AlterErmittlung {

  /** Zwölf Wochen als 84 × 24 Stunden (E10). */
  public static final Duration VERGLEICHSZEITRAUM = Duration.ofDays(84);

  /** Unter so vielen Vergleichswerten gibt es keine Schwelle (AK 8). */
  public static final int MINDESTVERGLEICHE = 5;

  /** Die Zustände der Platte in der Reihenfolge des Boards. */
  public static final List<CardStatus> ZUSTAENDE =
      List.of(CardStatus.READY, CardStatus.IN_PROGRESS, CardStatus.IN_REVIEW);

  private static final int PERZENTIL = 85;

  /**
   * Eine Karte im Zustand; Nummer und Titel ergänzt der Dienst.
   *
   * @param age Zeit seit dem letzten Eintritt in den Zustand
   * @param ageIsMinimum ob der Eintritt unbekannt und {@code age} nur der Mindestwert ist (E8)
   * @param overThreshold ob {@code age} die Schwelle übersteigt — der Bernstein-Melder
   */
  public record Karte(long cardId, Duration age, boolean ageIsMinimum, boolean overThreshold) {}

  /**
   * Ein Zustand der Platte.
   *
   * @param comparisonCount Zahl der Vergleichswerte
   * @param threshold die 85-%-Schwelle; {@code null} unter {@link #MINDESTVERGLEICHE} Werten
   * @param cards die Karten im Zustand, die älteste zuerst; leer, wenn keine dort liegt (AK 13)
   */
  public record Zustand(
      CardStatus status, int comparisonCount, @Nullable Duration threshold, List<Karte> cards) {}

  private AlterErmittlung() {}

  /**
   * Die drei Zustände Ready, In Progress und In Review, in dieser Reihenfolge.
   *
   * @param boardId Board des Leitstands — nur seine Aufenthalte zählen (E11)
   * @param aufenthalte Aufenthalte der Karten, auch auf anderen Boards
   */
  public static List<Zustand> ermittle(Instant jetzt, long boardId, List<FlussStay> aufenthalte) {
    List<FlussStay> amBoard = aufenthalte.stream().filter(a -> a.boardId() == boardId).toList();
    return ZUSTAENDE.stream().map(status -> zustand(status, jetzt, amBoard)).toList();
  }

  private static Zustand zustand(CardStatus status, Instant jetzt, List<FlussStay> amBoard) {
    Instant grenze = jetzt.minus(VERGLEICHSZEITRAUM);
    List<Duration> vergleiche =
        amBoard.stream()
            .filter(a -> a.status() == status && istVergleichswert(a, grenze))
            .map(a -> Duration.between(a.enteredAt(), Objects.requireNonNull(a.leftAt())))
            .sorted()
            .toList();
    Duration schwelle = schwelle(vergleiche);
    List<Karte> karten =
        amBoard.stream()
            .filter(a -> a.status() == status && a.leftAt() == null)
            .map(a -> karte(a, jetzt, schwelle))
            .sorted(Comparator.comparing(Karte::age).reversed().thenComparingLong(Karte::cardId))
            .toList();
    return new Zustand(status, vergleiche.size(), schwelle, karten);
  }

  private static boolean istVergleichswert(FlussStay aufenthalt, Instant grenze) {
    Instant ende = aufenthalt.leftAt();
    return ende != null
        && aufenthalt.endedBy() == EndedBy.STATUS
        && aufenthalt.enteredKnown()
        && !ende.isBefore(grenze);
  }

  /** Der Wert an Rang ⌈0,85 · n⌉ der sortierten Dauern, ganzzahlig gerechnet (E10). */
  private static @Nullable Duration schwelle(List<Duration> sortiert) {
    int n = sortiert.size();
    if (n < MINDESTVERGLEICHE) {
      return null;
    }
    int rang = (PERZENTIL * n + 99) / 100;
    return sortiert.get(rang - 1);
  }

  private static Karte karte(FlussStay aufenthalt, Instant jetzt, @Nullable Duration schwelle) {
    Duration alter = Duration.between(aufenthalt.enteredAt(), jetzt);
    boolean ueber = schwelle != null && alter.compareTo(schwelle) > 0;
    return new Karte(aufenthalt.cardId(), alter, !aufenthalt.enteredKnown(), ueber);
  }
}
