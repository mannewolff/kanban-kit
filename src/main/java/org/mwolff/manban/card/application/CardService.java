package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.List;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Karten-Use-Cases: Anlegen (board-scoped Nummer, ans Spaltenende), Bearbeiten,
 * Archivieren/Wiederherstellen, endgültig Löschen und Abhängigkeiten. Move/Reindex
 * folgt mit B3. Rechte über den {@link PermissionChecker} (CARD_CREATE / CARD_DELETE).
 */
@Service
public class CardService {

    private final CardRepository cards;
    private final CardDependencyRepository dependencies;
    private final BoardRepository boards;
    private final BoardColumnRepository columns;
    private final PermissionChecker permissions;

    public CardService(CardRepository cards, CardDependencyRepository dependencies, BoardRepository boards,
                       BoardColumnRepository columns, PermissionChecker permissions) {
        this.cards = cards;
        this.dependencies = dependencies;
        this.boards = boards;
        this.columns = columns;
        this.permissions = permissions;
    }

    @Transactional
    public CardView create(long userId, long boardId, long columnId, String title, String description,
                           List<Integer> dependsOn) {
        Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
        permissions.require(userId, board.projectId(), Permission.CARD_CREATE);
        requireColumnInBoard(columnId, boardId);

        int number = cards.maxNumberInBoard(boardId) + 1;
        int position = cards.maxActivePositionInColumn(columnId) + 1;
        Instant now = Instant.now();
        Card saved = cards.save(new Card(null, boardId, columnId, number, title.trim(),
                normalize(description), position, false, null, userId, now, now));

        setDependencies(saved, dependsOn);
        return view(saved);
    }

    @Transactional(readOnly = true)
    public List<CardView> listByBoard(long userId, long boardId) {
        Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
        permissions.requireMembership(userId, board.projectId());
        return cards.findByBoardId(boardId).stream().map(this::view).toList();
    }

    @Transactional
    public CardView update(long userId, long cardId, String title, String description, List<Integer> dependsOn) {
        Card card = requireCardWithPermission(userId, cardId, Permission.CARD_CREATE);
        Card saved = cards.save(card.withContent(title.trim(), normalize(description)));
        if (dependsOn != null) {
            setDependencies(saved, dependsOn);
        }
        return view(saved);
    }

    @Transactional
    public CardView archive(long userId, long cardId) {
        Card card = requireCardWithPermission(userId, cardId, Permission.CARD_DELETE);
        return view(cards.save(card.asArchived()));
    }

    @Transactional
    public CardView restore(long userId, long cardId) {
        Card card = requireCardWithPermission(userId, cardId, Permission.CARD_DELETE);
        int position = cards.maxActivePositionInColumn(card.columnId()) + 1;
        return view(cards.save(card.asRestored(position)));
    }

    @Transactional
    public void delete(long userId, long cardId) {
        Card card = requireCardWithPermission(userId, cardId, Permission.CARD_DELETE);
        dependencies.deleteByCardId(card.id());
        cards.deleteById(card.id());
    }

    private Card requireCardWithPermission(long userId, long cardId, Permission permission) {
        Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
        Board board = boards.findById(card.boardId()).orElseThrow(BoardNotFoundException::new);
        permissions.require(userId, board.projectId(), permission);
        return card;
    }

    private void requireColumnInBoard(long columnId, long boardId) {
        BoardColumn column = columns.findById(columnId).orElseThrow(ColumnNotFoundException::new);
        if (column.boardId() != boardId) {
            throw new ColumnNotFoundException();
        }
    }

    private void setDependencies(Card card, List<Integer> dependsOn) {
        if (dependsOn == null || dependsOn.isEmpty()) {
            dependencies.replaceDependencies(card.id(), List.of());
            return;
        }
        List<Integer> distinct = dependsOn.stream().distinct().toList();
        List<Integer> boardNumbers = cards.findByBoardId(card.boardId()).stream().map(Card::number).toList();
        for (Integer dep : distinct) {
            if (dep == card.number()) {
                throw new InvalidDependencyException("Karte kann nicht von sich selbst abhängen");
            }
            if (!boardNumbers.contains(dep)) {
                throw new InvalidDependencyException("Unbekannte Kartennummer: " + dep);
            }
        }
        dependencies.replaceDependencies(card.id(), distinct);
    }

    private static String normalize(String description) {
        return description == null || description.isBlank() ? null : description;
    }

    private CardView view(Card c) {
        return new CardView(c.id(), c.boardId(), c.columnId(), c.number(), c.title(), c.description(),
                c.positionInColumn(), c.archived(), c.movedToDoneAt(), dependencies.findByCardId(c.id()));
    }

    /** Kartendarstellung inkl. Abhängigkeits-Nummern. */
    public record CardView(
            Long id, Long boardId, Long columnId, int number, String title, String description,
            int positionInColumn, boolean archived, Instant movedToDoneAt, List<Integer> dependencies) {
    }
}
