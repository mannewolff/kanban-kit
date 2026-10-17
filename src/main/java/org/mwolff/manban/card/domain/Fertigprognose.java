package org.mwolff.manban.card.domain;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Exakte Fertigprognose der Leitstand-Platte „Vorhaben“ (Plan #1819, A3, A4, E5): Aus den
 * Tageswerten der letzten Tage — jeder Tag gleich wahrscheinlich, Nulltage eingeschlossen — rechnet
 * sie je Zahl n offener Pakete den frühesten Tag k, an dem P(Summe von k Tageswerten ≥ n) 50 % und
 * 85 % erreicht. Tag 1 ist der laufende Tag (E5). Rein, ohne Datenbank, ohne Zufall.
 *
 * <p>Gerechnet wird ein einziger Durchlauf für das größte n: Die Verteilung der Summe läuft über
 * die Zustände 0 bis n, alles ab n ist zusammengefasst. Jeden Tag wird sie einmal mit den
 * Tageswerten gefaltet. Ein n, das bis zur {@link #SCHUTZGRENZE} keine 85 % erreicht, erhält keinen
 * Tag — der Aufrufer meldet dafür zu wenig Durchsatz, die übrigen n behalten ihre Tage (A3).
 */
public final class Fertigprognose {

  /** Bis zu so vielen Tagen wird gerechnet; danach gibt es keinen Tag. */
  public static final int SCHUTZGRENZE = 36_500;

  private static final double FUENFZIG = 0.5;
  private static final double FUENFUNDACHTZIG = 0.85;

  /** Rundungsspielraum, damit eine Wahrscheinlichkeit genau auf der Marke sie auch erreicht. */
  private static final double TOLERANZ = 1e-12;

  /**
   * Die beiden Tage einer Prognose, je als Index ab dem laufenden Tag (Tag 1).
   *
   * @param tag50 frühester Tag mit mindestens 50 % Wahrscheinlichkeit
   * @param tag85 frühester Tag mit mindestens 85 % Wahrscheinlichkeit
   */
  public record Tage(int tag50, int tag85) {}

  private Fertigprognose() {}

  /**
   * Die Tage je Zahl offener Pakete.
   *
   * @param tageswerte fertige Pakete je abgeschlossenem Tag, Nulltage eingeschlossen; nicht leer
   * @param anzahlen die Zahlen offener Pakete, jede mindestens 1
   * @return je Zahl die Tage, leer für eine Zahl ohne 85-%-Tag bis zur {@link #SCHUTZGRENZE}
   */
  public static Map<Integer, Optional<Tage>> rechne(
      List<Integer> tageswerte, Set<Integer> anzahlen) {
    pruefe(tageswerte, anzahlen);
    if (anzahlen.isEmpty()) {
      return Map.of();
    }
    int[] sortiert = anzahlen.stream().mapToInt(Integer::intValue).sorted().toArray();
    int letzte = sortiert.length - 1;
    Summenverteilung summe = new Summenverteilung(tageswerte, sortiert[letzte]);
    int[] tag50 = new int[sortiert.length];
    int[] tag85 = new int[sortiert.length];

    // Das größte n erreicht jede Marke zuletzt: Seine Wahrscheinlichkeit ist ein Teil der Summe
    // jedes kleineren n.
    for (int tag = 1; tag <= SCHUTZGRENZE && tag85[letzte] == 0; tag++) {
      summe.naechsterTag();
      for (int i = 0; i <= letzte; i++) {
        double wahrscheinlichkeit = summe.mindestens(sortiert[i]);
        if (tag50[i] == 0 && erreicht(wahrscheinlichkeit, FUENFZIG)) {
          tag50[i] = tag;
        }
        if (tag85[i] == 0 && erreicht(wahrscheinlichkeit, FUENFUNDACHTZIG)) {
          tag85[i] = tag;
        }
      }
    }

    return ergebnis(sortiert, tag50, tag85);
  }

  private static Map<Integer, Optional<Tage>> ergebnis(int[] anzahlen, int[] tag50, int... tag85) {
    Map<Integer, Optional<Tage>> prognose = new TreeMap<>();
    for (int i = 0; i < anzahlen.length; i++) {
      prognose.put(
          anzahlen[i],
          tag85[i] == 0 ? Optional.empty() : Optional.of(new Tage(tag50[i], tag85[i])));
    }
    return Collections.unmodifiableMap(prognose);
  }

  private static void pruefe(List<Integer> tageswerte, Set<Integer> anzahlen) {
    if (tageswerte.isEmpty()) {
      throw new IllegalArgumentException("Ohne Tageswerte gibt es keine Prognose");
    }
    if (tageswerte.stream().anyMatch(wert -> wert < 0)) {
      throw new IllegalArgumentException("Tageswerte sind nie negativ: " + tageswerte);
    }
    if (anzahlen.stream().anyMatch(anzahl -> anzahl < 1)) {
      throw new IllegalArgumentException("Jede Zahl offener Pakete ist mindestens 1: " + anzahlen);
    }
  }

  private static boolean erreicht(double wahrscheinlichkeit, double marke) {
    return wahrscheinlichkeit >= marke - TOLERANZ;
  }

  /**
   * Die Verteilung der Summe aller Tageswerte bis zum aktuellen Tag über die Zustände 0 bis zum
   * größten n; der oberste Zustand hält, was ihn erreicht.
   */
  private static final class Summenverteilung {

    /** Die vorkommenden Tageswerte, ab dem größten n zu diesem zusammengefasst. */
    private final int[] werte;

    /** Die Wahrscheinlichkeit je Eintrag in {@link #werte}. */
    private final double[] gewichte;

    private double[] verteilung;

    /** P(Summe ≥ s) je Zustand s. */
    private final double[] restsummen;

    Summenverteilung(List<Integer> tageswerte, int hoechste) {
      Map<Integer, Integer> haeufigkeit = new TreeMap<>();
      for (int wert : tageswerte) {
        haeufigkeit.merge(Math.min(wert, hoechste), 1, Integer::sum);
      }
      werte = new int[haeufigkeit.size()];
      gewichte = new double[haeufigkeit.size()];
      int i = 0;
      for (Map.Entry<Integer, Integer> eintrag : haeufigkeit.entrySet()) {
        werte[i] = eintrag.getKey();
        gewichte[i] = (double) eintrag.getValue() / tageswerte.size();
        i++;
      }
      verteilung = new double[hoechste + 1];
      verteilung[0] = 1.0;
      restsummen = new double[hoechste + 1];
    }

    /** Faltet die Verteilung mit einem weiteren Tag. */
    void naechsterTag() {
      int hoechste = verteilung.length - 1;
      double[] neu = new double[hoechste + 1];
      neu[hoechste] = verteilung[hoechste];
      for (int summe = 0; summe < hoechste; summe++) {
        for (int j = 0; j < werte.length; j++) {
          neu[Math.min(summe + werte[j], hoechste)] += verteilung[summe] * gewichte[j];
        }
      }
      verteilung = neu;
      double rest = 0.0;
      for (int summe = hoechste; summe >= 0; summe--) {
        rest += verteilung[summe];
        restsummen[summe] = rest;
      }
    }

    double mindestens(int anzahl) {
      return restsummen[anzahl];
    }
  }
}
