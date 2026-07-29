package org.mwolff.manban.outbox.application;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Ein Worker-Durchlauf: greift die fälligen Einträge und lässt sie einzeln abarbeiten (Issue #501).
 *
 * <p>Bewusst <strong>ohne</strong> {@code @Transactional}: Die Transaktionsgrenze liegt je Eintrag
 * im {@link OutboxEntryDispatcher}. Läge sie hier, machte ein einziger scheiternder Seiteneffekt
 * die Ergebnisse des ganzen Durchlaufs rückgängig.
 *
 * <p>Die Batchgröße begrenzt, wie viele Einträge ein Durchlauf greift — ein Rückstand wird also
 * über mehrere Durchläufe abgetragen, nicht in einem. Das hält die Dauer eines Laufs planbar.
 */
@Service
public class OutboxDispatchService {

  private final OutboxRepository repository;
  private final OutboxEntryDispatcher entryDispatcher;
  private final OutboxProperties properties;
  private final Clock clock;

  public OutboxDispatchService(
      OutboxRepository repository,
      OutboxEntryDispatcher entryDispatcher,
      OutboxProperties properties,
      Clock clock) {
    this.repository = repository;
    this.entryDispatcher = entryDispatcher;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Arbeitet die derzeit fälligen Einträge ab.
   *
   * @return Anzahl der erfolgreich zugestellten Einträge
   */
  public int dispatchDue() {
    List<Long> due = repository.findDueIds(clock.instant(), properties.batchSize());
    int dispatched = 0;
    for (Long id : due) {
      if (entryDispatcher.dispatch(id)) {
        dispatched++;
      }
    }
    return dispatched;
  }
}
