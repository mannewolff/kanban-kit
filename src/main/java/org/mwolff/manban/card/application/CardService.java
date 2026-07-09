package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Karten- und Epic-Use-Cases: Anlegen (board-scoped Nummer, ans Spaltenende), Bearbeiten,
 * Archivieren/Wiederherstellen, Löschen, Move/Reindex und Abhängigkeiten. Epics sind Karten
 * vom Typ {@link CardType#EPIC}: sie erscheinen nicht auf dem Board, halten keine Position und
 * gruppieren Karten über {@code parentId}. Rechte über den {@link PermissionChecker}.
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
                           List<Integer> dependsOn, Long parentId) {
        Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
        permissions.require(userId, board.projectId(), Permission.CARD_CREATE);
        requireColumnInBoard(columnId, boardId);
        Long effectiveParent = parentId == null ? null : requireEpicInBoard(parentId, boardId).id();

        int number = cards.maxNumberInBoard(boardId) + 1;
        int position = cards.maxActivePositionInColumn(columnId) + 1;
        Instant now = Instant.now();
        Card saved = cards.save(new Card(null, boardId, columnId, number, title.trim(),
                normalize(description), position, false, null, userId, now, now,
                CardType.CARD, effectiveParent, null));

        setDependencies(saved, dependsOn);
        return view(saved);
    }

    /** Legt ein Epic an. Epics halten keine Board-Position und liegen technisch in der ersten Spalte. */
    @Transactional
    public CardView createEpic(long userId, long boardId, String title, String description, String shortcode) {
        Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
        permissions.require(userId, board.projectId(), Permission.CARD_CREATE);

        long columnId = columns.findByBoardId(boardId).stream()
                .min(Comparator.comparingInt(BoardColumn::position))
                .orElseThrow(ColumnNotFoundException::new)
                .id();

        int number = cards.maxNumberInBoard(boardId) + 1;
        Instant now = Instant.now();
        Card saved = cards.save(new Card(null, boardId, columnId, number, title.trim(),
                normalize(description), 0, false, null, userId, now, now,
                CardType.EPIC, null, trimToNull(shortcode)));
        return view(saved);
    }

    @Transactional(readOnly = true)
    public List<CardView> listByBoard(long userId, long boardId) {
        Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
        permissions.requireMembership(userId, board.projectId());
        return cards.findByBoardId(boardId).stream()
                .filter(c -> c.type() == CardType.CARD)
                .map(this::view)
                .toList();
    }

    /** Epics eines Boards inkl. Fortschritt (nicht-archivierte Kinder: gesamt / in Done). */
    @Transactional(readOnly = true)
    public List<EpicView> listEpics(long userId, long boardId) {
        Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
        permissions.requireMembership(userId, board.projectId());

        List<Card> all = cards.findByBoardId(boardId);
        Map<Long, String> columnNames = columns.findByBoardId(boardId).stream()
                .collect(Collectors.toMap(BoardColumn::id, BoardColumn::name));

        return all.stream()
                .filter(c -> c.type() == CardType.EPIC)
                .map(epic -> {
                    List<Card> children = all.stream()
                            .filter(c -> epic.id().equals(c.parentId()) && !c.archived())
                            .toList();
                    int total = children.size();
                    int done = (int) children.stream()
                            .filter(c -> isDoneColumn(columnNames.get(c.columnId())))
                            .count();
                    return new EpicView(epic.id(), epic.number(), epic.title(), epic.description(),
                            epic.shortcode(), done, total);
                })
                .toList();
    }

    @Transactional
    public CardView update(long userId, long cardId, String title, String description,
                           List<Integer> dependsOn, String shortcode, Long parentId) {
        Card card = requireCardWithPermission(userId, cardId, Permission.CARD_CREATE);
        Card updated = card.withContent(title.trim(), normalize(description));
        if (card.type() == CardType.EPIC) {
            // Epics tragen ein Kürzel, aber keinen Parent.
            updated = updated.withShortcode(trimToNull(shortcode));
        } else {
            // Karten: Epic-Zuordnung im selben PUT setzen/lösen (parentId == null -> lösen).
            Long effectiveParent = parentId == null ? null : requireEpicInBoard(parentId, card.boardId()).id();
            updated = updated.withParent(effectiveParent);
        }
        Card saved = cards.save(updated);
        if (dependsOn != null) {
            setDependencies(saved, dependsOn);
        }
        return view(saved);
    }

    /** Ordnet eine Karte einem Epic zu ({@code parentId}) oder löst die Zuordnung ({@code null}). */
    @Transactional
    public CardView assignParent(long userId, long cardId, Long parentId) {
        Card card = requireCardWithPermission(userId, cardId, Permission.CARD_CREATE);
        if (card.type() != CardType.CARD) {
            throw new InvalidDependencyException("Nur Karten können einem Epic zugeordnet werden");
        }
        Long effective = parentId == null ? null : requireEpicInBoard(parentId, card.boardId()).id();
        return view(cards.save(card.withParent(effective)));
    }

    @Transactional
    public CardView move(long userId, long cardId, long targetColumnId, int targetPosition) {
        Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
        if (card.type() == CardType.EPIC) {
            throw new InvalidDependencyException("Epics werden nicht auf dem Board positioniert");
        }
        Board board = boards.findById(card.boardId()).orElseThrow(BoardNotFoundException::new);
        permissions.require(userId, board.projectId(), Permission.CARD_MOVE);

        BoardColumn target = columns.findById(targetColumnId).orElseThrow(ColumnNotFoundException::new);
        if (target.boardId() != card.boardId()) {
            throw new ColumnNotFoundException();
        }

        cards.move(cardId, targetColumnId, targetPosition);

        // moved_to_done_at: beim Eintritt in eine "Done"-Spalte setzen, beim Verlassen löschen.
        boolean targetIsDone = isDoneColumn(target.name());
        Instant done = card.movedToDoneAt();
        if (targetIsDone && done == null) {
            done = Instant.now();
        } else if (!targetIsDone) {
            done = null;
        }

        Card moved = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
        return view(cards.save(moved.withMovedToDoneAt(done)));
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

    private Card requireEpicInBoard(long epicId, long boardId) {
        Card epic = cards.findById(epicId).orElseThrow(CardNotFoundException::new);
        if (epic.type() != CardType.EPIC || epic.boardId() != boardId) {
            throw new InvalidDependencyException("Kein Epic dieses Boards: " + epicId);
        }
        return epic;
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

    private static boolean isDoneColumn(String name) {
        return name != null && name.toLowerCase().contains("done");
    }

    private static String normalize(String description) {
        return description == null || description.isBlank() ? null : description;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private CardView view(Card c) {
        return new CardView(c.id(), c.boardId(), c.columnId(), c.number(), c.title(), c.description(),
                c.positionInColumn(), c.archived(), c.movedToDoneAt(), dependencies.findByCardId(c.id()),
                c.type(), c.parentId(), c.shortcode());
    }

    /** Kartendarstellung inkl. Abhängigkeits-Nummern, Typ und Epic-Zuordnung. */
    public record CardView(
            Long id, Long boardId, Long columnId, int number, String title, String description,
            int positionInColumn, boolean archived, Instant movedToDoneAt, List<Integer> dependencies,
            CardType type, Long parentId, String shortcode) {
    }

    /** Epic-Darstellung inkl. Fortschritt (Kinder gesamt / in Done). */
    public record EpicView(
            Long id, int number, String title, String description, String shortcode, int done, int total) {
    }
}
