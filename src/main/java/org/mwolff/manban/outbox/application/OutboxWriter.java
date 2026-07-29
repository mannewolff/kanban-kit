package org.mwolff.manban.outbox.application;

import java.time.Clock;
import org.mwolff.manban.outbox.domain.OutboxEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schreibweg der Outbox: merkt einen Seiteneffekt <strong>innerhalb</strong> der fachlichen
 * Transaktion vor (Issue #501).
 *
 * <p><strong>Warum {@link Propagation#MANDATORY}:</strong> Die ganze Zusage der Outbox hängt daran,
 * dass Vormerkung und fachliche Änderung gemeinsam committen oder gemeinsam verschwinden. Ein
 * eigener Commit — oder gar {@code REQUIRES_NEW} — machte aus der Garantie eine Hoffnung. {@code
 * MANDATORY} ist die Leitplanke dafür: Wer ohne laufende Transaktion einplant, bekommt sofort eine
 * {@link org.springframework.transaction.IllegalTransactionStateException} statt eines Eintrags,
 * der ein Rollback überlebt. Das ist ein Programmierfehler, kein Betriebsfall — und deshalb genau
 * an dieser Stelle laut.
 */
@Service
public class OutboxWriter {

  private final OutboxRepository repository;
  private final Clock clock;

  public OutboxWriter(OutboxRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * Plant den Seiteneffekt ein. Ein bereits vergebener {@link OutboxMessage#idempotencyKey()} legt
   * keinen zweiten Eintrag an.
   *
   * @return {@code true}, wenn ein neuer Eintrag entstand; {@code false} bei bekanntem Schlüssel
   * @throws org.springframework.transaction.IllegalTransactionStateException wenn keine Transaktion
   *     läuft
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean schedule(OutboxMessage message) {
    return repository.saveIfAbsent(
        OutboxEntry.pending(
            message.eventType(), message.idempotencyKey(), message.payload(), clock.instant()));
  }
}
