package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.domain.Card;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** End-to-End der Papierkorb-Persistenz (softDelete/findTrash/restore/purge/Retention). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardSoftDeleteIT extends AbstractIntegrationTest {

  private static final Instant DELETED_AT = Instant.parse("2026-01-01T10:00:00Z");

  @Autowired private CardRepository cards;
  @Autowired private JdbcTemplate jdbc;

  private long boardId;
  private long columnId;
  private long cardId;

  @BeforeEach
  void seed() {
    long userId =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('a@example.com', 'x', 'A') RETURNING id");
    long projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    boardId =
        insert("INSERT INTO board (project_id, name) VALUES (" + projectId + ", 'B') RETURNING id");
    columnId =
        insert(
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + boardId
                + ", 'Ready', 0) RETURNING id");
    cardId =
        insert(
            "INSERT INTO card (board_id, column_id, number, title, position_in_column) "
                + "VALUES ("
                + boardId
                + ", "
                + columnId
                + ", 1, 'A', 0) RETURNING id");
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    return id == null ? 0L : id;
  }

  @Test
  void softDeleteMovesCardToTrashAndRestoreBringsItBack() {
    cards.softDelete(cardId, DELETED_AT);

    // Aus der Board-Ansicht verschwunden, im Papierkorb sichtbar.
    assertThat(cards.findByBoardId(boardId)).isEmpty();
    assertThat(cards.findTrashByBoardId(boardId))
        .extracting(Card::requireId)
        .containsExactly(cardId);
    // Aus dem aktiven Positions-Namespace gefallen (active_position NULL).
    assertThat(
            jdbc.queryForObject(
                "SELECT active_position FROM card WHERE id = ?", Integer.class, cardId))
        .isNull();

    cards.restoreFromTrash(cardId, 0);

    assertThat(cards.findTrashByBoardId(boardId)).isEmpty();
    assertThat(cards.findByBoardId(boardId)).extracting(Card::requireId).containsExactly(cardId);
  }

  @Test
  void findPurgeableTrashReturnsCardsDeletedBeforeThreshold() {
    cards.softDelete(cardId, DELETED_AT);

    assertThat(cards.findPurgeableTrash(DELETED_AT.plusSeconds(1)))
        .extracting(Card::requireId)
        .containsExactly(cardId);
    assertThat(cards.findPurgeableTrash(DELETED_AT.minusSeconds(1))).isEmpty();

    cards.deleteById(cardId);
    assertThat(cards.findTrashByBoardId(boardId)).isEmpty();
  }

  @Test
  void softDeletedCardDoesNotBlockActivePosition() {
    cards.softDelete(cardId, DELETED_AT);

    // Neue Karte an Position 0 in derselben Spalte -> kein Unique-Konflikt mit der gelöschten.
    jdbc.update(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column) "
            + "VALUES ("
            + boardId
            + ", "
            + columnId
            + ", 2, 'B', 0)");

    assertThat(cards.findByBoardId(boardId)).extracting(Card::number).containsExactly(2);
  }
}
