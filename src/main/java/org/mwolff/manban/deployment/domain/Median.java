package org.mwolff.manban.deployment.domain;

import java.util.Collection;
import java.util.OptionalLong;

/** Median über Dauern in Sekunden (Issue #1832, Plan #1831 E8). */
public final class Median {

  private Median() {}

  /**
   * Der Median der Werte; bei gerader Anzahl das Mittel der beiden mittleren, auf ganze Sekunden
   * abgerundet. Leer ohne Werte.
   */
  public static OptionalLong von(Collection<Long> werte) {
    long[] sortiert = werte.stream().mapToLong(Long::longValue).sorted().toArray();
    if (sortiert.length == 0) {
      return OptionalLong.empty();
    }
    int mitte = sortiert.length / 2;
    boolean ungerade = sortiert.length % 2 == 1;
    if (ungerade) {
      return OptionalLong.of(sortiert[mitte]);
    }
    long unten = sortiert[mitte - 1];
    return OptionalLong.of(unten + (sortiert[mitte] - unten) / 2);
  }
}
