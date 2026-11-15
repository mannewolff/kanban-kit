package org.mwolff.manban.card.application;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.project.application.BoardQuelle;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entfernt Papierkorb-Karten endgültig, deren Löschung die Retention überschreitet. Eigener
 * transaktionaler Service (getrennt vom geplanten Job), um die Self-Invocation-Falle zu vermeiden.
 *
 * <p>Karten von Projekten mit fremdem Board bleiben im Papierkorb (Issue #2161, Plan #2152 A8):
 * Endgültiges Löschen nähme per Cascade ihre Messungen mit. Die Abfrage {@link
 * CardRepository#findPurgeableTrash} lässt sie schon aus; der Dienst prüft je Projekt noch einmal,
 * weil ein Fehler hier Daten unwiederbringlich löscht.
 */
@Service
public class TrashRetentionService {

  private final CardRepository cards;
  private final CardBestandRepository bestand;
  private final KartenAbhaengigkeiten abhaengigkeiten;
  private final ApplicationEventPublisher events;
  private final BoardQuelle boardQuelle;

  public TrashRetentionService(
      CardRepository cards,
      CardBestandRepository bestand,
      KartenAbhaengigkeiten abhaengigkeiten,
      ApplicationEventPublisher events,
      BoardQuelle boardQuelle) {
    this.cards = cards;
    this.bestand = bestand;
    this.abhaengigkeiten = abhaengigkeiten;
    this.events = events;
    this.boardQuelle = boardQuelle;
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
    Map<Long, Boolean> fremd = new HashMap<>();
    List<Card> expired =
        cards.findPurgeableTrash(threshold).stream()
            .filter(card -> !fremd.computeIfAbsent(card.projectId(), boardQuelle::istFremd))
            .toList();
    if (expired.isEmpty()) {
      return 0;
    }
    // Vor dem Delete publizieren (Issue #503): Anhänge planen ihre Blob-Löschung ein, solange die
    // Metadaten existieren — die Cascade nimmt sie gleich mit.
    List<Long> ids = expired.stream().map(Card::requireId).toList();
    events.publishEvent(new CardsPurgedEvent(ids));
    // Plan #1574, E18: Die FK setzt die Herkunft der Kinder beim Delete auf NULL — vorher die
    // Nummer an ihnen bewahren.
    bestand.bewahreHerkunftsnummern(ids);
    for (Card card : expired) {
      abhaengigkeiten.entferne(card.requireId());
      cards.deleteById(card.requireId());
    }
    return expired.size();
  }
}
