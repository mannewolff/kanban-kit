package org.mwolff.manban.nightrun.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * JPA-Abbildung der Tabelle {@code night_run} — ausschließlich für den Lesepfad.
 *
 * <p>Geschrieben wird über {@code INSERT … ON CONFLICT … RETURNING id} per JDBC (Plan #718, A11);
 * die Entity trägt deshalb keinen öffentlichen Konstruktor.
 */
// Die Feldzahl folgt dem Tabellenschema, nicht einer Entwurfsentscheidung: Diese Klasse
// bildet night_run ab, und die Tabelle traegt seit Issue #944 acht Spalten mehr. Sie
// aufzuteilen hiesse, eine Zeile auf zwei Objekte zu verteilen, die es in der Datenbank
// nicht gibt.
@SuppressWarnings("PMD.TooManyFields")
@Entity
@Table(name = "night_run")
class NightRunEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "project_id", nullable = false)
  private Long projectId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "mode", nullable = false)
  private String mode;

  /** Gattung des Eintrags (Issue #1010); wie {@code origin} als Zeichenkette abgebildet. */
  @Column(name = "kind", nullable = false)
  private String kind;

  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  @Column(name = "processed_count", nullable = false)
  private int processedCount;

  @Column(name = "skipped_count", nullable = false)
  private int skippedCount;

  @Column(name = "unparsed_count", nullable = false)
  private int unparsedCount;

  @Column(name = "unparsed_sample")
  private @Nullable String unparsedSample;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "origin", nullable = false)
  private String origin;

  @Column(name = "token_name")
  private @Nullable String tokenName;

  @Column(name = "complete", nullable = false)
  private boolean complete;

  @Column(name = "updated_at")
  private @Nullable Instant updatedAt;

  /** Grund ohne Arbeit (Issue #1068); {@code null} heisst „hat gearbeitet" oder „Bestand". */
  @Column(name = "no_work_reason")
  private @Nullable String noWorkReason;

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

  /** Die Vorgaben des Laufs (Issue #1112); {@code NULL} heisst „nicht angegeben". */
  @Column(name = "budget_plan_min")
  private @Nullable Integer budgetPlanMin;

  @Column(name = "budget_review_min")
  private @Nullable Integer budgetReviewMin;

  @Column(name = "budget_pakete_min")
  private @Nullable Integer budgetPaketeMin;

  @Column(name = "budget_abdeckung_min")
  private @Nullable Integer budgetAbdeckungMin;

  @Column(name = "budget_kosten_usd")
  private @Nullable BigDecimal budgetKostenUsd;

  /** Herkunft der Vorgaben; wie {@code origin} als Zeichenkette abgebildet. */
  @Column(name = "budget_origin")
  private @Nullable String budgetOrigin;

  @Column(name = "budget_default_fields")
  private @Nullable String budgetDefaultFields;

  /** Grund des harten Abbruchs (Issue #1142); {@code null} heisst „nicht abgebrochen". */
  @Column(name = "abort_reason")
  private @Nullable String abortReason;

  protected NightRunEntity() {
    // für JPA
  }

  @Nullable Long getId() {
    return id;
  }

  Long getProjectId() {
    return projectId;
  }

  Instant getStartedAt() {
    return startedAt;
  }

  String getMode() {
    return mode;
  }

  String getKind() {
    return kind;
  }

  long getDurationMs() {
    return durationMs;
  }

  int getProcessedCount() {
    return processedCount;
  }

  int getSkippedCount() {
    return skippedCount;
  }

  int getUnparsedCount() {
    return unparsedCount;
  }

  @Nullable String getUnparsedSample() {
    return unparsedSample;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  String getOrigin() {
    return origin;
  }

  @Nullable String getTokenName() {
    return tokenName;
  }

  boolean isComplete() {
    return complete;
  }

  @Nullable Instant getUpdatedAt() {
    return updatedAt;
  }

  @Nullable String getNoWorkReason() {
    return noWorkReason;
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

  @Nullable Integer getBudgetPlanMin() {
    return budgetPlanMin;
  }

  @Nullable Integer getBudgetReviewMin() {
    return budgetReviewMin;
  }

  @Nullable Integer getBudgetPaketeMin() {
    return budgetPaketeMin;
  }

  @Nullable Integer getBudgetAbdeckungMin() {
    return budgetAbdeckungMin;
  }

  @Nullable BigDecimal getBudgetKostenUsd() {
    return budgetKostenUsd;
  }

  @Nullable String getBudgetOrigin() {
    return budgetOrigin;
  }

  @Nullable String getBudgetDefaultFields() {
    return budgetDefaultFields;
  }

  @Nullable String getAbortReason() {
    return abortReason;
  }
}
