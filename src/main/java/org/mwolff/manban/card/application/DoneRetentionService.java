package org.mwolff.manban.card.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.mwolff.manban.card.domain.Card;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archiviert Done-Karten, deren Verweildauer die Retention überschreitet. Eigener
 * transaktionaler Service (getrennt vom geplanten Job), um die Self-Invocation-Falle
 * zu vermeiden (der Job ruft diesen Bean auf, nicht eine eigene Methode).
 */
@Service
public class DoneRetentionService {

    private final CardRepository cards;

    public DoneRetentionService(CardRepository cards) {
        this.cards = cards;
    }

    /**
     * Archiviert alle nicht-archivierten Karten, die vor {@code now - retentionDays} nach
     * Done verschoben wurden.
     *
     * @return Anzahl der archivierten Karten
     */
    @Transactional
    public int archiveExpiredDoneCards(Instant now, int retentionDays) {
        Instant threshold = now.minus(Duration.ofDays(retentionDays));
        List<Card> expired = cards.findArchivableDoneCards(threshold);
        expired.forEach(card -> cards.save(card.asArchived()));
        return expired.size();
    }
}
