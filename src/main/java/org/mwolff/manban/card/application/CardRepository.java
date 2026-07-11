package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.card.domain.Card;

/** Ausgehender Port für die Persistenz von Karten. */
public interface CardRepository {

  Card save(Card card);

  Optional<Card> findById(long id);

  List<Card> findByBoardId(long boardId);

  /** Nicht-archivierte Karten, die vor {@code threshold} nach Done verschoben wurden. */
  List<Card> findArchivableDoneCards(Instant threshold);

  /** Höchste vergebene board-scoped Nummer (0, wenn keine Karten). */
  int maxNumberInBoard(long boardId);

  /** Höchste Position unter nicht-archivierten Karten der Spalte (-1, wenn keine). */
  int maxActivePositionInColumn(long columnId);

  /**
   * Verschiebt eine Karte in eine Spalte an eine Zielposition und reindiziert die betroffenen
   * Spalten kollisionsfrei (Zwei-Phasen). Archivierte Karten bleiben unberührt (außerhalb des
   * aktiven Positions-Namespace).
   */
  void move(long cardId, long newColumnId, int newPosition);

  void deleteById(long id);
}
