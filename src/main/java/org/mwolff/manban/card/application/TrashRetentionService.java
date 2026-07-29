package org.mwolff.manban.card.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.mwolff.manban.card.domain.Card;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entfernt Papierkorb-Karten endgültig, deren Löschung die Retention überschreitet. Eigener
 * transaktionaler Service (getrennt vom geplanten Job), um die Self-Invocation-Falle zu vermeiden.
 */
@Service
public class TrashRetentionService {

  private final CardRepository cards;
  private final CardDependencyRepository dependencies;
  private final ApplicationEventPublisher events;

  public TrashRetentionService(
      CardRepository cards,
      CardDependencyRepository dependencies,
      ApplicationEventPublisher events) {
    this.cards = cards;
    this.dependencies = dependencies;
    this.events = events;
  }

  /**
   * Löscht alle Karten endgültig, die vor {@code now - retentionDays} in den Papierkorb verschoben
   * wurden.
   *
   * @return Anzahl der endgültig gelöschten Karten
   */
  @Transactional
  public int purgeExpiredTrash(Instant now, int retentionDays) {
    Instant threshold = now.minus(Duration.ofDays(retentionDays));
    List<Card> expired = cards.findPurgeableTrash(threshold);
    if (expired.isEmpty()) {
      return 0;
    }
    // Vor dem Delete publizieren (Issue #503): Anhänge planen ihre Blob-Löschung ein, solange die
    // Metadaten existieren — die Cascade nimmt sie gleich mit.
    events.publishEvent(new CardsPurgedEvent(expired.stream().map(Card::requireId).toList()));
    for (Card card : expired) {
      dependencies.deleteByCardId(card.requireId());
      cards.deleteById(card.requireId());
    }
    return expired.size();
  }
}
