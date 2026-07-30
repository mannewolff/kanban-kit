package org.mwolff.manban.board.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Board- und Spalten-Use-Cases. Rechte laufen über den {@link PermissionChecker}: BOARD_CREATE
 * (anlegen), BOARD_UPDATE (umbenennen/Spalten), BOARD_DELETE (löschen). Lesezugriffe verlangen nur
 * Projekt-Mitgliedschaft.
 */
@Service
public class BoardService {

  private static final List<String> DEFAULT_COLUMNS =
      List.of("Backlog", "Ready", "In Progress", "In Review", "Done");

  private final BoardRepository boards;
  private final BoardColumnRepository columns;
  private final ColumnCardCounter cardCounter;
  private final PermissionChecker permissions;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public BoardService(
      BoardRepository boards,
      BoardColumnRepository columns,
      ColumnCardCounter cardCounter,
      PermissionChecker permissions,
      ApplicationEventPublisher events,
      Clock clock) {
    this.boards = boards;
    this.columns = columns;
    this.cardCounter = cardCounter;
    this.permissions = permissions;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public BoardView createBoard(long userId, long projectId, String name) {
    permissions.require(userId, projectId, Permission.BOARD_CREATE);
    Board board = boards.save(new Board(null, projectId, name.trim(), clock.instant()));
    for (int i = 0; i < DEFAULT_COLUMNS.size(); i++) {
      columns.save(new BoardColumn(null, board.requireId(), DEFAULT_COLUMNS.get(i), i, null));
    }
    return view(board);
  }

  @Transactional(readOnly = true)
  public List<BoardView> listBoards(long userId, long projectId) {
    permissions.requireMembership(userId, projectId);
    return boards.findByProjectId(projectId).stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public BoardView getBoard(long userId, long boardId) {
    Board board = requireBoard(boardId);
    permissions.requireMembership(userId, board.projectId());
    return view(board);
  }

  @Transactional
  public BoardView renameBoard(long userId, long boardId, String name) {
    Board board = requireBoard(boardId);
    permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);
    return view(boards.save(board.withName(name.trim())));
  }

  /**
   * Archiviert das Board (reversibel), statt es physisch zu löschen. Karten und Spalten bleiben
   * erhalten; das Board verschwindet aus der aktiven Liste und ist für normale Zugriffe nicht mehr
   * auffindbar (→ 404).
   */
  @Transactional
  public void deleteBoard(long userId, long boardId) {
    Board board = requireBoard(boardId);
    permissions.require(userId, board.projectId(), Permission.BOARD_DELETE);
    boards.save(board.archivedAt(clock.instant()));
  }

  @Transactional(readOnly = true)
  public List<BoardView> listArchivedBoards(long userId, long projectId) {
    permissions.requireMembership(userId, projectId);
    return boards.findArchivedByProjectId(projectId).stream().map(this::view).toList();
  }

  /** Hebt die Archivierung auf; das Board ist wieder aktiv und normal auffindbar. */
  @Transactional
  public BoardView restoreBoard(long userId, long boardId) {
    Board board = requireBoardIncludingArchived(boardId);
    permissions.require(userId, board.projectId(), Permission.BOARD_DELETE);
    return view(boards.save(board.restored()));
  }

  /**
   * Löscht ein zuvor archiviertes Board endgültig (physischer Hard-Delete inkl. Cascade auf Spalten
   * und Karten). Ein noch aktives Board muss erst archiviert werden.
   */
  @Transactional
  public void purgeBoard(long userId, long boardId) {
    Board board = requireBoardIncludingArchived(boardId);
    permissions.require(userId, board.projectId(), Permission.BOARD_DELETE);
    if (!board.isArchived()) {
      throw new BoardNotArchivedException();
    }
    // Vor dem Delete publizieren (Issue #503): Das card-Modul löst die betroffenen Karten auf und
    // die Anhänge planen ihre Blob-Löschung ein, solange die Metadaten existieren — die Cascade
    // board → card → attachment_meta nimmt sie gleich mit.
    events.publishEvent(new BoardPurgedEvent(boardId));
    boards.deleteById(boardId);
  }

  @Transactional
  public ColumnView addColumn(long userId, long boardId, String name, @Nullable Integer wipLimit) {
    Board board = requireBoard(boardId);
    permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);
    // Vor dem Lesen sperren: die neue Position entsteht aus dem gelesenen Bestand (#499).
    columns.lockColumnOrder(boardId);
    int nextPosition =
        columns.findByBoardId(boardId).stream().mapToInt(BoardColumn::position).max().orElse(-1)
            + 1;
    return toColumnView(
        columns.save(new BoardColumn(null, boardId, name.trim(), nextPosition, wipLimit)));
  }

