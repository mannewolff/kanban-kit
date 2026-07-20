package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.domain.Card;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistenz des Ideen-Speicher-Zustands: idea_stored fällt aus dem aktiven Positions-Namespace.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardIdeaStorageIT extends AbstractIntegrationTest {

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
  void ideaStoredCardFallsOutOfActiveNamespace() {
    Card card = cards.findById(cardId).orElseThrow();

    cards.save(card.asIdeaStored());

    assertThat(
            jdbc.queryForObject(
                "SELECT active_position FROM card WHERE id = ?", Integer.class, cardId))
        .isNull();
  }

  @Test
  void ideaStoredCardDoesNotBlockActivePosition() {
    Card card = cards.findById(cardId).orElseThrow();
    cards.save(card.asIdeaStored());

    // Neue Karte an Position 0 in derselben Spalte -> kein Unique-Konflikt mit der Idee.
    jdbc.update(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column) "
            + "VALUES ("
            + boardId
            + ", "
            + columnId
            + ", 2, 'B', 0)");

    assertThat(cards.findByBoardId(boardId)).extracting(Card::number).contains(2);
  }
}
