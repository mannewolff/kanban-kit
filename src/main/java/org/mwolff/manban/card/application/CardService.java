package org.mwolff.manban.card.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
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
 * Archivieren/Wiederherstellen, Löschen, Move/Reindex und Abhängigkeiten. Epics sind Karten vom Typ
 * {@link CardType#EPIC}: sie erscheinen nicht auf dem Board, halten keine Position und gruppieren
 * Karten über {@code parentId}. Rechte über den {@link PermissionChecker}.
 */
@Service
public class CardService {

  private final CardRepository cards;
  private final CardDependencyRepository dependencies;
  private final BoardRepository boards;
  private final BoardColumnRepository columns;
  private final PermissionChecker permissions;
  private final CardColumnTransitionRepository transitions;
  private final Clock clock;

  public CardService(
      CardRepository cards,
      CardDependencyRepository dependencies,
      BoardRepository boards,
      BoardColumnRepository columns,
      PermissionChecker permissions,
      CardColumnTransitionRepository transitions,
      Clock clock) {
    this.cards = cards;
    this.dependencies = dependencies;
    this.boards = boards;
    this.columns = columns;
    this.permissions = permissions;
    this.transitions = transitions;
    this.clock = clock;
  }

  @Transactional
  public CardView create(
      long userId,
      long boardId,
      long columnId,
      String title,
      @Nullable String description,
      @Nullable List<Integer> dependsOn,
      @Nullable Long parentId) {
    Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.TICKET_CREATE);
    BoardColumn column = requireColumnInBoard(columnId, boardId);
    Long effectiveParent =
        parentId == null ? null : requireEpicInBoard(parentId, boardId).requireId();

    int number = cards.maxNumberInBoard(boardId) + 1;
    int position = cards.maxActivePositionInColumn(columnId) + 1;
    Instant now = clock.instant();
    Card saved =
        cards.save(
            new Card(
                null,
                boardId,
                columnId,
                number,
                title.trim(),
                normalize(description),
                position,
                false,
                null,
                userId,
                now,
                now,
                CardType.CARD,
                effectiveParent,
                null));

    transitions.open(saved.requireId(), columnId, column.name(), now);
    setDependencies(saved, dependsOn);
    return view(saved);
  }

  /**
   * Legt ein Epic an. Epics halten keine Board-Position und liegen technisch in der ersten Spalte.
   */
  @Transactional
  public CardView createEpic(
      long userId,
      long boardId,
      String title,
      @Nullable String description,
      @Nullable String shortcode) {
    Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.EPIC_CREATE);

    long columnId =
        columns.findByBoardId(boardId).stream()
            .min(Comparator.comparingInt(BoardColumn::position))
            .orElseThrow(ColumnNotFoundException::new)
            .requireId();

    int number = cards.maxNumberInBoard(boardId) + 1;
    Instant now = clock.instant();
    Card saved =
        cards.save(
            new Card(
                null,
                boardId,
                columnId,
                number,
                title.trim(),
                normalize(description),
                0,
                false,
                null,
                userId,
                now,
                now,
                CardType.EPIC,
                null,
                trimToNull(shortcode)));
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
    Map<Long, String> columnNames =
        columns.findByBoardId(boardId).stream()
            .collect(Collectors.toMap(BoardColumn::id, BoardColumn::name));

    return all.stream()
        .filter(c -> c.type() == CardType.EPIC)
        .map(
            epic -> {
              List<Card> children =
                  all.stream()
                      .filter(c -> epic.requireId().equals(c.parentId()) && !c.archived())
                      .toList();
              int total = children.size();
              int done =
                  (int)
                      children.stream()
                          .filter(c -> isDoneColumn(columnNames.get(c.columnId())))
                          .count();
              return new EpicView(
                  epic.requireId(),
                  epic.number(),
                  epic.title(),
                  epic.description(),
                  epic.shortcode(),
                  done,
                  total);
            })
        .toList();
  }

  @Transactional
  public CardView update(
      long userId,
      long cardId,
      String title,
      @Nullable String description,
      @Nullable List<Integer> dependsOn,
      @Nullable String shortcode,
      @Nullable Long parentId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    Card updated = card.withContent(title.trim(), normalize(description));
    if (card.type() == CardType.EPIC) {
      // Epics tragen ein Kürzel, aber keinen Parent.
      updated = updated.withShortcode(trimToNull(shortcode));
    } else {
      // Karten: Epic-Zuordnung im selben PUT setzen/lösen (parentId == null -> lösen).
      Long effectiveParent =
          parentId == null ? null : requireEpicInBoard(parentId, card.boardId()).requireId();
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
  public CardView assignParent(long userId, long cardId, @Nullable Long parentId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten können einem Epic zugeordnet werden");
    }
    Long effective =
        parentId == null ? null : requireEpicInBoard(parentId, card.boardId()).requireId();
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
    // Wertvergleich der Board-IDs (Long): '!=' würde Referenzen vergleichen und bei IDs
    // jenseits des Long-Caches (> 127) falsch schlagen.
    if (!Objects.equals(target.boardId(), card.boardId())) {
      throw new ColumnNotFoundException();
    }

    cards.move(cardId, targetColumnId, targetPosition);

    // Zykluszeit: nur bei echtem Spaltenwechsel (kein Eintrag bei reinem Reindex). Ein einziger
    // Zeitstempel schließt die verlassene und eröffnet die Ziel-Spalte lückenlos.
    long fromColumn = card.columnId();
    if (fromColumn != targetColumnId) {
      Instant switchedAt = clock.instant();
      transitions.closeOpen(cardId, switchedAt);
      transitions.open(cardId, targetColumnId, target.name(), switchedAt);
    }

    // moved_to_done_at: beim Eintritt in eine "Done"-Spalte setzen, beim Verlassen löschen.
    boolean targetIsDone = isDoneColumn(target.name());
    Instant done = card.movedToDoneAt();
    if (targetIsDone && done == null) {
      done = clock.instant();
    } else if (!targetIsDone) {
      done = null;
    }

    Card moved = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    return view(cards.save(moved.withMovedToDoneAt(done)));
  }

  /**
   * Verschiebt eine Karte board- und projektübergreifend in eine Spalte eines anderen Boards. Nur
   * möglich, wenn der Benutzer im Quell- und im Zielprojekt OWNER (oder Plattform-Admin) ist. Die
   * Karte erhält eine neue board-scoped Nummer und landet am Ende der Zielspalte; Epic-Zuordnung
   * und Abhängigkeiten (board-lokal) werden entfernt. Kommentare und Anhänge wandern mit (an der
   * Karten-ID).
   */
  @Transactional
  public CardView transfer(long userId, long cardId, long targetBoardId, long targetColumnId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() == CardType.EPIC) {
      throw new InvalidDependencyException("Epics können nicht verschoben werden");
    }
    Board sourceBoard = boards.findById(card.boardId()).orElseThrow(BoardNotFoundException::new);
    Board targetBoard = boards.findById(targetBoardId).orElseThrow(BoardNotFoundException::new);
    BoardColumn targetColumn = requireColumnInBoard(targetColumnId, targetBoardId);

    permissions.requireOwner(userId, sourceBoard.projectId());
    permissions.requireOwner(userId, targetBoard.projectId());

    int newNumber = cards.maxNumberInBoard(targetBoardId) + 1;
    cards.transfer(cardId, targetBoardId, targetColumnId, newNumber);
    dependencies.deleteByCardId(cardId);

    // Zykluszeit: der board-/spaltenübergreifende Umzug zählt als Spaltenwechsel.
    Instant switchedAt = clock.instant();
    transitions.closeOpen(cardId, switchedAt);
    transitions.open(cardId, targetColumnId, targetColumn.name(), switchedAt);

    Card moved = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    return view(cards.save(moved.withParent(null).withMovedToDoneAt(null)));
  }

  @Transactional
  public CardView archive(long userId, long cardId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    return view(cards.save(card.asArchived()));
  }

  @Transactional
  public CardView restore(long userId, long cardId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    int position = cards.maxActivePositionInColumn(card.columnId()) + 1;
    return view(cards.save(card.asRestored(position)));
  }

  @Transactional
  public void delete(long userId, long cardId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    dependencies.deleteByCardId(card.requireId());
    cards.deleteById(card.requireId());
  }

  /** Lädt die Karte und verlangt das je nach Kartentyp (Ticket/Epic) passende Recht. */
  private Card requireCardOp(
      long userId, long cardId, Permission ticketPermission, Permission epicPermission) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    Board board = boards.findById(card.boardId()).orElseThrow(BoardNotFoundException::new);
    permissions.require(
        userId,
        board.projectId(),
        card.type() == CardType.EPIC ? epicPermission : ticketPermission);
    return card;
  }

  private Card requireEpicInBoard(long epicId, long boardId) {
    Card epic = cards.findById(epicId).orElseThrow(CardNotFoundException::new);
    if (epic.type() != CardType.EPIC || epic.boardId() != boardId) {
      throw new InvalidDependencyException("Kein Epic dieses Boards: " + epicId);
    }
    return epic;
  }

  private BoardColumn requireColumnInBoard(long columnId, long boardId) {
    BoardColumn column = columns.findById(columnId).orElseThrow(ColumnNotFoundException::new);
    if (column.boardId() != boardId) {
      throw new ColumnNotFoundException();
    }
    return column;
  }

  private void setDependencies(Card card, @Nullable List<Integer> dependsOn) {
    if (dependsOn == null || dependsOn.isEmpty()) {
      dependencies.replaceDependencies(card.requireId(), List.of());
      return;
    }
    List<Integer> distinct = dependsOn.stream().distinct().toList();
    List<Integer> boardNumbers =
        cards.findByBoardId(card.boardId()).stream().map(Card::number).toList();
    for (Integer dep : distinct) {
      if (dep == card.number()) {
        throw new InvalidDependencyException("Karte kann nicht von sich selbst abhängen");
      }
      if (!boardNumbers.contains(dep)) {
        throw new InvalidDependencyException("Unbekannte Kartennummer: " + dep);
      }
    }
    dependencies.replaceDependencies(card.requireId(), distinct);
  }

  private static boolean isDoneColumn(@Nullable String name) {
    return name != null && name.toLowerCase(Locale.ROOT).contains("done");
  }

  private static @Nullable String normalize(@Nullable String description) {
    return description == null || description.isBlank() ? null : description;
  }

  private static @Nullable String trimToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private CardView view(Card c) {
    return new CardView(
        c.requireId(),
        c.boardId(),
        c.columnId(),
        c.number(),
        c.title(),
        c.description(),
        c.positionInColumn(),
        c.archived(),
        c.movedToDoneAt(),
        dependencies.findByCardId(c.requireId()),
        c.type(),
        c.parentId(),
        c.shortcode());
  }

  /** Kartendarstellung inkl. Abhängigkeits-Nummern, Typ und Epic-Zuordnung. */
  public record CardView(
      Long id,
      Long boardId,
      Long columnId,
      int number,
      String title,
      @Nullable String description,
      int positionInColumn,
      boolean archived,
      @Nullable Instant movedToDoneAt,
      List<Integer> dependencies,
      CardType type,
      @Nullable Long parentId,
      @Nullable String shortcode) {}

  /** Epic-Darstellung inkl. Fortschritt (Kinder gesamt / in Done). */
  public record EpicView(
      Long id,
      int number,
      String title,
      @Nullable String description,
      @Nullable String shortcode,
      int done,
      int total) {}
}