  @Transactional
  public ColumnView updateColumn(
      long userId, long columnId, String name, @Nullable Integer wipLimit) {
    BoardColumn column = loadColumn(columnId);
    Board board = requireBoard(column.boardId());
    permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);
    return toColumnView(columns.save(column.with(name.trim(), wipLimit)));
  }

  @Transactional
  public void deleteColumn(long userId, long columnId) {
    BoardColumn column = loadColumn(columnId);
    Board board = requireBoard(column.boardId());
    permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);
    if (cardCounter.countByColumnId(columnId) > 0) {
      throw new ColumnNotEmptyException();
    }
    columns.deleteById(columnId);
  }

  @Transactional
  public List<ColumnView> reorderColumns(long userId, long boardId, List<Long> orderedColumnIds) {
    Board board = requireBoard(boardId);
    permissions.require(userId, board.projectId(), Permission.BOARD_UPDATE);

    // Vor dem Lesen sperren: gegen die gelesene Ordnung wird die Anfrage validiert, und aus ihr
    // entstehen die neuen Positionen. Eine parallel angehängte Spalte bliebe sonst außerhalb der
    // Neuvergabe zurück (#499).
    columns.lockColumnOrder(boardId);
    List<BoardColumn> current = columns.findByBoardId(boardId);
    List<Long> existing = current.stream().map(BoardColumn::id).sorted().toList();
    List<Long> requested = orderedColumnIds.stream().sorted().toList();
    if (!existing.equals(requested)) {
      throw new ColumnNotFoundException();
    }

    columns.reorder(boardId, orderedColumnIds);

    // Antwort in-memory aufbauen: der Direkt-SQL-Reindex umgeht den JPA-L1-Cache,
    // ein erneutes JPA-Read würde veraltete Positionen liefern.
    Map<Long, BoardColumn> byId =
        current.stream().collect(Collectors.toMap(BoardColumn::id, Function.identity()));
    List<ColumnView> result = new ArrayList<>();
    for (int i = 0; i < orderedColumnIds.size(); i++) {
      // Nach dem Set-Abgleich oben ist jede angefragte ID vorhanden; requireNonNull macht
      // diese Invariante fuer NullAway explizit (Map.get liefert @Nullable).
      BoardColumn c = Objects.requireNonNull(byId.get(orderedColumnIds.get(i)));
      result.add(new ColumnView(c.requireId(), c.name(), i, c.wipLimit()));
    }
    return result;
  }

  // --- Fassade fuer modulfremde Use-Cases (Issue #459) ----------------------
  // Die folgenden Methoden pruefen bewusst KEINE Rechte: sie loesen nur Board- und Spaltendaten
  // auf, die der aufrufende fremde Use-Case fuer seine eigene Rechtepruefung erst braucht (z. B.
  // die Projekt-ID). Die Autorisierung bleibt beim Aufrufer.

  /**
   * Projekt-ID des Boards — die Auflösung, die modulfremde Use-Cases für ihre Rechteprüfung
   * brauchen, ohne das Board-Aggregat oder dessen Persistenz-Port zu kennen. Archivierte Boards
   * gelten wie überall als nicht vorhanden.
   *
   * @throws BoardNotFoundException wenn das Board nicht existiert oder archiviert ist
   */
  @Transactional(readOnly = true)
  public long requireProjectId(long boardId) {
    return requireBoard(boardId).projectId();
  }

  /**
   * Projekt-ID des Boards, sofern vorhanden — die Variante für Aufrufer, die ein unbekanntes Board
   * fachlich anders behandeln als mit 404 (der {@code accesstoken}-Ingest wertet es als
   * unschlüssige Bindung).
   */
  @Transactional(readOnly = true)
  public Optional<Long> findProjectId(long boardId) {
    return boards.findById(boardId).map(Board::projectId);
  }

  /**
   * Name und Archiv-Zustand des Boards — <strong>einschließlich archivierter Boards</strong>. Für
   * modulfremde Use-Cases, die den Ort einer Sache benennen müssen, statt auf das Board zuzugreifen
   * (die Kartensuche nennt Projekt/Board/Spalte eines Treffers, #489).
   *
   * <p>Bewusst anders als {@link #requireProjectId(long)} und {@link #getBoard(long, long)}: Dort
   * gilt ein archiviertes Board wie nicht vorhanden, weil dort etwas mit ihm geschehen soll. Hier
   * wird es nur benannt — eine Karte auf einem archivierten Board bleibt auffindbar, und ein
   * Treffer ohne Boardnamen wäre für den Suchenden weniger nützlich, nicht sicherer. Der
   * Archiv-Zustand wird deshalb mitgeliefert statt verschwiegen.
   *
   * @throws BoardNotFoundException wenn das Board nicht existiert
   */
  @Transactional(readOnly = true)
  public BoardSummary requireBoardSummary(long boardId) {
    Board board = requireBoardIncludingArchived(boardId);
    return new BoardSummary(board.requireId(), board.name(), board.isArchived());
  }

  /**
   * Spalte des Boards. Die Board-Zugehörigkeit ist Teil der Zusicherung: eine Spalte eines anderen
   * Boards gilt wie eine unbekannte Spalte (kein Existenz-Leak fremder Boards).
   *
   * @throws ColumnNotFoundException wenn die Spalte nicht existiert oder zu einem anderen Board
   *     gehört
   */
  @Transactional(readOnly = true)
  public ColumnView requireColumn(long columnId, long boardId) {
    BoardColumn column = loadColumn(columnId);
    // column.boardId() ist ein Long, boardId ein long: Java entpackt hier und vergleicht die
    // Zahlenwerte. Wird der Parameter je zu Long, vergleicht dieselbe Zeile stillschweigend
    // Referenzen und liefert oberhalb des Long-Caches falsche Ergebnisse.
    if (column.boardId() != boardId) {
      throw new ColumnNotFoundException();
    }
    return toColumnView(column);
  }

  /**
   * Board-ID der Spalte — die Auflösung für modulfremde Use-Cases, die nur eine Spalten-ID kennen
   * (das Sortieren einer Spalte spricht {@code /api/columns/{columnId}} an) und daraus erst Board
   * und Projekt für ihre eigene Rechteprüfung ableiten müssen.
   *
   * <p>Bewusst ohne Board-Parameter und damit ohne die Zusicherung von {@link #requireColumn(long,
   * long)}: Wer das Board noch gar nicht kennt, kann es nicht mitgeben. Deshalb auch bewusst ohne
   * {@code require*}-Präfix — der verspricht im Bestand eine Zugehörigkeitsprüfung, die diese
   * Methode nicht leistet. Die Rechteprüfung des Aufrufers findet danach auf dem hier aufgelösten
   * Projekt statt — ein Existenz-Leak entsteht nicht, weil ohne Mitgliedschaft 404 folgt.
   *
   * @throws ColumnNotFoundException wenn die Spalte nicht existiert
   */
  @Transactional(readOnly = true)
  public long boardIdOfColumn(long columnId) {
    return loadColumn(columnId).boardId();
  }

  /** Spalten des Boards, aufsteigend nach Position (leer, wenn das Board keine Spalten hat). */
  @Transactional(readOnly = true)
  public List<ColumnView> listColumns(long boardId) {
    return sortedColumns(boardId);
  }

  /**
   * Erste Spalte des Boards (kleinste Position) — die Backlog-Spalte, in der neu eingeplante Karten
   * und Epics landen.
   *
   * @throws ColumnNotFoundException wenn das Board keine Spalte hat
   */
  @Transactional(readOnly = true)
  public ColumnView firstColumn(long boardId) {
    return sortedColumns(boardId).stream().findFirst().orElseThrow(ColumnNotFoundException::new);
  }

  // Ohne eigenes @Transactional: wird von listColumns/firstColumn (je @Transactional) aufgerufen,
  // ohne Self-Invocation über den Proxy (java:S6809).
  private List<ColumnView> sortedColumns(long boardId) {
    return columns.findByBoardId(boardId).stream()
        .sorted(Comparator.comparingInt(BoardColumn::position))
        .map(BoardService::toColumnView)
        .toList();
  }

  private Board requireBoard(long boardId) {
    return boards.findById(boardId).orElseThrow(BoardNotFoundException::new);
  }

  private Board requireBoardIncludingArchived(long boardId) {
    return boards.findByIdIncludingArchived(boardId).orElseThrow(BoardNotFoundException::new);
  }

  private BoardColumn loadColumn(long columnId) {
    return columns.findById(columnId).orElseThrow(ColumnNotFoundException::new);
  }

  private BoardView view(Board board) {
    List<ColumnView> columnViews =
        columns.findByBoardId(board.requireId()).stream().map(BoardService::toColumnView).toList();
    return new BoardView(
        board.requireId(), board.projectId(), board.name(), board.createdAt(), columnViews);
  }

  private static ColumnView toColumnView(BoardColumn c) {
    return new ColumnView(c.requireId(), c.name(), c.position(), c.wipLimit());
  }

  /** Board inkl. seiner Spalten. */
  public record BoardView(
      Long id, Long projectId, String name, Instant createdAt, List<ColumnView> columns) {}

  /** Spaltendarstellung. */
  public record ColumnView(Long id, String name, int position, @Nullable Integer wipLimit) {}

  /** Board-Kurzinfo für Ortsangaben: Name plus Archiv-Zustand (siehe requireBoardSummary). */
  public record BoardSummary(Long id, String name, boolean archived) {}
}
