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
import org.mwolff.manban.board.application.BoardChangedEvent;
import org.mwolff.manban.board.application.BoardChangedEvent.ChangeType;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.application.ColumnNotFoundException;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Karten- und Epic-Use-Cases: Anlegen (projektweite Nummer, ans Spaltenende), Bearbeiten,
 * Archivieren/Wiederherstellen, Löschen, Move/Reindex und Abhängigkeiten. Epics sind Karten vom Typ
 * {@link CardType#EPIC}: sie erscheinen nicht auf dem Board, halten keine Position und gruppieren
 * Karten über {@code parentId}. Rechte über den {@link PermissionChecker}.
 */
// PMD.CouplingBetweenObjects: zentraler Karten-Use-Case-Service; die Kopplung an die Ports
// (Karten, Abhängigkeiten, Boards/Spalten, Rechte, Zykluszeit, Zuständige, Mitgliedschaften)
// ist fachlich begründet und kein God-Class-Smell.
// PMD.CyclomaticComplexity: die Klassen-Gesamtkomplexität summiert viele kleine, je für sich
// einfache Use-Case-Methoden (höchste Einzelmethode weit unter dem Schwellwert); kein Smell.
// PMD.TooManyMethods: zentraler Karten-/Epic-Use-Case-Service — viele kleine, kohäsive Methoden
// (Anlegen/Bearbeiten/Move/Archiv/Ideen-Speicher/Zuständige/Labels je Erfolgs- und Fehlerpfad);
// eine Aufspaltung würde denselben Use-Case-Kontext künstlich zerreißen, kein God-Class-Smell.
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.CyclomaticComplexity", "PMD.TooManyMethods"})
@Service
public class CardService {

  private final CardRepository cards;
  private final CardDependencyRepository dependencies;
  private final BoardRepository boards;
  private final BoardColumnRepository columns;
  private final PermissionChecker permissions;
  private final CardColumnTransitionRepository transitions;
  private final CardAssigneeRepository assignees;
  private final ProjectMembershipRepository memberships;
  private final LabelRepository labels;
  private final CardLabelRepository cardLabels;
  private final CardActivityRepository activity;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public CardService(
      CardRepository cards,
      CardDependencyRepository dependencies,
      BoardRepository boards,
      BoardColumnRepository columns,
      PermissionChecker permissions,
      CardColumnTransitionRepository transitions,
      CardAssigneeRepository assignees,
      ProjectMembershipRepository memberships,
      LabelRepository labels,
      CardLabelRepository cardLabels,
      CardActivityRepository activity,
      ApplicationEventPublisher events,
      Clock clock) {
    this.cards = cards;
    this.dependencies = dependencies;
    this.boards = boards;
    this.columns = columns;
    this.permissions = permissions;
    this.transitions = transitions;
    this.assignees = assignees;
    this.memberships = memberships;
    this.labels = labels;
    this.cardLabels = cardLabels;
    this.activity = activity;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Publiziert ein {@link BoardChangedEvent} für Live-Board-Updates. Der Board-Event-Listener
   * reicht es transaktionsgebunden (nach Commit) an die SSE-Registry weiter — bei Rollback entsteht
   * kein Event. Wird am erfolgreichen Ende jeder board-relevanten Karten-Mutation aufgerufen.
   */
  private void publishChanged(long boardId, ChangeType type, @Nullable Long cardId) {
    events.publishEvent(new BoardChangedEvent(boardId, type, cardId));
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
    return doCreate(
        userId,
        boardId,
        columnId,
        title,
        description,
        dependsOn,
        parentId,
        false,
        null,
        null,
        null);
  }

  /**
   * Legt eine Karte an (ohne Fälligkeit/Zuständige/Labels). Delegiert an die Voll-Signatur mit
   * {@code null} für die inhaltlichen Zusatzfelder — genutzt vom {@code kanbancompat}-Ingest und
   * der schlanken internen Überladung, die diese Felder nicht setzen.
   */
  @Transactional
  public CardView create(
      long userId,
      long boardId,
      long columnId,
      String title,
      @Nullable String description,
      @Nullable List<Integer> dependsOn,
      @Nullable Long parentId,
      boolean ideaStored) {
    return doCreate(
        userId,
        boardId,
        columnId,
        title,
        description,
        dependsOn,
        parentId,
        ideaStored,
        null,
        null,
        null);
  }

  /**
   * Legt eine Karte an. Mit {@code ideaStored=true} entsteht sie direkt im Ideen-Speicher: sie hält
   * keinen aktiven Positions-Anspruch (fällt via {@code active_position=NULL} aus dem Namespace)
   * und eröffnet keine Spalten-Transition, weil sie nicht am Board-Workflow teilnimmt. {@code
   * dueDate}, {@code assigneeIds} und {@code labelIds} werden — sofern gesetzt — atomar mit der
   * Anlage übernommen (ein einziger {@code CREATED}-Aktivitätseintrag, kein Teil-Zustand); {@code
   * null}/leer bedeutet „nicht gesetzt". Assignees/Labels durchlaufen dieselbe Prüfung wie {@link
   * #setAssignees} / {@link #setLabels} (Mitglied im Projekt, Label des Boards).
   */
  @Transactional
  public CardView create(
      long userId,
      long boardId,
      long columnId,
      String title,
      @Nullable String description,
      @Nullable List<Integer> dependsOn,
      @Nullable Long parentId,
      boolean ideaStored,
      @Nullable Instant dueDate,
      @Nullable List<Long> assigneeIds,
      @Nullable List<Long> labelIds) {
    return doCreate(
        userId,
        boardId,
        columnId,
        title,
        description,
        dependsOn,
        parentId,
        ideaStored,
        dueDate,
        assigneeIds,
        labelIds);
  }

  // Kern-Logik des Anlegens ohne eigene @Transactional: wird von den öffentlichen create-
  // Überladungen (je @Transactional) aufgerufen, ohne Self-Invocation über den Proxy (java:S6809).
  private CardView doCreate(
      long userId,
      long boardId,
      long columnId,
      String title,
      @Nullable String description,
      @Nullable List<Integer> dependsOn,
      @Nullable Long parentId,
      boolean ideaStored,
      @Nullable Instant dueDate,
      @Nullable List<Long> assigneeIds,
      @Nullable List<Long> labelIds) {
    Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.TICKET_CREATE);
    BoardColumn column = requireColumnInBoard(columnId, boardId);
    Long effectiveParent =
        parentId == null ? null : requireEpicInBoard(parentId, boardId).requireId();

    int number = cards.maxNumberInProject(board.projectId()) + 1;
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
                ideaStored,
                null,
                userId,
                now,
                now,
                CardType.CARD,
                effectiveParent,
                null,
                dueDate,
                board.projectId(),
                null));

    if (!ideaStored) {
      transitions.open(saved.requireId(), columnId, column.name(), now);
    }
    activity.add(saved.requireId(), userId, CardActivityType.CREATED, "Karte angelegt", now);
    setDependencies(saved, dependsOn);
    if (assigneeIds != null && !assigneeIds.isEmpty()) {
      assignValidatedAssignees(saved.requireId(), board.projectId(), assigneeIds);
    }
    if (labelIds != null && !labelIds.isEmpty()) {
      assignValidatedLabels(saved.requireId(), boardId, labelIds);
    }
    publishChanged(boardId, ChangeType.CREATED, saved.requireId());
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

    int number = cards.maxNumberInProject(board.projectId()) + 1;
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
                false,
                null,
                userId,
                now,
                now,
                CardType.EPIC,
                null,
                trimToNull(shortcode),
                null,
                board.projectId(),
                null));
    publishChanged(boardId, ChangeType.CREATED, saved.requireId());
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
                  epic.requireNumber(),
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
      @Nullable Long parentId,
      @Nullable Instant dueDate) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    Card updated = card.withContent(title.trim(), normalize(description));
    if (card.type() == CardType.EPIC) {
      // Epics tragen ein Kürzel, aber keinen Parent.
      updated = updated.withShortcode(trimToNull(shortcode));
    } else {
      // Karten: Epic-Zuordnung im selben PUT setzen/lösen (parentId == null -> lösen).
      Long effectiveParent =
          parentId == null ? null : requireEpicInBoard(parentId, card.requireBoardId()).requireId();
      updated = updated.withParent(effectiveParent).withDueDate(dueDate);
    }
    Card saved = cards.save(updated);
    activity.add(cardId, userId, CardActivityType.UPDATED, "Karte bearbeitet", clock.instant());
    if (dependsOn != null) {
      setDependencies(saved, dependsOn);
    }
    publishChanged(saved.requireBoardId(), ChangeType.UPDATED, cardId);
    return view(saved);
  }

  /**
   * Ersetzt die Zuständigen einer Karte. Nur Karten (keine Epics); zugewiesen werden dürfen
   * ausschließlich Mitglieder des Projekts. Recht: {@link Permission#TICKET_UPDATE} (Member und
   * aufwärts).
   */
  @Transactional
  public CardView setAssignees(long userId, long cardId, List<Long> assigneeIds) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten haben Zuständige");
    }
    Board board = boards.findById(card.requireBoardId()).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.TICKET_UPDATE);

    assignValidatedAssignees(cardId, board.projectId(), assigneeIds);
    activity.add(cardId, userId, CardActivityType.ASSIGNED, "Zuständige geändert", clock.instant());
    publishChanged(card.requireBoardId(), ChangeType.UPDATED, cardId);
    return view(card);
  }

  /**
   * Ersetzt die Labels einer Karte. Nur Karten (keine Epics); zugeordnet werden dürfen nur Labels
   * desselben Boards. Recht: {@link Permission#TICKET_UPDATE} (Member und aufwärts).
   */
  @Transactional
  public CardView setLabels(long userId, long cardId, List<Long> labelIds) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten haben Labels");
    }
    Board board = boards.findById(card.requireBoardId()).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.TICKET_UPDATE);

    assignValidatedLabels(cardId, card.requireBoardId(), labelIds);
    publishChanged(card.requireBoardId(), ChangeType.UPDATED, cardId);
    return view(card);
  }

  /**
   * Prüft und setzt die Zuständigen einer Karte (Duplikate raus; jede ID muss Mitglied des Projekts
   * sein) ohne Aktivitätseintrag — die wiederverwendbare Kernlogik von {@link #setAssignees} und
   * dem atomaren {@link #create}.
   */
  private void assignValidatedAssignees(long cardId, long projectId, List<Long> assigneeIds) {
    List<Long> distinct = assigneeIds.stream().distinct().toList();
    for (Long assignee : distinct) {
      if (memberships.findByProjectIdAndUserId(projectId, assignee).isEmpty()) {
        throw new InvalidAssigneeException("Kein Projektmitglied: " + assignee);
      }
    }
    assignees.replaceAssignees(cardId, distinct);
  }

  /**
   * Prüft und setzt die Labels einer Karte (Duplikate raus; jede ID muss ein Label desselben Boards
   * sein) — die wiederverwendbare Kernlogik von {@link #setLabels} und dem atomaren {@link
   * #create}.
   */
  private void assignValidatedLabels(long cardId, long boardId, List<Long> labelIds) {
    List<Long> distinct = labelIds.stream().distinct().toList();
    List<Long> boardLabelIds =
        labels.findByBoardId(boardId).stream().map(Label::requireId).toList();
    for (Long labelId : distinct) {
      if (!boardLabelIds.contains(labelId)) {
        throw new InvalidLabelException("Kein Label dieses Boards: " + labelId);
      }
    }
    cardLabels.replaceLabels(cardId, distinct);
  }

  /** Ordnet eine Karte einem Epic zu ({@code parentId}) oder löst die Zuordnung ({@code null}). */
  @Transactional
  public CardView assignParent(long userId, long cardId, @Nullable Long parentId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten können einem Epic zugeordnet werden");
    }
    Long effective =
        parentId == null ? null : requireEpicInBoard(parentId, card.requireBoardId()).requireId();
    Card saved = cards.save(card.withParent(effective));
    publishChanged(card.requireBoardId(), ChangeType.UPDATED, cardId);
    return view(saved);
  }

  @Transactional
  public CardView move(long userId, long cardId, long targetColumnId, int targetPosition) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() == CardType.EPIC) {
      throw new InvalidDependencyException("Epics werden nicht auf dem Board positioniert");
    }
    Board board = boards.findById(card.requireBoardId()).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.CARD_MOVE);

    BoardColumn target = columns.findById(targetColumnId).orElseThrow(ColumnNotFoundException::new);
    // Wertvergleich der Board-IDs (Long): '!=' würde Referenzen vergleichen und bei IDs
    // jenseits des Long-Caches (> 127) falsch schlagen.
    if (!Objects.equals(target.boardId(), card.requireBoardId())) {
      throw new ColumnNotFoundException();
    }

    cards.move(cardId, targetColumnId, targetPosition);

    // Zykluszeit: nur bei echtem Spaltenwechsel (kein Eintrag bei reinem Reindex). Ein einziger
    // Zeitstempel schließt die verlassene und eröffnet die Ziel-Spalte lückenlos.
    long fromColumn = card.requireColumnId();
    if (fromColumn != targetColumnId) {
      Instant switchedAt = clock.instant();
      transitions.closeOpen(cardId, switchedAt);
      transitions.open(cardId, targetColumnId, target.name(), switchedAt);
      activity.add(
          cardId, userId, CardActivityType.MOVED, "Verschoben nach " + target.name(), switchedAt);
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
    CardView result = view(cards.save(moved.withMovedToDoneAt(done)));
    publishChanged(card.requireBoardId(), ChangeType.MOVED, cardId);
    return result;
  }

  /**
   * Verschiebt eine Karte board- und projektübergreifend in eine Spalte eines anderen Boards. Nur
   * möglich, wenn der Benutzer im Quell- und im Zielprojekt OWNER (oder Plattform-Admin) ist. Die
   * Karte erhält eine neue projekt-scoped Nummer und landet am Ende der Zielspalte; Epic-Zuordnung
   * und Abhängigkeiten (board-lokal) werden entfernt. Kommentare und Anhänge wandern mit (an der
   * Karten-ID).
   */
  @Transactional
  public CardView transfer(long userId, long cardId, long targetBoardId, long targetColumnId) {
    return doTransfer(userId, cardId, targetBoardId, targetColumnId);
  }

  private CardView doTransfer(long userId, long cardId, long targetBoardId, long targetColumnId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() == CardType.EPIC) {
      throw new InvalidDependencyException("Epics können nicht verschoben werden");
    }
    Board sourceBoard =
        boards.findById(card.requireBoardId()).orElseThrow(BoardNotFoundException::new);
    Board targetBoard = boards.findById(targetBoardId).orElseThrow(BoardNotFoundException::new);
    BoardColumn targetColumn = requireColumnInBoard(targetColumnId, targetBoardId);

    permissions.requireOwner(userId, sourceBoard.projectId());
    permissions.requireOwner(userId, targetBoard.projectId());

    int newNumber = cards.maxNumberInProject(targetBoard.projectId()) + 1;
    cards.transfer(cardId, targetBoardId, targetColumnId, newNumber);
    dependencies.deleteByCardId(cardId);
    // Zuständige gehören zum Quellprojekt; im Zielprojekt sind sie evtl. keine Mitglieder.
    assignees.deleteByCardId(cardId);

    // Zykluszeit: der board-/spaltenübergreifende Umzug zählt als Spaltenwechsel.
    Instant switchedAt = clock.instant();
    transitions.closeOpen(cardId, switchedAt);
    transitions.open(cardId, targetColumnId, targetColumn.name(), switchedAt);

    Card moved = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    CardView result = view(cards.save(moved.withParent(null).withMovedToDoneAt(null)));
    // Board-übergreifend: Quell- und Ziel-Board müssen beide live nachziehen.
    publishChanged(card.requireBoardId(), ChangeType.MOVED, cardId);
    publishChanged(targetBoardId, ChangeType.MOVED, cardId);
    return result;
  }

  /**
   * Verschiebt mehrere Karten in einer Transaktion auf dasselbe Zielboard und dieselbe Zielspalte
   * (alles-oder-nichts). Nutzt je Karte die Einzel-Logik von {@link #transfer(long, long, long,
   * long)} inklusive Owner-Prüfung in Quell- und Zielprojekt sowie Epic-Ausschluss; scheitert eine
   * Karte, rollt der gesamte Batch zurück. Die Karten landen in Eingabereihenfolge am Ende der
   * Zielspalte, jede Quellspalte wird dabei lückenlos nachgezogen.
   */
  @Transactional
  public List<CardView> bulkTransfer(
      long userId, List<Long> cardIds, long targetBoardId, long targetColumnId) {
    return cardIds.stream()
        .map(cardId -> doTransfer(userId, cardId, targetBoardId, targetColumnId))
        .toList();
  }

  @Transactional
  public CardView archive(long userId, long cardId) {
    return doArchive(userId, cardId);
  }

  private CardView doArchive(long userId, long cardId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    activity.add(
        card.requireId(), userId, CardActivityType.ARCHIVED, "Archiviert", clock.instant());
    CardView result = view(cards.save(card.asArchived()));
    publishChanged(card.requireBoardId(), ChangeType.ARCHIVED, card.requireId());
    return result;
  }

  /**
   * Archiviert mehrere Karten in einer Transaktion (alles-oder-nichts). Nutzt je Karte die
   * Einzel-Logik von {@link #archive(long, long)} inklusive Rechteprüfung; fehlt an einer Karte das
   * Recht oder existiert sie nicht, rollt der gesamte Batch zurück. Kein Positions-Reindex nötig,
   * da archivierte Karten über {@code active_position = NULL} aus dem Namespace fallen.
   */
  @Transactional
  public List<CardView> bulkArchive(long userId, List<Long> cardIds) {
    return cardIds.stream().map(cardId -> doArchive(userId, cardId)).toList();
  }

  @Transactional
  public CardView restore(long userId, long cardId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    int position = cards.maxActivePositionInColumn(card.requireColumnId()) + 1;
    activity.add(
        card.requireId(), userId, CardActivityType.RESTORED, "Wiederhergestellt", clock.instant());
    CardView result = view(cards.save(card.asRestored(position)));
    publishChanged(card.requireBoardId(), ChangeType.RESTORED, card.requireId());
    return result;
  }

  /**
   * Legt eine Karte in den Ideen-Speicher (Demotion, analog {@link #archive(long, long)}): sie
   * fällt aus dem aktiven Board (kein Positions-Reindex nötig, {@code active_position=NULL}).
   * Ideen-Pflege ist normaler Arbeitsfluss, kein Löschen — daher das Karten-Verschieberecht ({@link
   * Permission#CARD_MOVE}), nicht das Archiv-/Lösch-Recht. Nur Karten, keine Epics.
   */
  @Transactional
  public CardView moveToIdeaStorage(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() == CardType.EPIC) {
      throw new InvalidDependencyException("Epics können nicht in den Ideen-Speicher");
    }
    Board board = boards.findById(card.requireBoardId()).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.CARD_MOVE);
    activity.add(
        card.requireId(),
        userId,
        CardActivityType.IDEA_STORED,
        "In den Ideen-Speicher",
        clock.instant());
    CardView result = view(cards.save(card.asIdeaStored()));
    publishChanged(card.requireBoardId(), ChangeType.MOVED, card.requireId());
    return result;
  }

  /**
   * Holt eine Idee aus dem Ideen-Speicher zurück ins Backlog (Promotion, analog {@link
   * #restore(long, long)}). Anders als das Wiederherstellen wandert die Karte bewusst in die erste
   * Spalte (das Backlog, niedrigste Position) und landet dort am Ende. Recht wie die Demotion
   * ({@link Permission#CARD_MOVE}). Nur Karten, keine Epics.
   */
  @Transactional
  public CardView promoteToBacklog(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() == CardType.EPIC) {
      throw new InvalidDependencyException("Epics können nicht ins Backlog geholt werden");
    }
    Board board = boards.findById(card.requireBoardId()).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.CARD_MOVE);
    long firstColumnId =
        columns.findByBoardId(card.requireBoardId()).stream()
            .min(Comparator.comparingInt(BoardColumn::position))
            .orElseThrow(ColumnNotFoundException::new)
            .requireId();
    int position = cards.maxActivePositionInColumn(firstColumnId) + 1;
    activity.add(
        card.requireId(), userId, CardActivityType.PROMOTED, "Ins Backlog geholt", clock.instant());
    CardView result = view(cards.save(card.asPromoted(position, firstColumnId)));
    publishChanged(card.requireBoardId(), ChangeType.MOVED, card.requireId());
    return result;
  }

  // --- Projektweiter Ideen-Pool (board-lose Ideen) --------------------------

  /**
   * Legt eine board-lose Idee im projektweiten Pool an. Recht: {@link Permission#TICKET_CREATE}.
   */
  @Transactional
  public CardView createProjectIdea(
      long userId,
      long projectId,
      String title,
      @Nullable String description,
      @Nullable Long targetBoardId) {
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    Instant now = clock.instant();
    Card saved =
        cards.save(
            new Card(
                null,
                null,
                null,
                null,
                title.trim(),
                normalize(description),
                0,
                false,
                true,
                null,
                userId,
                now,
                now,
                CardType.CARD,
                null,
                null,
                null,
                projectId,
                targetBoardId));
    activity.add(saved.requireId(), userId, CardActivityType.CREATED, "Idee angelegt", now);
    return view(saved);
  }

  /**
   * Plant eine Idee ins Backlog (erste Spalte) eines Boards desselben Projekts ein: setzt
   * Board/Spalte/Nummer/Position, löscht das Ideen-Flag und den Zielboard-Hinweis. Recht {@link
   * Permission#TICKET_CREATE} im Zielboard.
   */
  @Transactional
  public CardView planOntoBoard(long userId, long cardId, long targetBoardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    Board targetBoard = boards.findById(targetBoardId).orElseThrow(BoardNotFoundException::new);
    // Nur auf ein Board des eigenen Projekts einplanbar (kein Existenz-Leak fremder Boards).
    if (!Objects.equals(targetBoard.projectId(), card.projectId())) {
      throw new BoardNotFoundException();
    }
    permissions.require(userId, targetBoard.projectId(), Permission.TICKET_CREATE);
    BoardColumn backlog =
        columns.findByBoardId(targetBoardId).stream()
            .min(Comparator.comparingInt(BoardColumn::position))
            .orElseThrow(ColumnNotFoundException::new);
    long columnId = backlog.requireId();
    int number = cards.maxNumberInProject(card.projectId()) + 1;
    int position = cards.maxActivePositionInColumn(columnId) + 1;
    Instant now = clock.instant();
    Card planned = cards.save(card.withPlannedOnBoard(targetBoardId, columnId, number, position));
    transitions.open(cardId, columnId, backlog.name(), now);
    activity.add(cardId, userId, CardActivityType.PROMOTED, "Auf Board eingeplant", now);
    publishChanged(targetBoardId, ChangeType.CREATED, cardId);
    return view(planned);
  }

  /**
   * Holt eine board-gebundene Karte zurück in den projektweiten Ideen-Pool (board-los); das
   * bisherige Board wird als Zielboard-Hinweis notiert. Recht {@link Permission#CARD_MOVE}.
   */
  @Transactional
  public CardView moveBackToPool(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    Board board = boards.findById(card.requireBoardId()).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.CARD_MOVE);
    Card pooled = cards.save(card.asPooledIdea(card.boardId()));
    activity.add(
        cardId, userId, CardActivityType.IDEA_STORED, "Zurück in den Ideen-Pool", clock.instant());
    publishChanged(card.requireBoardId(), ChangeType.MOVED, cardId);
    return view(pooled);
  }

  /**
   * Alle Ideen eines Projekts (board-lose Pool-Ideen und board-gebundene Legacy-Ideen), neueste
   * zuerst. Erfordert Projekt-Mitgliedschaft (Leserecht).
   */
  @Transactional(readOnly = true)
  public List<CardView> listProjectIdeas(long userId, long projectId) {
    permissions.requireMembership(userId, projectId);
    return cards.findIdeasByProjectId(projectId).stream()
        .filter(c -> c.type() == CardType.CARD)
        .map(this::view)
        .toList();
  }

  /** Aktivitätsverlauf einer Karte (chronologisch). Erfordert Board-Mitgliedschaft (Leserecht). */
  @Transactional(readOnly = true)
  public List<CardActivity> listActivity(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    Board board = boards.findById(card.requireBoardId()).orElseThrow(BoardNotFoundException::new);
    permissions.requireMembership(userId, board.projectId());
    return activity.findByCardId(cardId);
  }

  /**
   * Verschiebt eine Karte in den Papierkorb (Soft-Delete, reversibel). Recht: TICKET/EPIC_DELETE.
   */
  @Transactional
  public void delete(long userId, long cardId) {
    doDelete(userId, cardId);
  }

  private void doDelete(long userId, long cardId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    // Beim Löschen eines Epics die Kinder lösen — die DB-„ON DELETE SET NULL"-Kaskade auf
    // parent_id feuert nur beim Hard-Delete, nicht beim Soft-Delete.
    if (card.type() == CardType.EPIC) {
      cards.findByBoardId(card.requireBoardId()).stream()
          .filter(c -> Objects.equals(c.parentId(), card.requireId()))
          .forEach(child -> cards.save(child.withParent(null)));
    }
    cards.softDelete(card.requireId(), clock.instant());
    publishChanged(card.requireBoardId(), ChangeType.DELETED, card.requireId());
  }

  /**
   * Verschiebt mehrere Karten in einer Transaktion in den Papierkorb (alles-oder-nichts). Nutzt je
   * Karte die Einzel-Logik von {@link #delete(long, long)} inklusive Rechteprüfung und Lösen der
   * Epic-Kinder; fehlt an einer Karte das Recht oder existiert sie nicht, rollt der gesamte Batch
   * zurück.
   */
  @Transactional
  public void bulkDelete(long userId, List<Long> cardIds) {
    cardIds.forEach(cardId -> doDelete(userId, cardId));
  }

  /**
   * Holt eine Karte aus dem Papierkorb zurück (ans Spaltenende). Recht wie Löschen (Member und
   * aufwärts) — so kann ein Member eine versehentlich gelöschte Karte selbst wiederherstellen.
   */
  @Transactional
  public CardView restoreFromTrash(long userId, long cardId) {
    Card card = requireCardOp(userId, cardId, Permission.TICKET_DELETE, Permission.EPIC_DELETE);
    int position = cards.maxActivePositionInColumn(card.requireColumnId()) + 1;
    cards.restoreFromTrash(card.requireId(), position);
    activity.add(
        card.requireId(),
        userId,
        CardActivityType.RESTORED,
        "Aus Papierkorb wiederhergestellt",
        clock.instant());
    publishChanged(card.requireBoardId(), ChangeType.RESTORED, card.requireId());
    // View aus der bereits geladenen Karte mit neuer Position — der JDBC-Restore hat die DB-Zeile
    // geändert; ein erneutes findById käme aus dem JPA-Cache noch mit dem alten Stand.
    return view(card.asRestored(position));
  }

  /**
   * Entfernt eine Karte endgültig (Hard-Delete). Nur für Board-Verwalter (Projekt-Admin/Owner,
   * Recht {@link Permission#BOARD_DELETE}) — bewusst restriktiver als das reversible Löschen.
   */
  @Transactional
  public void purge(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    Board board = boards.findById(card.requireBoardId()).orElseThrow(BoardNotFoundException::new);
    permissions.require(userId, board.projectId(), Permission.BOARD_DELETE);
    dependencies.deleteByCardId(card.requireId());
    cards.deleteById(card.requireId());
    publishChanged(card.requireBoardId(), ChangeType.DELETED, card.requireId());
  }

  /** Karten im Papierkorb eines Boards. Erfordert Board-Mitgliedschaft (Leserecht). */
  @Transactional(readOnly = true)
  public List<CardView> listTrash(long userId, long boardId) {
    Board board = boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
    permissions.requireMembership(userId, board.projectId());
    return cards.findTrashByBoardId(boardId).stream()
        .filter(c -> c.type() == CardType.CARD)
        .map(this::view)
        .toList();
  }

  /** Lädt die Karte und verlangt das je nach Kartentyp (Ticket/Epic) passende Recht. */
  private Card requireCardOp(
      long userId, long cardId, Permission ticketPermission, Permission epicPermission) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    Board board = boards.findById(card.requireBoardId()).orElseThrow(BoardNotFoundException::new);
    permissions.require(
        userId,
        board.projectId(),
        card.type() == CardType.EPIC ? epicPermission : ticketPermission);
    return card;
  }

  private Card requireEpicInBoard(long epicId, long boardId) {
    Card epic = cards.findById(epicId).orElseThrow(CardNotFoundException::new);
    if (epic.type() != CardType.EPIC || epic.requireBoardId() != boardId) {
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
        cards.findByBoardId(card.requireBoardId()).stream().map(Card::number).toList();
    for (Integer dep : distinct) {
      if (dep == card.requireNumber()) {
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
        c.ideaStored(),
        c.movedToDoneAt(),
        dependencies.findByCardId(c.requireId()),
        c.type(),
        c.parentId(),
        c.shortcode(),
        assignees.findByCardId(c.requireId()),
        c.dueDate(),
        cardLabels.findByCardId(c.requireId()),
        c.targetBoardId());
  }

  /** Kartendarstellung inkl. Abhängigkeits-Nummern, Typ und Epic-Zuordnung. */
  public record CardView(
      Long id,
      @Nullable Long boardId,
      @Nullable Long columnId,
      @Nullable Integer number,
      String title,
      @Nullable String description,
      int positionInColumn,
      boolean archived,
      boolean ideaStored,
      @Nullable Instant movedToDoneAt,
      List<Integer> dependencies,
      CardType type,
      @Nullable Long parentId,
      @Nullable String shortcode,
      List<Long> assignees,
      @Nullable Instant dueDate,
      List<Long> labels,
      @Nullable Long targetBoardId) {}

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
