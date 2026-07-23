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

  /** Alle nicht-gelöschten Karten eines Projekts (board-übergreifend, inkl. board-loser Ideen). */
  List<Card> findByProjectId(long projectId);

  /**
   * Ideen-Karten eines Projekts (idea_stored), neueste zuerst — board-lose Pool-Ideen und
   * board-gebundene Legacy-Ideen. Papierkorb-Karten sind ausgenommen.
   */
  List<Card> findIdeasByProjectId(long projectId);

  /** Nicht-archivierte Karten, die vor {@code threshold} nach Done verschoben wurden. */
  List<Card> findArchivableDoneCards(Instant threshold);

  /**
   * Nächste zu vergebende projektweite Kartennummer: die Untergrenze (Floor) aus höchster bereits
   * vergebener Nummer + 1 und der optionalen Projekt-Startnummer ({@code
   * project.next_card_number}). Ohne gesetzte Startnummer schlicht {@code max(number) + 1} (bzw. 1,
   * wenn das Projekt keine nummerierten Karten hat). Eine gesetzte Startnummer wirkt nur, solange
   * sie über der höchsten vergebenen Nummer liegt.
   */
  int nextCardNumber(long projectId);

  /**
   * Höchste bereits vergebene projektweite Kartennummer (0, wenn keine nummerierte Karte). Dient
   * der Validierung einer neu gesetzten Projekt-Startnummer (sie muss darüber liegen).
   */
  int highestNumberInProject(long projectId);

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
