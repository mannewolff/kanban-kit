package org.mwolff.manban.outbox.application;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Räumt erledigte Outbox-Einträge ab (Issue #501). Eigener transaktionaler Service getrennt vom
 * geplanten Job, um die Self-Invocation-Falle zu vermeiden — dasselbe Muster wie bei der
 * Done-Retention.
 *
 * <p>Abgeräumt wird ausschließlich {@link org.mwolff.manban.outbox.domain.OutboxStatus#DONE}.
 * Endgültig gescheiterte Einträge bleiben stehen: Sie sind der einzige Ort, an dem ein nie
 * ausgeführter Seiteneffekt noch sichtbar ist, und eine automatische Löschung machte aus einem
 * Rückstand ein Schweigen. Dass diese Einträge dadurch unbegrenzt wachsen können, ist die bewusste
 * Kehrseite — sie wachsen nur, wenn dauerhaft etwas kaputt ist.
 */
@Service
public class OutboxRetentionService {

  private final OutboxRepository repository;

  public OutboxRetentionService(OutboxRepository repository) {
    this.repository = repository;
  }

  /**
   * Löscht erledigte Einträge, die vor {@code now - retentionDays} abgeschlossen wurden. Bei {@code
   * retentionDays <= 0} ist die Aufbewahrung unbegrenzt: es wird nichts abgefragt und nichts
   * gelöscht.
   *
   * @return Anzahl der gelöschten Einträge
   */
  @Transactional
  public int purgeCompleted(Instant now, int retentionDays) {
    if (retentionDays <= 0) {
      return 0;
    }
    return repository.deleteCompletedBefore(now.minus(Duration.ofDays(retentionDays)));
  }
}
