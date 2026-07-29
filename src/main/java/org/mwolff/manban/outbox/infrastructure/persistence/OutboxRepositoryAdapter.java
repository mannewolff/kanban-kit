package org.mwolff.manban.outbox.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.outbox.application.OutboxRepository;
import org.mwolff.manban.outbox.domain.OutboxEntry;
import org.springframework.stereotype.Component;

/** Adapter des {@link OutboxRepository}-Ports auf Spring Data JPA. */
@Component
class OutboxRepositoryAdapter implements OutboxRepository {

  private final OutboxJpaRepository jpa;

  OutboxRepositoryAdapter(OutboxJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public boolean saveIfAbsent(OutboxEntry entry) {
    return jpa.insertIfAbsent(
            entry.eventType(),
            entry.idempotencyKey(),
            entry.payload(),
            entry.status().name(),
            entry.attempts(),
            entry.createdAt(),
            entry.nextAttemptAt())
        > 0;
  }

  @Override
  public List<Long> findDueIds(Instant now, int limit) {
    return jpa.findDueIds(now, limit);
  }

  @Override
  public Optional<OutboxEntry> claimDue(long id, Instant now) {
    // Erst sperren (Skalar-Projektion, siehe Javadoc der Abfrage), dann laden: Nur so ist der
    // gelesene Zustand der, der auch gesperrt ist.
    if (jpa.lockDueId(id, now).isEmpty()) {
      return Optional.empty();
    }
    return jpa.findById(id).map(OutboxEntryEntity::toDomain);
  }

  @Override
  public void update(OutboxEntry entry) {
    jpa.save(new OutboxEntryEntity(entry));
  }

  @Override
  public int deleteCompletedBefore(Instant threshold) {
    return jpa.deleteCompletedBefore(threshold);
  }
}
