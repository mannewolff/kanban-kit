package org.mwolff.manban.outbox.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.outbox.domain.OutboxEntry;
import org.mwolff.manban.outbox.domain.OutboxStatus;

/** JPA-Abbildung der Tabelle {@code outbox_entry}. */
@Entity
@Table(name = "outbox_entry")
class OutboxEntryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(name = "payload", nullable = false)
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private OutboxStatus status;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "completed_at")
  private @Nullable Instant completedAt;

  @Column(name = "last_error")
  private @Nullable String lastError;

  protected OutboxEntryEntity() {
    // für JPA
  }

  /** Baut die Entity direkt aus dem Domänenobjekt (statt aus 10 Einzelparametern, Sonar S107). */
  OutboxEntryEntity(OutboxEntry entry) {
    this.id = entry.id();
    this.eventType = entry.eventType();
    this.idempotencyKey = entry.idempotencyKey();
    this.payload = entry.payload();
    this.status = entry.status();
    this.attempts = entry.attempts();
    this.createdAt = entry.createdAt();
    this.nextAttemptAt = entry.nextAttemptAt();
    this.completedAt = entry.completedAt();
    this.lastError = entry.lastError();
  }

  OutboxEntry toDomain() {
    return new OutboxEntry(
        id,
        eventType,
        idempotencyKey,
        payload,
        status,
        attempts,
        createdAt,
        nextAttemptAt,
        completedAt,
        lastError);
  }
}
