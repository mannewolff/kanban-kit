package org.mwolff.manban.codeanalyse.domain;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Die Wochenwerte der Platte „Codeanalyse“ eines Projekts (Issue #2273, Plan #2272 E7, E9).
 * Gebildet von {@link BefunddichteErmittlung}.
 *
 * @param hatMessungen ob für das Projekt je eine Messung ankam, zählend oder nicht; ohne zeigt die
 *     Platte „noch keine Messung“
 * @param dataSince kleinstes {@code publishedAt} aller Messungen, wenn es nach dem Fensterbeginn
 *     liegt; sonst {@code null}
 * @param wochen die zwölf Kalenderwochen des Fensters, die älteste zuerst
 */
public record Wochenwerte(boolean hatMessungen, @Nullable Instant dataSince, List<Woche> wochen) {

  public Wochenwerte {
    wochen = List.copyOf(wochen);
  }

  /**
   * Eine Kalenderwoche. Ohne zählende Messung sind {@code karten}, {@code vorher}, {@code
   * endzustand} und {@code rueckgangProzent} {@code null}.
   *
   * @param wochenbeginn Montag 00:00 UTC aus {@link Kalenderwochen}
   * @param zaehlendeMessungen Zahl der zählenden Messungen
   * @param karten Summe ihrer Kartenzahlen; eine Karte in zwei Messungen zählt zweimal
   * @param vorher Summen vor der Codeanalyse
   * @param endzustand Summen des veröffentlichten Stands
   * @param rueckgangProzent (Funde vorher − Funde Endzustand) / Funde vorher × 100, negativ, wenn
   *     die Funde steigen; {@code null} ohne Fund vorher
   * @param nichtZaehlend die nicht zählenden Messungen der Woche je Grund, nach Grund geordnet
   */
  public record Woche(
      Instant wochenbeginn,
      int zaehlendeMessungen,
      @Nullable Long karten,
      @Nullable Summe vorher,
      @Nullable Summe endzustand,
      @Nullable Double rueckgangProzent,
      List<NichtZaehlend> nichtZaehlend) {

    public Woche {
      nichtZaehlend = List.copyOf(nichtZaehlend);
    }
  }

  /**
   * Die Wochensummen eines Zustands über alle zählenden Messungen. Jede Dichte ist Funde / Zeilen ×
   * 1.000 aus den Summen, kein Mittelwert; ohne Zeilen ist sie {@code null}.
   */
  public record Summe(
      long fehler,
      long sicherheitsluecken,
      long wartbarkeitsmaengel,
      long funde,
      long zeilen,
      @Nullable Double dichteFehler,
      @Nullable Double dichteSicherheitsluecken,
      @Nullable Double dichteWartbarkeitsmaengel,
      @Nullable Double dichte) {}

  /**
   * Nicht zählende Messungen einer Woche mit demselben Grund.
   *
   * @param grund der Grund, wie ihn das Kit meldete
   * @param anzahl Zahl dieser Messungen
   */
  public record NichtZaehlend(String grund, int anzahl) {}
}
