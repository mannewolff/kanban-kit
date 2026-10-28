package org.mwolff.manban.nightrun.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Der Modellvergleich der Runs an einem Board (Issue #1946, Plan #1905): je Stufe der Kette eine
 * Zeile je Modell und Denktiefe mit Zahl der Vorgänge, Erfolgen und den Medians von Kosten und
 * Modellzeit. Gebildet von {@link ModellvergleichErmittlung}.
 *
 * @param kopf der Kopf der Platte (E11)
 * @param stufen alle fünf Stufen in der Reihenfolge der Kette, auch ohne Vorgang (E12)
 */
public record Modellvergleich(Kopf kopf, List<Stufe> stufen) {

  public Modellvergleich {
    stufen = List.copyOf(stufen);
  }

  /**
   * Der Kopf der Platte (E11, AK 7–9).
   *
   * @param datenbeginn frühester Laufstart eines gezählten Vorgangs mit Modell; {@code null} ohne
   *     einen solchen
   * @param letzte12Wochen ob der Datenbeginn im ersten der zwölf Wochenfenster liegt
   * @param ohneModell Zahl der gezählten Vorgänge ohne Modell ab dem Datenbeginn
   * @param vorgaengeImZeitraum Zahl der gezählten Vorgänge mit Modell
   */
  public record Kopf(
      @Nullable Instant datenbeginn,
      boolean letzte12Wochen,
      int ohneModell,
      int vorgaengeImZeitraum) {}

  /**
   * Eine Stufe der Kette.
   *
   * @param zeilen nach Modellname, dann ohne Denktiefe, dann Denktiefe (E12); leer ohne Vorgang
   */
  public record Stufe(NightRunAttemptStage stufe, List<Zeile> zeilen) {

    public Stufe {
      zeilen = List.copyOf(zeilen);
    }
  }

  /**
   * Eine Zeile: ein Modell mit einer Denktiefe in einer Stufe (E10, E12).
   *
   * @param model das Modell, wie gemeldet
   * @param effort die Denktiefe, wie gemeldet; {@code null} für Vorgänge ohne Denktiefe
   * @param vorgaenge Zahl der Vorgänge
   * @param erfolgreich Zahl der erfolgreichen Vorgänge (E8)
   * @param enoughData ob die Zeile die Mindestzahl {@link ModellvergleichErmittlung#MINDESTZAHL}
   *     erreicht; sonst steht „zu wenig Daten“ statt der Quote
   * @param kostenMedian Median der Kosten je Vorgang; {@code null}, wenn kein Vorgang sie trägt
   * @param kostenAus Zahl der Vorgänge, die Kosten tragen
   * @param modellzeitMedianMs Median der Modellzeit je Vorgang; {@code null}, wenn kein Vorgang sie
   *     trägt
   * @param modellzeitAus Zahl der Vorgänge, die eine Modellzeit tragen
   */
  public record Zeile(
      String model,
      @Nullable String effort,
      int vorgaenge,
      int erfolgreich,
      boolean enoughData,
      @Nullable BigDecimal kostenMedian,
      int kostenAus,
      @Nullable Long modellzeitMedianMs,
      int modellzeitAus) {}
}
