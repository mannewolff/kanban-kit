package org.mwolff.manban.card.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.card.domain.Card;

/** Ausgehender Port für die Persistenz von Karten. */
public interface CardRepository {

    Card save(Card card);

    Optional<Card> findById(long id);

    List<Card> findByBoardId(long boardId);

    /** Höchste vergebene board-scoped Nummer (0, wenn keine Karten). */
    int maxNumberInBoard(long boardId);

    /** Höchste Position unter nicht-archivierten Karten der Spalte (-1, wenn keine). */
    int maxActivePositionInColumn(long columnId);

    void deleteById(long id);
}
