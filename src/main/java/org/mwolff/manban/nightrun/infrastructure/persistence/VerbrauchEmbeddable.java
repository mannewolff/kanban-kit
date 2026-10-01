package org.mwolff.manban.nightrun.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Die sechs Verbrauchsspalten, die {@code night_run}, {@code night_run_item} und {@code
 * night_run_item_stage} gleichlautend tragen (Issue #1317) — ausschließlich für den Lesepfad.
 *
 * <p>Ein Embeddable statt einer gemeinsamen Oberklasse: Die drei Entities haben sonst nichts
 * gemeinsam, und Tabellen wie Spaltennamen bleiben unverändert. Sind alle sechs Spalten {@code
 * NULL}, setzt Hibernate das eingebettete Feld auf {@code null}; die Entities fangen das in ihrem
 * Getter ab.
 */
@Embeddable
class VerbrauchEmbeddable {

  @Column(name = "cost_usd")
  private @Nullable BigDecimal costUsd;

  @Column(name = "input_tokens")
  private @Nullable Long inputTokens;

  @Column(name = "output_tokens")
  private @Nullable Long outputTokens;

  @Column(name = "cached_input_tokens")
  private @Nullable Long cachedInputTokens;

  /** Modellzeit und Zuege gehoeren zum Verbrauch (Issue #1112, Plan #1110 E1). */
  @Column(name = "model_duration_ms")
  private @Nullable Long modelDurationMs;

  @Column(name = "turns")
  private @Nullable Integer turns;

  protected VerbrauchEmbeddable() {
    // für JPA und als leerer Verbrauch
  }

  @Nullable BigDecimal getCostUsd() {
    return costUsd;
  }

  @Nullable Long getInputTokens() {
    return inputTokens;
  }

  @Nullable Long getOutputTokens() {
    return outputTokens;
  }

  @Nullable Long getCachedInputTokens() {
    return cachedInputTokens;
  }

  @Nullable Long getModelDurationMs() {
    return modelDurationMs;
  }

  @Nullable Integer getTurns() {
    return turns;
  }
}
