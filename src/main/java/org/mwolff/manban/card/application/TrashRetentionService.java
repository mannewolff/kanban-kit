package org.mwolff.manban.card.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.mwolff.manban.card.domain.Card;
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

  public TrashRetentionService(CardRepository cards, CardDependencyRepository dependencies) {
    this.cards = cards;
    this.dependencies = dependencies;
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
    for (Card card : expired) {
      dependencies.deleteByCardId(card.requireId());
      cards.deleteById(card.requireId());
    }
    return expired.size();
  }
}
