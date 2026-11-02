package org.mwolff.manban.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Quantil über Beträge, linear interpoliert nach Typ 7 (Issue #2002, Plan #1990 E4): Mit {@code h =
 * (n − 1) · p} liegt das Quantil zwischen dem {@code ⌊h⌋}-ten und dem nächsten Wert der sortierten
 * Liste, im Verhältnis des Nachkommaanteils von {@code h}. Der 50-%-Wert ist damit der übliche
 * Median.
 *
 * <p>Gerechnet wird mit {@link BigDecimal} ohne Rundung: Anteil, Abstand und Differenz sind
 * endliche Dezimalzahlen, ihr Produkt also auch.
 */
public final class Quantil {

  private Quantil() {}

  /**
   * Das Quantil {@code anteil} der Werte; leer ohne Werte.
   *
   * @param werte die Beträge, in beliebiger Reihenfolge
   * @param anteil der Anteil zwischen 0 und 1 einschließlich, etwa {@code 0.15}
   * @throws IllegalArgumentException wenn der Anteil außerhalb von 0 bis 1 liegt
   */
  public static Optional<BigDecimal> von(Collection<BigDecimal> werte, BigDecimal anteil) {
    if (anteil.signum() < 0 || anteil.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("Anteil außerhalb von 0 bis 1: " + anteil);
    }
    List<BigDecimal> sortiert = werte.stream().sorted().toList();
    if (sortiert.isEmpty()) {
      return Optional.empty();
    }
    BigDecimal h = anteil.multiply(BigDecimal.valueOf(sortiert.size() - 1L));
    int unten = h.setScale(0, RoundingMode.FLOOR).intValueExact();
    BigDecimal rest = h.subtract(BigDecimal.valueOf(unten));
    BigDecimal untenWert = sortiert.get(unten);
    if (rest.signum() == 0) {
      return Optional.of(untenWert);
    }
    BigDecimal abstand = sortiert.get(unten + 1).subtract(untenWert);
    return Optional.of(untenWert.add(rest.multiply(abstand)));
  }
}
