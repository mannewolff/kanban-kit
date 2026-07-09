package org.mwolff.manban.card.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.domain.Card;
import org.springframework.stereotype.Component;

/** Adapter des {@link CardRepository}-Ports auf Spring Data JPA. */
@Component
class CardRepositoryAdapter implements CardRepository {

    private final CardJpaRepository jpa;

    CardRepositoryAdapter(CardJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Card save(Card card) {
        return toDomain(jpa.save(toEntity(card)));
    }

    @Override
    public Optional<Card> findById(long id) {
        return jpa.findById(id).map(CardRepositoryAdapter::toDomain);
    }

    @Override
    public List<Card> findByBoardId(long boardId) {
        return jpa.findByBoardIdOrderByNumber(boardId).stream().map(CardRepositoryAdapter::toDomain).toList();
    }

    @Override
    public int maxNumberInBoard(long boardId) {
        return jpa.maxNumberInBoard(boardId);
    }

    @Override
    public int maxActivePositionInColumn(long columnId) {
        return jpa.maxActivePositionInColumn(columnId);
    }

    @Override
    public void deleteById(long id) {
        jpa.deleteById(id);
    }

    private static CardEntity toEntity(Card c) {
        return new CardEntity(c.id(), c.boardId(), c.columnId(), c.number(), c.title(), c.description(),
                c.positionInColumn(), c.archived(), c.movedToDoneAt(), c.createdBy(), c.createdAt(), c.updatedAt());
    }

    private static Card toDomain(CardEntity e) {
        return new Card(e.getId(), e.getBoardId(), e.getColumnId(), e.getNumber(), e.getTitle(),
                e.getDescription(), e.getPositionInColumn(), e.isArchived(), e.getMovedToDoneAt(),
                e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
