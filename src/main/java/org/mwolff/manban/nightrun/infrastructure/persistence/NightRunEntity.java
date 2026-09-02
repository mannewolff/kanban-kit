package org.mwolff.manban.nightrun.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * JPA-Abbildung der Tabelle {@code night_run} — ausschließlich für den Lesepfad.
 *
 * <p>Geschrieben wird über {@code INSERT … ON CONFLICT … RETURNING id} per JDBC (Plan #718, A11);
 * die Entity trägt deshalb keinen öffentlichen Konstruktor.
 */
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
}
