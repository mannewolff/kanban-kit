package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardLabelRepository;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.LabelService;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.Project;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Weist gegen echtes PostgreSQL nach, dass die Label-Zuordnung einer Karte <em>atomar</em> entsteht
 * (Issue #574): Zwei gleichzeitige Hinzufügungen derselben {@code (card_id, label_id)}-Zuordnung
 * enden beide erfolgreich, und danach existiert genau eine Zeile.
 *
 * <p>Dieser Test ist der einzige, der die geforderte Atomarität wirklich belegt. Ein sequenzieller
 * Idempotenz-Test besteht auch bei der ausdrücklich verbotenen Umsetzung „erst {@code SELECT}, dann
 * {@code INSERT}" — dort läge zwischen Prüfung und Einfügen ein offenes Rennen. Die Idempotenz
 * gehört deshalb in die Datenbank ({@code INSERT … ON CONFLICT DO NOTHING} auf dem Primärschlüssel
 * {@code pk_card_label}).
 *
 * <p>{@link TransactionRace#run} erzwingt die Lage deterministisch: Der zweite Aufruf muss
 * nachweislich auf einer Sperre warten, sonst schlägt der Lauf fehl — der Test ist damit zugleich
 * seine eigene Gegenprobe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardLabelAtomicityIT extends AbstractIntegrationTest {

  @Autowired private CardService cardService;
  @Autowired private BoardService boardService;
  @Autowired private LabelService labelService;
  @Autowired private CardLabelRepository cardLabels;
  @Autowired private BoardColumnRepository columns;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectRepository projects;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  @Test
  void concurrentAssignmentOfTheSameLabel_bothSucceedAndLeaveExactlyOneRow() throws Exception {
    long user = admin("race-label@example.com");
    long projectId = project(user);
    long boardId = boardService.createBoard(user, projectId, "Brett").id();
    long columnId = columns.findByBoardId(boardId).get(0).requireId();
    long cardId = cardService.create(user, boardId, columnId, "Karte", null, null, null).id();
    long labelId = labelService.create(user, boardId, "kit:nightrun", "#f00").requireId();

    TransactionRace.Result race =
        new TransactionRace(transactionManager, dataSource)
            .run(
                () -> cardLabels.addLabel(cardId, labelId),
                () -> cardLabels.addLabel(cardId, labelId));

    // Kein Aufruf scheitert — das ist der Kern des Akzeptanzkriteriums.
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(assignments(cardId, labelId)).isEqualTo(1);
  }

  // --- Fixtures ------------------------------------------------------------

  /** Plattform-Admin: passiert jede Rechteprüfung, spart das Mitgliedschafts-Setup. */
  private long admin(String email) {
    return users.save(new AppUser(null, email, "hash", "A", true, PlatformRole.ADMIN)).requireId();
  }

  private long project(long ownerUserId) {
    return projects.save(new Project(null, "Rennen", ownerUserId, Instant.now())).requireId();
  }

  /**
   * Zeilenzahl direkt per SQL statt über den Port: Die Erwartung soll den Datenbankstand prüfen,
   * nicht die Sicht der Anwendung.
   */
  private int assignments(long cardId, long labelId) {
    Integer value =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT count(*) FROM card_label WHERE card_id = ? AND label_id = ?",
                Integer.class,
                cardId,
                labelId);
    return value == null ? 0 : value;
  }
}
