package org.mwolff.manban.card.application;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.project.application.BoardQuelle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archiviert Done-Karten, deren Verweildauer die Retention überschreitet. Eigener transaktionaler
 * Service (getrennt vom geplanten Job), um die Self-Invocation-Falle zu vermeiden (der Job ruft
 * diesen Bean auf, nicht eine eigene Methode).
 *
 * <p>Karten von Projekten mit fremdem Board bleiben unberührt (Issue #2161, Plan #2152 A10): Ihren
 * Stand bestimmt allein das fremde System.
 */
@Service
public class DoneRetentionService {

  private final CardRepository cards;
  private final BoardQuelle boardQuelle;

  public DoneRetentionService(CardRepository cards, BoardQuelle boardQuelle) {
    this.cards = cards;
    this.boardQuelle = boardQuelle;
  }

  /**
   * Archiviert alle nicht-archivierten Karten, die vor {@code now - retentionDays} nach Done
   * verschoben wurden. Bei {@code retentionDays <= 0} ist das Auto-Archiv abgeschaltet: es wird
   * nichts abgefragt und nichts archiviert.
   *
   * @return Anzahl der archivierten Karten
   */
  @Transactional
  public int archiveExpiredDoneCards(Instant now, int retentionDays) {
    if (retentionDays <= 0) {
      return 0;
    }
    Instant threshold = now.minus(Duration.ofDays(retentionDays));
    // Je Projekt einmal fragen, nicht je Karte.
    Map<Long, Boolean> fremd = new HashMap<>();
    List<Card> expired =
        cards.findArchivableDoneCards(threshold).stream()
            .filter(card -> !fremd.computeIfAbsent(card.projectId(), boardQuelle::istFremd))
            .toList();
    expired.forEach(card -> cards.save(card.asArchived()));
    return expired.size();
  }
}
