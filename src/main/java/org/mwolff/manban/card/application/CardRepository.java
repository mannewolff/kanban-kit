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

  /**
   * Hängt eine Karte board-/spaltenübergreifend um: setzt Board, Spalte und eine neue board-scoped
   * Nummer, hängt sie ans Ende der Zielspalte und reindiziert die Quellspalte lückenlos.
   */
  void transfer(long cardId, long targetBoardId, long targetColumnId, int newNumber);

  /** Verschiebt eine Karte in den Papierkorb (Soft-Delete): setzt {@code deleted_at}. */
  void softDelete(long cardId, Instant when);

  /**
   * Holt eine Karte aus dem Papierkorb zurück: löscht {@code deleted_at} und setzt sie an die
   * angegebene (freie) Position.
   */
  void restoreFromTrash(long cardId, int newPosition);

  /** Karten im Papierkorb eines Boards (deleted_at gesetzt), aufsteigend nach Nummer. */
  List<Card> findTrashByBoardId(long boardId);

  /** Karten, die vor {@code threshold} gelöscht wurden (für die Papierkorb-Retention). */
  List<Card> findPurgeableTrash(Instant threshold);

  /** Entfernt eine Karte endgültig (Hard-Delete). */
  void deleteById(long id);
}
