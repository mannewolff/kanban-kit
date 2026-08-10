package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.domain.BoardColumn;
import org.mwolff.manban.card.application.CardDependencyRepository;
import org.mwolff.manban.card.application.CardMovedConcurrentlyException;
import org.mwolff.manban.card.application.CardNumberConflictException;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.ProjectStartNumberService;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.Project;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Weist gegen echtes PostgreSQL nach, dass Kartennummern und Spaltenpositionen auch unter echter
 * Nebenläufigkeit eindeutig und lückenlos vergeben werden (Issue #499) — und dass die dafür nötigen
 * Sperren <em>nicht weiter greifen als nötig</em>.
 *
 * <p>Beide Vergaben waren „lies das Maximum, schreibe Maximum + 1" ohne Sperre. Unter READ
 * COMMITTED lasen zwei gleichzeitige Anlagen dasselbe Maximum, rechneten dieselbe Nummer bzw.
 * dieselbe Position aus und schrieben beide — die Unique-Constraints {@code uq_card_number} und
 * {@code uq_card_active_position} verhinderten zwar falsche Daten, aber ein fachlich gültiger
 * Request scheiterte.
 *
 * <p>{@link TransactionRace#run} erzwingt die Lage deterministisch: Der zweite Aufruf muss
 * nachweislich auf einer Sperre warten, sonst schlägt der Lauf fehl. Jeder Test ist damit zugleich
 * seine eigene Gegenprobe. {@link TransactionRace#runUnblocked} prüft die Gegenrichtung — dort muss
 * der zweite Aufruf durchlaufen, während der erste seine Sperren hält.
 *
 * <p>Die Kontext-Konfiguration ist bewusst identisch mit den übrigen {@code
 * WebEnvironment.NONE}-ITs: Ein eigener Spring-Kontext brächte einen weiteren Verbindungspool mit
 * und sprengte die {@code max_connections} des geteilten Postgres-Containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardNumberAndPositionConcurrencyIT extends AbstractIntegrationTest {

  @Autowired private CardService cardService;
  @Autowired private BoardService boardService;
  @Autowired private ProjectStartNumberService startNumbers;
  @Autowired private CardRepository cards;
  @Autowired private CardDependencyRepository dependencies;
  @Autowired private BoardColumnRepository columns;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectRepository projects;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  // --- Kartennummern -------------------------------------------------------

  @Test
  void concurrentCreate_inTheSameProject_yieldsDistinctConsecutiveNumbers() throws Exception {
    long user = admin("race-number@example.com");
    long projectId = project(user);
    long boardId = board(user, projectId);
    long backlog = column(boardId, 0);

    TransactionRace.Result race =
        race(
            () -> cardService.create(user, boardId, backlog, "A", null, null, null),
            () -> cardService.create(user, boardId, backlog, "B", null, null, null));

    // Kein Request scheitert — das ist der Kern des Akzeptanzkriteriums.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(numbers(projectId)).containsExactly(1, 2);
  }

  @Test
  void concurrentCreate_respectsTheProjectStartNumberFloor() throws Exception {
    long user = admin("race-floor@example.com");
    long projectId = project(user);
    long boardId = board(user, projectId);
    long backlog = column(boardId, 0);
    startNumbers.setNextCardNumber(user, projectId, 500);

    TransactionRace.Result race =
        race(
            () -> cardService.create(user, boardId, backlog, "A", null, null, null),
            () -> cardService.create(user, boardId, backlog, "B", null, null, null));

    // Der Floor aus V20 wirkt unverändert: die Vergabe startet bei 500, nicht bei 1.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(numbers(projectId)).containsExactly(500, 501);
  }

  @Test
  void concurrentCreate_inDifferentProjects_doesNotBlock() throws Exception {
    long user = admin("race-two-projects@example.com");
    long projectA = project(user);
    long boardA = board(user, projectA);
    long columnA = column(boardA, 0);
    long projectB = project(user);
    long boardB = board(user, projectB);
    long columnB = column(boardB, 0);

    TransactionRace.Result race =
        raceUnblocked(
            () -> cardService.create(user, boardA, columnA, "A", null, null, null),
            () -> cardService.create(user, boardB, columnB, "B", null, null, null));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(numbers(projectA)).containsExactly(1);
    assertThat(numbers(projectB)).containsExactly(1);
  }

  // --- Positionen in der Spalte -------------------------------------------

  @Test
  void concurrentCreate_inTheSameColumn_yieldsGaplessPositions() throws Exception {
    long user = admin("race-position@example.com");
    long projectId = project(user);
    long boardId = board(user, projectId);
    long backlog = column(boardId, 0);

    TransactionRace.Result race =
        race(
            () -> cardService.create(user, boardId, backlog, "A", null, null, null),
            () -> cardService.create(user, boardId, backlog, "B", null, null, null));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(positions(backlog)).containsExactly(0, 1);
  }

  @Test
  void concurrentMove_intoTheSameColumn_isSerialisedAndLeavesGaplessPositions() throws Exception {
    long user = admin("race-move-same@example.com");
    long projectId = project(user);
    long boardId = board(user, projectId);
    long backlog = column(boardId, 0);
    long ready = column(boardId, 1);
    long first = card(user, boardId, backlog, "A");
    long second = card(user, boardId, backlog, "B");
    long resident = card(user, boardId, ready, "R");

    TransactionRace.Result race =
        race(
            () -> cardService.move(user, first, ready, 0),
            () -> cardService.move(user, second, ready, 0));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(positions(ready)).containsExactly(0, 1, 2);
    assertThat(cards.findById(resident).orElseThrow().columnId()).isEqualTo(ready);
    assertThat(positions(backlog)).isEmpty();
  }

  @Test
  void concurrentMove_intoDifferentColumns_doesNotBlock() throws Exception {
    long user = admin("race-move-different@example.com");
    long projectId = project(user);
    long boardId = board(user, projectId);
    long first = card(user, boardId, column(boardId, 0), "A");
    long second = card(user, boardId, column(boardId, 2), "B");
    long targetOfFirst = column(boardId, 1);
    long targetOfSecond = column(boardId, 3);

    TransactionRace.Result race =
        raceUnblocked(
            () -> cardService.move(user, first, targetOfFirst, 0),
            () -> cardService.move(user, second, targetOfSecond, 0));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(positions(targetOfSecond)).containsExactly(0);
  }

  @Test
  void concurrentMove_ofTheSameCard_rejectsTheSecondAsConflict() throws Exception {
    long user = admin("race-move-same-card@example.com");
    long projectId = project(user);
    long boardId = board(user, projectId);
    long backlog = column(boardId, 0);
    long ready = column(boardId, 1);
    long review = column(boardId, 2);
    long cardId = card(user, boardId, backlog, "A");

    TransactionRace.Result race =
        race(
            () -> cardService.move(user, cardId, ready, 0),
            () -> cardService.move(user, cardId, review, 0));

    // Der zweite Aufruf hat die Quellspalte gesperrt, die die Karte inzwischen verlassen hat —
    // seine Sperrmenge deckt den tatsächlichen Umzug nicht ab. Ein 409 ist die ehrliche Antwort.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isInstanceOf(CardMovedConcurrentlyException.class);
    assertThat(cards.findById(cardId).orElseThrow().columnId()).isEqualTo(ready);
  }

  @Test
  void concurrentTransfer_intoTheSameColumn_isSerialisedAndLeavesGaplessPositions()
      throws Exception {
    long user = admin("race-transfer@example.com");
    long projectId = project(user);
    long sourceBoard = board(user, projectId);
    long targetBoard = board(user, projectId);
    long targetColumn = column(targetBoard, 0);
    long first = card(user, sourceBoard, column(sourceBoard, 0), "A");
    long second = card(user, sourceBoard, column(sourceBoard, 0), "B");

    TransactionRace.Result race =
        race(
            () -> cardService.transfer(user, first, targetBoard, targetColumn),
            () -> cardService.transfer(user, second, targetBoard, targetColumn));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(positions(targetColumn)).containsExactly(0, 1);
  }

  @Test
  void concurrentBulkAndSingleTransfer_acrossProjects_doNotDeadlock() throws Exception {
    // Given: zwei Quellprojekte, ein gemeinsames Zielprojekt. Der Sammel-Umzug sperrt Spalten
    // vorab, der Einzel-Umzug das Zielprojekt zuerst — griffen beide ihre zwei Ressourcen in
    // unterschiedlicher Reihenfolge, verklemmten sie (#499).
    long user = admin("race-bulk-vs-single@example.com");
    long targetProject = project(user);
    long targetBoard = board(user, targetProject);
    long targetColumn = column(targetBoard, 0);
    long sourceBoardA = board(user, project(user));
    long sourceBoardB = board(user, project(user));
    long bulkCard = card(user, sourceBoardA, column(sourceBoardA, 0), "Bulk");
    long singleCard = card(user, sourceBoardB, column(sourceBoardB, 0), "Single");

    TransactionRace.Result race =
        race(
            () -> cardService.bulkTransfer(user, List.of(bulkCard), targetBoard, targetColumn),
            () -> cardService.transfer(user, singleCard, targetBoard, targetColumn));

    // Der zweite Aufruf wartet — auf der Projektsperre, nicht in einer Verklemmung.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(positions(targetColumn)).containsExactly(0, 1);
    assertThat(numbers(targetProject)).containsExactly(1, 2);
  }

  // --- Spaltenpositionen ---------------------------------------------------

  @Test
  void concurrentAddColumn_onTheSameBoard_yieldsDistinctPositions() throws Exception {
    long user = admin("race-add-column@example.com");
    long projectId = project(user);
    long boardId = board(user, projectId);

    TransactionRace.Result race =
        race(
            () -> boardService.addColumn(user, boardId, "Sechste", null),
            () -> boardService.addColumn(user, boardId, "Siebte", null));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(columns.findByBoardId(boardId))
        .extracting(BoardColumn::position)
        .doesNotHaveDuplicates();
    assertThat(columns.findByBoardId(boardId)).hasSize(7);
  }

  @Test
  void concurrentReorderAndAddColumn_onTheSameBoard_isSerialised() throws Exception {
    long user = admin("race-reorder-column@example.com");
    long projectId = project(user);
    long boardId = board(user, projectId);
    List<Long> reversed =
        columns.findByBoardId(boardId).stream()
            .map(BoardColumn::requireId)
            .sorted()
            .toList()
            .reversed();

    TransactionRace.Result race =
        race(
            () -> boardService.reorderColumns(user, boardId, reversed),
            () -> boardService.addColumn(user, boardId, "Sechste", null));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    // Die neue Spalte hängt sich hinter den umsortierten Bestand — lückenlos 0..5.
    assertThat(columns.findByBoardId(boardId))
        .extracting(BoardColumn::position)
        .containsExactly(0, 1, 2, 3, 4, 5);
  }

  @Test
  void concurrentAddColumn_onDifferentBoards_doesNotBlock() throws Exception {
    long user = admin("race-add-column-two-boards@example.com");
    long projectId = project(user);
    long boardA = board(user, projectId);
    long boardB = board(user, projectId);

    TransactionRace.Result race =
        raceUnblocked(
            () -> boardService.addColumn(user, boardA, "Sechste", null),
            () -> boardService.addColumn(user, boardB, "Sechste", null));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(columns.findByBoardId(boardB)).hasSize(6);
  }

  // --- Vorgegebene Nummern und Abhängigkeiten (#565/#566) ------------------

  @Test
  void concurrentImportOfTheSameGivenNumber_isSerialisedAndOnlyOneWins() throws Exception {
    // Deterministische Fassung des Rennens aus #565: Der zweite Aufruf muss nachweislich auf der
    // Sperre des ersten warten. Ohne sie entschiede der Unique-Constraint — und der liefert
    // denselben Statuscode, weshalb ein Statusvergleich allein die Sperre nicht beweist.
    long user = admin("race-given-number@example.com");
    long projectId = project(user);
    long boardId = board(user, projectId);
    long backlog = column(boardId, 0);

    TransactionRace.Result race =
        race(
            () -> cardService.createDirect(user, boardId, backlog, "A", null, "k-a", 900),
            () -> cardService.createDirect(user, boardId, backlog, "B", null, "k-b", 900));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isInstanceOf(CardNumberConflictException.class);
    assertThat(numbers(projectId)).containsExactly(900);
  }

  @Test
  void concurrentDependencyReplacement_endsWithOneCompleteList() throws Exception {
    // Ersetzen-Semantik unter Nebenläufigkeit (#566): Ohne Sperre auf der Kartenzeile löschen
    // beide Transaktionen nichts (es gibt keine Zeilen) und fügen anschließend beide ein — das
    // Ergebnis wäre die Vereinigung statt einer der beiden Listen.
    long user = admin("race-deps@example.com");
    long projectId = project(user);
    long boardId = board(user, projectId);
    long backlog = column(boardId, 0);
    long target = card(user, boardId, backlog, "Ziel");

    TransactionRace.Result race =
        race(
            () ->
                cardService.replaceDependenciesFromIngest(user, target, projectId, List.of(11, 12)),
            () ->
                cardService.replaceDependenciesFromIngest(
                    user, target, projectId, List.of(21, 22)));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    // Genau eine der beiden Listen — keine Vereinigung, keine Mischung.
    List<Integer> deps = dependencies.findByCardId(target).stream().sorted().toList();
    assertThat(deps).isIn(List.of(11, 12), List.of(21, 22));
  }

  // --- Fixtures ------------------------------------------------------------

  private TransactionRace.Result race(Runnable first, Runnable second) throws InterruptedException {
    return new TransactionRace(transactionManager, dataSource).run(first, second);
  }

  private TransactionRace.Result raceUnblocked(Runnable first, Runnable second)
      throws InterruptedException {
    return new TransactionRace(transactionManager, dataSource).runUnblocked(first, second);
  }

  /** Plattform-Admin: passiert jede Rechteprüfung, spart das Mitgliedschafts-Setup. */
  private long admin(String email) {
    return users.save(new AppUser(null, email, "hash", "A", true, PlatformRole.ADMIN)).requireId();
  }

  private long project(long ownerUserId) {
    return projects.save(new Project(null, "Rennen", ownerUserId, Instant.now())).requireId();
  }

  private long board(long userId, long projectId) {
    return boardService.createBoard(userId, projectId, "Brett").id();
  }

  private long column(long boardId, int position) {
    return columns.findByBoardId(boardId).get(position).requireId();
  }

  private long card(long userId, long boardId, long columnId, String title) {
    return cardService.create(userId, boardId, columnId, title, null, null, null).id();
  }

  private List<Integer> numbers(long projectId) {
    return cards.findByProjectId(projectId).stream().map(Card::requireNumber).sorted().toList();
  }

  /**
   * Die aktiven Positionen der Spalte, aufsteigend — genau die Menge, über die {@code
   * uq_card_active_position} wacht (archivierte, gelöschte, Pool-Ideen und Epics fallen mit {@code
   * active_position = NULL} heraus). Bewusst direkt per SQL statt über den Port: Die Erwartung soll
   * den Datenbankstand prüfen, nicht die Sicht der Anwendung.
   */
  private List<Integer> positions(long columnId) {
    return new JdbcTemplate(dataSource)
        .queryForList(
            "SELECT position_in_column FROM card WHERE column_id = ? AND archived = false"
                + " AND idea_stored = false AND deleted_at IS NULL AND type <> 'EPIC'"
                + " ORDER BY position_in_column",
            Integer.class,
            columnId);
  }
}
