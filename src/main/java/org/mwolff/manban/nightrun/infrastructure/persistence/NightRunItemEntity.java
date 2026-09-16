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
 * JPA-Abbildung der Tabelle {@code night_run_item} — ausschließlich für den Lesepfad.
 *
 * <p>Geschrieben werden die Zeilen zusammen mit ihrem Lauf über JDBC (siehe {@link
 * NightRunRepositoryAdapter}); die Entity trägt deshalb keinen öffentlichen Konstruktor.
 */
@Entity
@Table(name = "night_run_item")
class NightRunItemEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "night_run_id", nullable = false)
  private Long nightRunId;

  @Column(name = "card_number", nullable = false)
  private int cardNumber;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "state", nullable = false)
  private String state;

  @Column(name = "error_class")
  private @Nullable String errorClass;

  @Column(name = "duration_ms")
  private @Nullable Long durationMs;

  @Column(name = "commit_hash")
  private @Nullable String commitHash;

  @Column(name = "excerpt")
  private @Nullable String excerpt;

  @Column(name = "cost_usd")
  private @Nullable BigDecimal costUsd;

  @Column(name = "input_tokens")
  private @Nullable Long inputTokens;

  @Column(name = "output_tokens")
  private @Nullable Long outputTokens;

  @Column(name = "cached_input_tokens")
  private @Nullable Long cachedInputTokens;

  protected NightRunItemEntity() {
    // für JPA
  }

  @Nullable Long getId() {
    return id;
  }

  Long getNightRunId() {
    return nightRunId;
  }

  int getCardNumber() {
    return cardNumber;
  }

  String getTitle() {
    return title;
  }

  String getState() {
    return state;
  }

  @Nullable String getErrorClass() {
    return errorClass;
  }

  @Nullable Long getDurationMs() {
    return durationMs;
  }

  @Nullable String getCommitHash() {
    return commitHash;
  }

  @Nullable String getExcerpt() {
    return excerpt;
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
}
