package org.mwolff.manban.nightrun.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

/**
 * Was ein Lauf oder ein Arbeitspaket verbraucht hat (Issue #944, Plan #943).
 *
 * <p>Ein eigener Typ statt acht loser Felder an zwei Records: Lauf und Arbeitspaket tragen dieselbe
 * Form, und wer sie einmal liest, liest sie überall gleich.
 *
 * <p><b>Jedes Feld darf fehlen.</b> {@code null} heißt „nicht gemessen" und nie Null — für Altläufe
 * sind die Werte nicht rekonstruierbar, und eine 0 behauptete, der Lauf habe nichts verbraucht.
 *
 * <p>Summiert wird feldweise mit {@link #plus} (Issue #934, Plan #933 E5): Eine fehlende Angabe
 * trägt nichts bei, und eine Summe aus lauter fehlenden bleibt fehlend — sie wird nie zu 0.
 *
 * @param costUsd der gemeldete Betrag, nicht ein aus Mengen und Tarif gerechneter. Wer rechnete,
 *     änderte die Vergangenheit, sobald ein Preis sich ändert.
 * @param inputTokens verarbeitete Eingabemenge
 * @param outputTokens erzeugte Ausgabemenge
 * @param cachedInputTokens der Anteil der Eingabe, der aus dem Zwischenspeicher kam
 */
public record NightRunUsage(
    @Nullable BigDecimal costUsd,
    @Nullable Long inputTokens,
    @Nullable Long outputTokens,
    @Nullable Long cachedInputTokens) {

  private static final BigDecimal HUNDERT = BigDecimal.valueOf(100);

  /**
   * Die feldweise Summe. {@code null} plus {@code null} bleibt {@code null}, {@code null} plus ein
   * Wert ergibt den Wert.
   */
  public NightRunUsage plus(NightRunUsage other) {
    return new NightRunUsage(
        summe(costUsd, other.costUsd),
        summe(inputTokens, other.inputTokens),
        summe(outputTokens, other.outputTokens),
        summe(cachedInputTokens, other.cachedInputTokens));
  }

  /**
   * Der Anteil der Eingabe aus dem Zwischenspeicher in Prozent, auf zwei Nachkommastellen gerundet
   * (#926 AK 13). Nicht bestimmt — {@code null}, nicht 0 — ohne Eingabemenge, ohne
   * Zwischenspeicher-Menge oder bei einer Eingabemenge von 0.
   */
  public @Nullable BigDecimal cachedInputSharePercent() {
    if (inputTokens == null || cachedInputTokens == null || inputTokens == 0L) {
      return null;
    }
    return BigDecimal.valueOf(cachedInputTokens)
        .multiply(HUNDERT)
        .divide(BigDecimal.valueOf(inputTokens), 2, RoundingMode.HALF_UP);
  }

  private static @Nullable BigDecimal summe(@Nullable BigDecimal a, @Nullable BigDecimal b) {
    if (a == null) {
      return b;
    }
    return b == null ? a : a.add(b);
  }

  private static @Nullable Long summe(@Nullable Long a, @Nullable Long b) {
    if (a == null) {
      return b;
    }
    return b == null ? a : a + b;
  }
}
