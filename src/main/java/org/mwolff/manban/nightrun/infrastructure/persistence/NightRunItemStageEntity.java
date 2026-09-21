package org.mwolff.manban.nightrun.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * JPA-Abbildung der Tabelle {@code night_run_item_stage} (Issue #1112) — ausschließlich für den
 * Lesepfad.
 *
 * <p>Geschrieben werden die Zeilen zusammen mit ihrem Arbeitspaket über JDBC (siehe {@link
 * NightRunRepositoryAdapter}); die Entity trägt deshalb keinen öffentlichen Konstruktor.
 *
 * <p>Die Stufen hängen als eigene Zeilen am Paket und nicht als {@code @OneToMany} daran: Der
 * Lesepfad holt sie zu einer Menge von Paketen in einer Abfrage nach, und eine Sammlung an {@link
 * NightRunItemEntity} zöge die Wahl zwischen einer N+1-Abfrage und einem {@code JOIN FETCH} nach
 * sich, den jeder Aufrufer mitdenken müsste.
 */
@Entity
@Table(name = "night_run_item_stage")
class NightRunItemStageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "night_run_item_id", nullable = false)
  private Long nightRunItemId;

  /** Die Stufe; wie {@code kind} am Lauf als Zeichenkette abgebildet. */
  @Column(name = "stage", nullable = false)
  private String stage;

  @Column(name = "duration_ms")
  private @Nullable Long durationMs;

  @Column(name = "cost_usd")
  private @Nullable BigDecimal costUsd;

  @Column(name = "input_tokens")
  private @Nullable Long inputTokens;

  @Column(name = "output_tokens")
  private @Nullable Long outputTokens;

  @Column(name = "cached_input_tokens")
  private @Nullable Long cachedInputTokens;

  @Column(name = "model_duration_ms")
  private @Nullable Long modelDurationMs;

  @Column(name = "turns")
  private @Nullable Integer turns;

  protected NightRunItemStageEntity() {
    // für JPA
  }

  Long getNightRunItemId() {
    return nightRunItemId;
  }

  String getStage() {
    return stage;
  }

  @Nullable Long getDurationMs() {
    return durationMs;
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
