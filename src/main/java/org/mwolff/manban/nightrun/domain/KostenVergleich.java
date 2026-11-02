package org.mwolff.manban.nightrun.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Die Abweichung der tatsächlichen Kosten eines abgeschlossenen Vorhabens von einer festgehaltenen
 * Voraussage (Issue #2004, Plan #1990 E13; fachlich #1954 AK 8).
 *
 * <p>Die Abweichung ist (tatsächlich − typisch) / typisch, auf ganze Prozent mit {@link
 * RoundingMode#HALF_UP} gerundet: 12,5 → 13 und −12,5 → −13. Ein Plus heißt teurer als
 * vorausgesagt. Keine Abweichung gibt es bei unvollständigen tatsächlichen Kosten, bei einer
 * Voraussage von höchstens 0 und ohne bezifferte Voraussage.
 */
public final class KostenVergleich {

  private static final BigDecimal HUNDERT = BigDecimal.valueOf(100);

  private KostenVergleich() {}

  /**
   * Die Abweichung in ganzen Prozent.
   *
   * @param tatsaechlich die festgehaltenen tatsächlichen Kosten; {@code null} ohne Abschluss
   * @param unvollstaendig ob beim Festhalten Messungen fehlten
   * @param typisch der typische Betrag der Voraussage; {@code null} bei „keine Voraussage“
   * @return die Abweichung oder leer, wenn keine zu nennen ist
   */
  public static Optional<Integer> abweichung(
      @Nullable BigDecimal tatsaechlich, boolean unvollstaendig, @Nullable BigDecimal typisch) {
    if (tatsaechlich == null || unvollstaendig || typisch == null || typisch.signum() <= 0) {
      return Optional.empty();
    }
    return Optional.of(
        tatsaechlich
            .subtract(typisch)
            .multiply(HUNDERT)
            .divide(typisch, 0, RoundingMode.HALF_UP)
            .intValueExact());
  }
}
