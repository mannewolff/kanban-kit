package org.mwolff.manban.card.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardSummary;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectService;
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
// (Karten, Abhängigkeiten, Boards/Spalten, Rechte, Zykluszeit, Zuständige, Labels)
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
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final ProjectService projects;
  private final CardColumnTransitionRepository transitions;
  private final CardAssigneeRepository assignees;
  private final LabelRepository labels;
  private final CardLabelRepository cardLabels;
  private final CardActivityRepository activity;
  private final ActorContext actor;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public CardService(
      CardRepository cards,
      CardDependencyRepository dependencies,
      BoardService boardService,
      PermissionChecker permissions,
      ProjectService projects,
      CardColumnTransitionRepository transitions,
      CardAssigneeRepository assignees,
      LabelRepository labels,
      CardLabelRepository cardLabels,
      CardActivityRepository activity,
      ActorContext actor,
      ApplicationEventPublisher events,
      Clock clock) {
    this.cards = cards;
    this.dependencies = dependencies;
    this.boardService = boardService;
    this.permissions = permissions;
    this.projects = projects;
    this.transitions = transitions;
    this.assignees = assignees;
    this.labels = labels;
    this.cardLabels = cardLabels;
    this.activity = activity;
    this.actor = actor;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Publiziert ein {@link CardBoardActivityEvent} für Live-Board-Updates. Die Composition-Root
   * übersetzt es in den SSE-Vertrag des board-Moduls, der Board-Event-Listener reicht es
   * transaktionsgebunden (nach Commit) an die SSE-Registry weiter — bei Rollback entsteht kein
   * Event. Wird am erfolgreichen Ende jeder board-relevanten Karten-Mutation aufgerufen.
   */
  private void publishChanged(long boardId, ActivityType type, @Nullable Long cardId) {
    events.publishEvent(new CardBoardActivityEvent(boardId, type, cardId));
  }

  /**
   * Publiziert ein {@link ProjectIdeasChangedEvent} für Live-Updates des projektweiten Ideen-Pools.
   * Der Ideen-Event-Listener reicht es transaktionsgebunden (nach Commit) an die SSE-Registry
   * weiter — bei Rollback entsteht kein Event. Wird am erfolgreichen Ende jeder pool-relevanten
   * Karten-Mutation aufgerufen (Idee anlegen, einplanen, zurück in den Pool).
   */
  private void publishIdeasChanged(long projectId) {
    events.publishEvent(new ProjectIdeasChangedEvent(projectId));
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
    long projectId = boardService.requireProjectId(boardId);
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    ColumnView column = boardService.requireColumn(columnId, boardId);
    Long effectiveParent =
        parentId == null ? null : requireEpicInBoard(parentId, boardId).requireId();

    int number = cards.allocateCardNumber(projectId);
    int position = cards.allocateActivePosition(columnId);
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
                projectId,
                null,
                null));

    if (!ideaStored) {
      transitions.open(saved.requireId(), columnId, column.name(), now);
    }
    activity.add(
        saved.requireId(),
        userId,
        CardActivityType.CREATED,
        "Karte angelegt",
        now,
        actor.current());
    setDependencies(saved, dependsOn);
    if (assigneeIds != null && !assigneeIds.isEmpty()) {
      assignValidatedAssignees(saved.requireId(), projectId, assigneeIds);
    }
    if (labelIds != null && !labelIds.isEmpty()) {
      assignValidatedLabels(saved.requireId(), boardId, labelIds);
    }
    publishChanged(boardId, ActivityType.CREATED, saved.requireId());
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
    long projectId = boardService.requireProjectId(boardId);
    permissions.require(userId, projectId, Permission.EPIC_CREATE);

    long columnId = boardService.firstColumn(boardId).id();

    int number = cards.allocateCardNumber(projectId);
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
                projectId,
                null,
                null));
    publishChanged(boardId, ActivityType.CREATED, saved.requireId());
    return view(saved);
  }

  @Transactional(readOnly = true)
  public List<CardView> listByBoard(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    return cards.findByBoardId(boardId).stream()
        .filter(c -> c.type() == CardType.CARD)
        .map(this::view)
        .toList();
  }

  /**
   * Sichtbare Board-Items (Karten <em>und</em> Epics) als schlanke Projektion für modulfremde
   * Aufrufer: ohne archivierte und ohne im Ideen-Speicher liegende Karten, nach Position in der
   * Spalte sortiert. Erfordert Projekt-Mitgliedschaft (Leserecht).
   *
   * <p>Bewusst nicht {@link CardView}: diese Projektion kommt mit einer einzigen Abfrage aus,
   * während {@code view(...)} je Karte Abhängigkeiten, Zuständige und Labels nachlädt (N+1). Der
   * {@code kanbancompat}-Ingest listet ganze Boards und braucht davon nichts.
   */
  @Transactional(readOnly = true)
  public List<BoardItemView> listBoardItems(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    return cards.findByBoardId(boardId).stream()
        .filter(c -> !c.archived() && !c.ideaStored())
        .sorted(Comparator.comparingInt(Card::positionInColumn))
        .map(
            c ->
                new BoardItemView(
                    c.requireId(),
                    c.requireNumber(),
                    c.title(),
                    c.description(),
                    c.columnId(),
                    c.positionInColumn(),
                    c.type() == CardType.EPIC))
        .toList();
  }

  /**
   * Projekt-ID der Karte — die Auflösung, die modulfremde Rechteprüfungen (Anhänge, Kommentare)
   * brauchen, ohne das Kartenaggregat oder dessen Port zu kennen. Projekt-basiert über {@code
   * card.projectId()} (immer gesetzt, V18), daher auch für board-lose Pool-Ideen (#405) korrekt.
   *
   * @throws CardNotFoundException wenn die Karte nicht existiert
   */
  @Transactional(readOnly = true)
  public long requireProjectId(long cardId) {
    return cards.findById(cardId).orElseThrow(CardNotFoundException::new).projectId();
  }

  /**
   * Einzelne Karte für Stellen, die nur eine Karten-ID kennen (Dashboard-Ausreißer, #515) — ohne
   * den Umweg über die komplette Board-Kartenliste. Leserecht wie bei den übrigen Lesepfaden:
   * Projekt-Mitgliedschaft über die Projekt-ID der Karte; Nichtmitglied und unbekannte Karte sind
   * nicht unterscheidbar (beide 404, kein Existenz-Leak).
   *
   * @throws CardNotFoundException wenn die Karte nicht existiert
   */
  @Transactional(readOnly = true)
  public CardView getCard(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.requireMembership(userId, card.projectId());
    return view(card);
  }

  /**
   * Sichert zu, dass die Karte auf dem angegebenen Board liegt — der Board-Guard des
   * token-gebundenen {@code kanbancompat}-Zugriffs (#44).
   *
   * <p>Eine Karte im Ideen-Speicher gilt dabei als nicht vorhanden (#434): sie ist für Menschen
   * ausgeblendet, also darf die Automatik sie auch nicht bewegen oder kommentieren. Andernfalls
   * entstünden Änderungen an einer Karte, die auf dem Board niemand sieht.
   *
   * @throws CardNotFoundException wenn die Karte fehlt, auf einem anderen Board liegt oder im
   *     Ideen-Speicher liegt
   */
  @Transactional(readOnly = true)
  public void requireOnBoard(long cardId, long boardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    // Wertvergleich der Board-IDs (Long): '!=' würde Referenzen vergleichen und bei IDs
    // jenseits des Long-Caches (> 127) falsch schlagen.
    if (!Long.valueOf(boardId).equals(card.boardId()) || card.ideaStored()) {
      throw new CardNotFoundException();
    }
  }

  /** Epics eines Boards inkl. Fortschritt (nicht-archivierte Kinder: gesamt / in Done). */
  @Transactional(readOnly = true)
  public List<EpicView> listEpics(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));

    List<Card> all = cards.findByBoardId(boardId);
    Map<Long, String> columnNames =
        boardService.listColumns(boardId).stream()
            .collect(Collectors.toMap(ColumnView::id, ColumnView::name));

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
    activity.add(
        cardId,
        userId,
        CardActivityType.UPDATED,
        "Karte bearbeitet",
        clock.instant(),
        actor.current());
    if (dependsOn != null) {
      setDependencies(saved, dependsOn);
    }
    publishChangedIfOnBoard(saved.boardId(), ActivityType.UPDATED, cardId);
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
    // Projekt-basierte Rechte (#405): auch board-lose Pool-Ideen haben Zuständige.
    permissions.require(userId, card.projectId(), Permission.TICKET_UPDATE);

    assignValidatedAssignees(cardId, card.projectId(), assigneeIds);
    activity.add(
        cardId,
        userId,
        CardActivityType.ASSIGNED,
        "Zuständige geändert",
        clock.instant(),
        actor.current());
    publishChangedIfOnBoard(card.boardId(), ActivityType.UPDATED, cardId);
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
    // Projekt-basierte Rechte (#405). Labels bleiben board-scoped: eine board-lose Pool-Idee hat
    // kein Board mit Labels; das Frontend ruft setLabels für sie nicht auf.
    permissions.require(userId, card.projectId(), Permission.TICKET_UPDATE);

    assignValidatedLabels(cardId, card.requireBoardId(), labelIds);
    publishChanged(card.requireBoardId(), ActivityType.UPDATED, cardId);
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
      if (!permissions.isRealProjectMember(assignee, projectId)) {
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
    publishChanged(card.requireBoardId(), ActivityType.UPDATED, cardId);
    return view(saved);
  }

  @Transactional
  public CardView move(long userId, long cardId, long targetColumnId, int targetPosition) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() == CardType.EPIC) {
      throw new InvalidDependencyException("Epics werden nicht auf dem Board positioniert");
    }
    permissions.require(
        userId, boardService.requireProjectId(card.requireBoardId()), Permission.CARD_MOVE);

    ColumnView target = boardService.requireColumn(targetColumnId, card.requireBoardId());

    cards.move(cardId, targetColumnId, targetPosition);

    // Zykluszeit: nur bei echtem Spaltenwechsel (kein Eintrag bei reinem Reindex). Ein einziger
    // Zeitstempel schließt die verlassene und eröffnet die Ziel-Spalte lückenlos.
    long fromColumn = card.requireColumnId();
    if (fromColumn != targetColumnId) {
      Instant switchedAt = clock.instant();
      transitions.closeOpen(cardId, switchedAt);
      transitions.open(cardId, targetColumnId, target.name(), switchedAt);
      activity.add(
          cardId,
          userId,
          CardActivityType.MOVED,
          "Verschoben nach " + target.name(),
          switchedAt,
          actor.current());
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
    publishChanged(card.requireBoardId(), ActivityType.MOVED, cardId);
    return result;
  }

  /**
   * Ordnet die aktiven Karten einer Spalte nach ihrer Kartennummer — {@link SortDirection#ASC}
   * kleinste zuerst, {@link SortDirection#DESC} größte zuerst. Gedacht für Spalten, in die mehrere
   * Karten am Stück gezogen wurden und die deshalb ungeordnet dastehen.
   *
   * <p>Fachlich ist das ein <em>Massen-Verschieben innerhalb</em> der Spalte und keine
   * Strukturänderung am Board, deshalb genügt {@link Permission#CARD_MOVE} — dasselbe Recht wie für
   * das Verschieben einer einzelnen Karte. Karten außerhalb des aktiven Positions-Namespace
   * (archiviert, Papierkorb, Ideen-Speicher) und Epics bleiben unberührt; Details am Port {@link
   * CardRepository#sortActiveByNumber(long, SortDirection)}.
   *
   * <p>Bewusst ohne {@link CardActivity}-Eintrag: Die Umsortierung ändert nur die Anordnung
   * innerhalb der Spalte, keine Karte wechselt Spalte oder Zustand — ein Audit-Eintrag pro
   * betroffener Karte würde den Aktivitätsverlauf fluten, ohne eine fachliche Änderung zu
   * dokumentieren. Offene Boards erfahren von der neuen Anordnung über das SSE-Event.
   */
  @Transactional
  public void sortColumnByNumber(long userId, long columnId, SortDirection direction) {
    long boardId = boardService.boardIdOfColumn(columnId);
    permissions.require(userId, boardService.requireProjectId(boardId), Permission.CARD_MOVE);

    cards.sortActiveByNumber(columnId, direction);

    // Ohne Karten-Bezug: betroffen ist die ganze Spalte, offene Boards laden über SSE neu.
    publishChanged(boardId, ActivityType.MOVED, null);
  }

  /**
   * Verschiebt eine Karte in eine Spalte eines anderen Boards; die Karte landet am Ende der
   * Zielspalte. Rechte und Nebenwirkungen sind richtungsabhängig:
   *
   * <ul>
   *   <li><b>Selbes Projekt:</b> es genügt {@link Permission#CARD_MOVE} — dasselbe Recht wie für
   *       das Verschieben innerhalb eines Boards und für den Rückweg aus dem Ideen-Pool. Die Nummer
   *       bleibt erhalten (projektweit ohnehin eindeutig), Abhängigkeiten und Zuständige wandern
   *       mit — so brechen Querverweise beim Board-Wechsel nicht.
   *   <li><b>Anderes Projekt:</b> der Benutzer muss im Quell- <em>und</em> im Zielprojekt OWNER
   *       (oder Plattform-Admin) sein. Die Karte erhält eine neue projekt-scoped Nummer;
   *       Abhängigkeiten und Zuständige (projekt-lokal) werden entfernt.
   * </ul>
   *
   * <p>Die board-lokale Epic-Zuordnung wird in beiden Fällen entfernt (das Ziel-Board hat eigene
   * Epics). Kommentare und Anhänge wandern immer mit (an der Karten-ID).
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
    long sourceProjectId = boardService.requireProjectId(card.requireBoardId());
    long targetProjectId = boardService.requireProjectId(targetBoardId);
    ColumnView targetColumn = boardService.requireColumn(targetColumnId, targetBoardId);

    boolean sameProject = sourceProjectId == targetProjectId;
    // Projektintern ist der Board-Wechsel nur ein Verschieben und verlangt daher CARD_MOVE — genau
    // das Recht, das auch der Rückweg in den Ideen-Pool verlangt. Über Projektgrenzen bleibt es bei
    // der strengen Eigentümer-Prüfung in beiden Projekten.
    if (sameProject) {
      permissions.require(userId, sourceProjectId, Permission.CARD_MOVE);
    } else {
      permissions.requireOwner(userId, sourceProjectId);
      permissions.requireOwner(userId, targetProjectId);
    }

    // Innerhalb desselben Projekts bleibt die Nummer erhalten (projektweit ohnehin eindeutig) und
    // Abhängigkeiten/Zuständige wandern mit — nur so bleiben Querverweise beim Board-Wechsel
    // stabil.
    // Nur über Projektgrenzen wird neu nummeriert und werden die projekt-lokalen Verknüpfungen
    // (Abhängigkeiten, Zuständige) entfernt.
    int newNumber = sameProject ? card.requireNumber() : cards.allocateCardNumber(targetProjectId);
    cards.transfer(cardId, targetBoardId, targetColumnId, newNumber);
    if (!sameProject) {
      dependencies.deleteByCardId(cardId);
      assignees.deleteByCardId(cardId);
    }

    // Zykluszeit: der board-/spaltenübergreifende Umzug zählt als Spaltenwechsel.
    Instant switchedAt = clock.instant();
    transitions.closeOpen(cardId, switchedAt);
    transitions.open(cardId, targetColumnId, targetColumn.name(), switchedAt);

    Card moved = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    CardView result = view(cards.save(moved.withParent(null).withMovedToDoneAt(null)));
    // Board-übergreifend: Quell- und Ziel-Board müssen beide live nachziehen.
    publishChanged(card.requireBoardId(), ActivityType.MOVED, cardId);
    publishChanged(targetBoardId, ActivityType.MOVED, cardId);
    return result;
  }

  /**
   * Verschiebt mehrere Karten in einer Transaktion auf dasselbe Zielboard und dieselbe Zielspalte
   * (alles-oder-nichts). Nutzt je Karte die Einzel-Logik von {@link #transfer(long, long, long,
   * long)} inklusive der richtungsabhängigen Rechteprüfung ({@link Permission#CARD_MOVE} innerhalb
   * des Projekts, OWNER in Quell- und Zielprojekt darüber hinaus) sowie Epic-Ausschluss; scheitert
   * eine Karte, rollt der gesamte Batch zurück. Die Karten landen in Eingabereihenfolge am Ende der
   * Zielspalte, jede Quellspalte wird dabei lückenlos nachgezogen.
   *
   * <p>Die Spaltensperren nimmt der Batch <strong>vorab in einem Zug</strong> (Issue #499): Nähme
   * jeder Einzel-Umzug seine beiden Sperren für sich, könnten zwei gleichzeitige Sammel-Umzüge mit
   * überlappenden Quellspalten dieselben Spalten in unterschiedlicher Reihenfolge greifen und
   * verklemmen. Ein sortierter Aufruf über die Vereinigung schließt das aus; die Sperren der
   * Einzel-Umzüge sind danach wirkungslose Wiederholungen.
   *
   * <p>Enthält der Batch eine Karte aus einem <em>anderen</em> Projekt, wird zuvor der
   * Nummern-Namespace des Zielprojekts gesperrt: Nur so bleibt die Ordnung „Projekt vor Spalte"
   * gewahrt, die jeder Einzel-Umzug einhält. Innerhalb eines Projekts entfällt diese Sperre — dort
   * wird keine Nummer neu vergeben, und ein Sammel-Umzug soll die Karten-Anlage im selben Projekt
   * nicht für die Dauer des Batches ausbremsen.
   */
  @Transactional
  public List<CardView> bulkTransfer(
      long userId, List<Long> cardIds, long targetBoardId, long targetColumnId) {
    long targetProjectId = boardService.requireProjectId(targetBoardId);
    List<Card> batch = cardIds.stream().map(cards::findById).flatMap(Optional::stream).toList();
    if (batch.stream().anyMatch(card -> !Objects.equals(card.projectId(), targetProjectId))) {
      cards.lockCardNumbers(targetProjectId);
    }
    List<Long> affectedColumns = new ArrayList<>();
    affectedColumns.add(targetColumnId);
    batch.forEach(card -> Optional.ofNullable(card.columnId()).ifPresent(affectedColumns::add));
    cards.lockColumnPositions(affectedColumns);
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
        card.requireId(),
        userId,
        CardActivityType.ARCHIVED,
        "Archiviert",
        clock.instant(),
        actor.current());
    CardView result = view(cards.save(card.asArchived()));
    publishChanged(card.requireBoardId(), ActivityType.ARCHIVED, card.requireId());
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
    int position = cards.allocateActivePosition(card.requireColumnId());
    activity.add(
        card.requireId(),
        userId,
        CardActivityType.RESTORED,
        "Wiederhergestellt",
        clock.instant(),
        actor.current());
    CardView result = view(cards.save(card.asRestored(position)));
    publishChanged(card.requireBoardId(), ActivityType.RESTORED, card.requireId());
    return result;
  }

  /**
   * Legt eine Karte in den Ideen-Speicher (analog {@link #moveBackToPool(long, long)}): sie wird
   * board-los und landet im projektweiten Ideen-Pool, das bisherige Board als Zielboard-Hinweis
   * notiert (#433). Vorher blieb die Karte board-gebunden und war dadurch in keiner Ansicht mehr
   * sichtbar — ein unauffindbarer Zwischenzustand (#428). Ideen-Pflege ist normaler Arbeitsfluss,
   * kein Löschen — daher das Karten-Verschieberecht ({@link Permission#CARD_MOVE}), nicht das
   * Archiv-/Lösch-Recht. Nur Karten, keine Epics.
   */
  @Transactional
  public CardView moveToIdeaStorage(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() == CardType.EPIC) {
      throw new InvalidDependencyException("Epics können nicht in den Ideen-Speicher");
    }
    permissions.require(
        userId, boardService.requireProjectId(card.requireBoardId()), Permission.CARD_MOVE);
    activity.add(
        card.requireId(),
        userId,
        CardActivityType.IDEA_STORED,
        "In den Ideen-Speicher",
        clock.instant(),
        actor.current());
    CardView result = view(cards.save(card.asPooledIdea(card.boardId())));
    publishChanged(card.requireBoardId(), ActivityType.MOVED, card.requireId());
    publishIdeasChanged(card.projectId());
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
    return createProjectIdea(userId, projectId, title, description, targetBoardId, null).view();
  }

  /**
   * Wie {@link #createProjectIdea(long, long, String, String, Long)}, aber idempotent über einen
   * optionalen externen Schlüssel (Issue #534): Existiert im Projekt bereits eine Karte mit diesem
   * Schlüssel — gleich ob Pool, Board, archiviert oder Papierkorb —, wird nichts angelegt und die
   * bestehende Karte mit {@code created=false} zurückgegeben; es entsteht dann weder ein
   * Aktivitätseintrag noch ein SSE-Ereignis. Erst endgültiges Löschen (purge) gibt den Schlüssel
   * frei.
   *
   * <p>Nebenläufigkeit: bewusst SELECT-first statt Insert-and-catch — nach einer
   * Constraint-Verletzung wäre die Transaktion rollback-only, ein Nachlesen in ihr unmöglich. Den
   * seltenen Wettlauf zweier gleichzeitiger Erst-Ingests fängt der partielle Unique-Index (V24) als
   * Backstop; er endet als 409 über die bestehende {@code DataIntegrityViolationException}-
   * Behandlung (#496), und der Wiederholungs-Request trifft dann den SELECT.
   */
  @Transactional
  public IdeaCreation createProjectIdea(
      long userId,
      long projectId,
      String title,
      @Nullable String description,
      @Nullable Long targetBoardId,
      @Nullable String externalKey) {
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    if (externalKey != null) {
      Optional<Card> existing = cards.findByProjectIdAndExternalKey(projectId, externalKey);
      if (existing.isPresent()) {
        return new IdeaCreation(view(existing.get()), false);
      }
    }
    CardView created =
        storeProjectIdea(userId, projectId, title, description, targetBoardId, externalKey);
    publishIdeasChanged(projectId);
    return new IdeaCreation(created, true);
  }

  /**
   * Legt mehrere board-lose Ideen in einem Zug im projektweiten Pool an — der Backend-Teil des
   * Spezifikations-Imports (#492). Recht: {@link Permission#TICKET_CREATE}, einmal für den ganzen
   * Stapel geprüft; importieren ist fachlich dasselbe wie Ideen anlegen, nur in Menge, deshalb kein
   * eigenes Recht. Das optionale {@code targetBoardId} gilt für alle Ideen des Stapels (Vorauswahl
   * beim späteren Einplanen, wie beim Token-Ingest).
   *
   * <p><b>Alles-oder-nichts.</b> Die Methode läuft in einer Transaktion: schlägt eine Idee fehl,
   * entsteht keine. Ein halb importierter Fachbereichs-Spec wäre schwerer aufzuräumen (welche
   * Abschnitte fehlen?) als ein wiederholter Import. Feld- und Mengengrenzen prüft bereits die
   * Bean-Validation am Endpoint, sodass ein ungültiges Element gar nicht bis hierher gelangt.
   *
   * <p><b>Ein Ereignis für den ganzen Stapel</b> statt eines je Karte: Der SSE-Vertrag des
   * Ideen-Pools meldet nur „hat sich geändert", woraufhin ein offenes Ideen-Fenster die Liste
   * komplett neu lädt — n Ereignisse lösten n identische Neuladungen aus.
   *
   * <p>Die Ideen landen bewusst im Pool und nicht in einer Board-Spalte (wie beim Token-Ingest, s.
   * {@code KanbanCompatService}): Was von außen hereinkommt, plant ein Mensch bewusst ein.
   */
  @Transactional
  public List<CardView> createProjectIdeas(
      long userId, long projectId, List<NewIdea> ideas, @Nullable Long targetBoardId) {
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    List<CardView> created =
        ideas.stream()
            .map(
                idea ->
                    storeProjectIdea(
                        userId, projectId, idea.title(), idea.description(), targetBoardId, null))
            .toList();
    publishIdeasChanged(projectId);
    return created;
  }

  /**
   * Schreibt eine einzelne Pool-Idee (ohne Rechteprüfung und ohne SSE-Ereignis) — gemeinsamer Kern
   * von {@link #createProjectIdea} und {@link #createProjectIdeas}, damit der Stapel mit einer
   * Rechteprüfung und einem Ereignis auskommt.
   */
  private CardView storeProjectIdea(
      long userId,
      long projectId,
      String title,
      @Nullable String description,
      @Nullable Long targetBoardId,
      @Nullable String externalKey) {
    Instant now = clock.instant();
    // #402: Pool-Ideen bekommen sofort eine projektweite Nummer (referenzierbar wie Board-Karten);
    // sie bleiben board-los und behalten die Nummer beim späteren Einplanen.
    int number = cards.allocateCardNumber(projectId);
    Card saved =
        cards.save(
            new Card(
                null,
                null,
                null,
                number,
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
                targetBoardId,
                externalKey));
    activity.add(
        saved.requireId(), userId, CardActivityType.CREATED, "Idee angelegt", now, actor.current());
    return view(saved);
  }

  /**
   * Eine anzulegende Pool-Idee im Stapel: Titel und optionale Beschreibung. Das Zielboard steht
   * bewusst nicht hier, sondern gilt für den ganzen Stapel (ein Import bedient ein Board).
   */
  public record NewIdea(String title, @Nullable String description) {}

  /** Ergebnis eines idempotenten Ingests (#534): die Karte plus ob sie neu angelegt wurde. */
  public record IdeaCreation(CardView view, boolean created) {}

  /**
   * Plant eine Idee ins Backlog (erste Spalte) eines Boards desselben Projekts ein: setzt
   * Board/Spalte/Nummer/Position, löscht das Ideen-Flag und den Zielboard-Hinweis. Recht {@link
   * Permission#TICKET_CREATE} im Zielboard.
   */
  @Transactional
  public CardView planOntoBoard(long userId, long cardId, long targetBoardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    long targetProjectId = boardService.requireProjectId(targetBoardId);
    // Nur auf ein Board des eigenen Projekts einplanbar (kein Existenz-Leak fremder Boards).
    if (!Objects.equals(targetProjectId, card.projectId())) {
      throw new BoardNotFoundException();
    }
    permissions.require(userId, targetProjectId, Permission.TICKET_CREATE);
    ColumnView backlog = boardService.firstColumn(targetBoardId);
    long columnId = backlog.id();
    // #402: eine bereits nummerierte Pool-Idee behält ihre Nummer; nur Legacy-Ideen ohne Nummer
    // bekommen beim Einplanen eine.
    int number =
        card.number() != null ? card.requireNumber() : cards.allocateCardNumber(card.projectId());
    int position = cards.allocateActivePosition(columnId);
    Instant now = clock.instant();
    Card planned = cards.save(card.withPlannedOnBoard(targetBoardId, columnId, number, position));
    transitions.open(cardId, columnId, backlog.name(), now);
    activity.add(
        cardId, userId, CardActivityType.PROMOTED, "Auf Board eingeplant", now, actor.current());
    publishChanged(targetBoardId, ActivityType.CREATED, cardId);
    publishIdeasChanged(card.projectId());
    return view(planned);
  }

  /**
   * Holt eine board-gebundene Karte zurück in den projektweiten Ideen-Pool (board-los); das
   * bisherige Board wird als Zielboard-Hinweis notiert. Recht {@link Permission#CARD_MOVE}.
   */
  @Transactional
  public CardView moveBackToPool(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.require(
        userId, boardService.requireProjectId(card.requireBoardId()), Permission.CARD_MOVE);
    Card pooled = cards.save(card.asPooledIdea(card.boardId()));
    activity.add(
        cardId,
        userId,
        CardActivityType.IDEA_STORED,
        "Zurück in den Ideen-Pool",
        clock.instant(),
        actor.current());
    publishChanged(card.requireBoardId(), ActivityType.MOVED, cardId);
    publishIdeasChanged(card.projectId());
    return view(pooled);
  }

  /**
   * Alle Ideen eines Projekts (board-lose Pool-Ideen und board-gebundene Legacy-Ideen), älteste
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

  /**
   * Löst eine projektweite Kartennummer zu ihrer {@link CardView} auf (board-gebundene Karte oder
   * board-lose Pool-Idee). Erfordert Projekt-Mitgliedschaft (Leserecht); Nichtmitglied wie
   * unbekannte oder gelöschte Nummer → 404 (kein Existenz-Leak). Basis für klickbare {@code
   * #N}-Verweise (#403).
   */
  @Transactional(readOnly = true)
  public CardView getByNumber(long userId, long projectId, int number) {
    permissions.requireMembership(userId, projectId);
    Card card =
        cards.findByProjectIdAndNumber(projectId, number).orElseThrow(CardNotFoundException::new);
    return view(card);
  }

  /**
   * Sucht eine projektweite Kartennummer über <strong>alle Projekte, in denen der Benutzer lesen
   * darf</strong>, und liefert je Treffer die Karte samt Ortsangabe (Projekt, Board, Spalte).
   *
   * <p><strong>Warum eine Liste:</strong> Kartennummern sind projektweit eindeutig, nicht global
   * ({@code uq_card_number (project_id, number)}). Dass die Projekte hier faktisch disjunkte
   * Nummernkreise haben (Startnummer-Floor aus V20), ist Konvention und keine Invariante — dieselbe
   * Nummer kann in mehreren Projekten existieren, und dann sind alle Treffer gemeint.
   *
   * <p><strong>Sichtbarkeit:</strong> Gesucht wird ausschließlich in den Projekten des Benutzers.
   * Ein fremdes Projekt macht sich in keiner Weise bemerkbar — kein 403, kein Zähler, kein
   * Unterschied im Antwortverhalten. Die leere Liste ist die Antwort sowohl für „Nummer existiert
   * nirgends" als auch für „Nummer existiert nur in fremden Projekten" (Prinzip aus {@link
   * PermissionChecker}). Ein <b>Plattform-Admin</b> findet dagegen per Definition alles: Die
   * Projektauswahl kommt von {@link ProjectService#listAccessible(long)}, das ihm wie überall sonst
   * (Projektliste, {@code requireMembership}) alle Projekte zeigt. Das ist bewusst das
   * Bestandsverhalten und keine Sonderregel dieser Suche.
   *
   * <p><strong>Was nicht gefunden wird:</strong> Karten im Papierkorb — ihre Nummer bleibt belegt
   * (sie kann wiederhergestellt werden), per Suche sind sie unsichtbar. <b>Archivierte</b> Karten
   * bleiben dagegen auffindbar, ebenso Karten auf einem <b>archivierten Board</b>: Deren Boardname
   * wird über {@link BoardService#requireBoardSummary(long)} aufgelöst, das den Archiv-Filter
   * bewusst nicht anwendet und den Zustand stattdessen mitliefert.
   */
  @Transactional(readOnly = true)
  public List<CardSearchHit> searchByNumber(long userId, int number) {
    Map<Long, String> projectNames =
        projects.listAccessible(userId).stream()
            .collect(
                Collectors.toMap(
                    ProjectService.AccessibleProject::id, ProjectService.AccessibleProject::name));
    if (projectNames.isEmpty()) {
      // Ohne Projekte gibt es nichts zu durchsuchen — und eine leere IN-Menge wäre keine sinnvolle
      // Anfrage an die Datenbank (siehe Zusicherung an CardRepository.findByNumberInProjects).
      return List.of();
    }
    return cards.findByNumberInProjects(number, List.copyOf(projectNames.keySet())).stream()
        .map(c -> hit(c, Objects.requireNonNull(projectNames.get(c.projectId()))))
        .toList();
  }

  /**
   * Baut den Suchtreffer samt Ortsangabe. Eine board-lose Pool-Idee hat weder Board noch Spalte;
   * eine board-gebundene Karte hat beides (die Datenbank lässt seit V18 nichts dazwischen zu), und
   * beides wird über die board-Fassade aufgelöst — der Boardname auch dann, wenn das Board
   * archiviert ist.
   */
  private CardSearchHit hit(Card c, String projectName) {
    Long boardId = c.boardId();
    if (boardId == null) {
      return new CardSearchHit(view(c), c.projectId(), projectName, null, null, false, null, null);
    }
    BoardSummary board = boardService.requireBoardSummary(boardId);
    ColumnView column = boardService.requireColumn(c.requireColumnId(), boardId);
    return new CardSearchHit(
        view(c),
        c.projectId(),
        projectName,
        boardId,
        board.name(),
        board.archived(),
        column.id(),
        column.name());
  }

  /**
   * Aktivitätsverlauf einer Karte (chronologisch). Erfordert Projekt-Mitgliedschaft (Leserecht),
   * geprüft über {@code card.projectId()} — auch für board-lose Pool-Ideen (#405).
   */
  @Transactional(readOnly = true)
  public List<CardActivity> listActivity(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.requireMembership(userId, card.projectId());
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
    publishChanged(card.requireBoardId(), ActivityType.DELETED, card.requireId());
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
    int position = cards.allocateActivePosition(card.requireColumnId());
    cards.restoreFromTrash(card.requireId(), position);
    activity.add(
        card.requireId(),
        userId,
        CardActivityType.RESTORED,
        "Aus Papierkorb wiederhergestellt",
        clock.instant(),
        actor.current());
    publishChanged(card.requireBoardId(), ActivityType.RESTORED, card.requireId());
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
    permissions.require(
        userId, boardService.requireProjectId(card.requireBoardId()), Permission.BOARD_DELETE);
    // Vor dem Delete publizieren (Issue #503): Nachgelagerte Module (Anhänge) planen ihre
    // Aufräum-Aufträge ein, solange die Metadaten existieren — die Cascade nimmt sie gleich mit.
    events.publishEvent(new CardsPurgedEvent(List.of(card.requireId())));
    dependencies.deleteByCardId(card.requireId());
    cards.deleteById(card.requireId());
    publishChanged(card.requireBoardId(), ActivityType.DELETED, card.requireId());
  }

  /** Karten im Papierkorb eines Boards. Erfordert Board-Mitgliedschaft (Leserecht). */
  @Transactional(readOnly = true)
  public List<CardView> listTrash(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    return cards.findTrashByBoardId(boardId).stream()
        .filter(c -> c.type() == CardType.CARD)
        .map(this::view)
        .toList();
  }

  /**
   * Lädt die Karte und verlangt das je nach Kartentyp (Ticket/Epic) passende Recht. Die Rechte sind
   * projekt-basiert und werden über {@code card.projectId()} (immer gesetzt, V18) geprüft — nicht
   * über das Board. So sind auch board-lose Pool-Ideen (#405) editierbar; für board-gebundene
   * Karten ist die Prüfung identisch (Projekt-ID stimmt mit dem Board-Projekt überein).
   */
  private Card requireCardOp(
      long userId, long cardId, Permission ticketPermission, Permission epicPermission) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.require(
        userId, card.projectId(), card.type() == CardType.EPIC ? epicPermission : ticketPermission);
    return card;
  }

  /**
   * Feuert ein Board-Live-Update nur für board-gebundene Karten. Board-lose Pool-Ideen (#405) haben
   * kein Board, das per SSE nachziehen müsste — für sie entfällt das Event.
   */
  private void publishChangedIfOnBoard(@Nullable Long boardId, ActivityType type, long cardId) {
    if (boardId != null) {
      publishChanged(boardId, type, cardId);
    }
  }

  private Card requireEpicInBoard(long epicId, long boardId) {
    Card epic = cards.findById(epicId).orElseThrow(CardNotFoundException::new);
    if (epic.type() != CardType.EPIC || epic.requireBoardId() != boardId) {
      throw new InvalidDependencyException("Kein Epic dieses Boards: " + epicId);
    }
    return epic;
  }

  private void setDependencies(Card card, @Nullable List<Integer> dependsOn) {
    if (dependsOn == null || dependsOn.isEmpty()) {
      dependencies.replaceDependencies(card.requireId(), List.of());
      return;
    }
    List<Integer> distinct = dependsOn.stream().distinct().toList();
    // Querverweise werden projektweit aufgelöst: eine #N-Abhängigkeit darf auf jede Karte desselben
    // Projekts zeigen (board-übergreifend), nicht nur auf dasselbe Board. Board-lose Pool-Ideen
    // (number == null) fallen dabei heraus.
    List<Integer> projectNumbers =
        cards.findByProjectId(card.projectId()).stream()
            .map(Card::number)
            .filter(Objects::nonNull)
            .toList();
    for (Integer dep : distinct) {
      if (dep == card.requireNumber()) {
        throw new InvalidDependencyException("Karte kann nicht von sich selbst abhängen");
      }
      if (!projectNumbers.contains(dep)) {
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

  /**
   * Treffer der projektübergreifenden Nummernsuche: die Karte plus die Angabe, wo sie liegt.
   *
   * @param card die gefundene Karte
   * @param projectId Projekt der Karte (immer gesetzt)
   * @param projectName Name des Projekts — das unterscheidende Merkmal, wenn dieselbe Nummer in
   *     mehreren Projekten existiert
   * @param boardId Board der Karte; {@code null} bei einer board-losen Pool-Idee
   * @param boardName Name des Boards; {@code null} bei einer board-losen Pool-Idee
   * @param boardArchived ob das Board archiviert ist (die Karte bleibt auffindbar, das Board ist
   *     über die normale Board-API aber nicht mehr ladbar); {@code false} ohne Board
   * @param columnId Spalte der Karte; {@code null} bei einer board-losen Pool-Idee
   * @param columnName Name der Spalte; {@code null} bei einer board-losen Pool-Idee
   */
  public record CardSearchHit(
      CardView card,
      Long projectId,
      String projectName,
      @Nullable Long boardId,
      @Nullable String boardName,
      boolean boardArchived,
      @Nullable Long columnId,
      @Nullable String columnName) {}

  /**
   * Schlanke Board-Projektion einer Karte oder eines Epics — ohne Abhängigkeiten, Zuständige und
   * Labels. {@code epic} unterscheidet die beiden Ausprägungen, ohne den Kartentyp aus {@code
   * card.domain} nach außen zu geben.
   */
  public record BoardItemView(
      long id,
      int number,
      String title,
      @Nullable String description,
      @Nullable Long columnId,
      int positionInColumn,
      boolean epic) {}

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
