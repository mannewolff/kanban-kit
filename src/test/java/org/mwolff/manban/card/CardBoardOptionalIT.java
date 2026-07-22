package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistenz des board-optionalen Datenmodells (V18): eine board-lose Pool-Idee lässt sich
 * speichern und laden; der CHECK erzwingt Board-Konsistenz.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardBoardOptionalIT extends AbstractIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired private CardRepository cards;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;
  private long boardId;

  @BeforeEach
  void seed() {
    long userId =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('a@example.com', 'x', 'A') RETURNING id");
    projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    boardId =
        insert("INSERT INTO board (project_id, name) VALUES (" + projectId + ", 'B') RETURNING id");
  }

  @Test
  void boardLosePoolIdee_wirdBoardlosGespeichertUndGeladen() {
    Card idea =
        new Card(
            null,
            null,
            null,
            null,
            "Idee",
            null,
            0,
            false,
            true,
            null,
            null,
            NOW,
            NOW,
            CardType.CARD,
            null,
            null,
            null,
            projectId,
            boardId);

    Card saved = cards.save(idea);
    Card reloaded = cards.findById(saved.requireId()).orElseThrow();

    assertThat(reloaded.boardId()).isNull();
    assertThat(reloaded.columnId()).isNull();
    assertThat(reloaded.number()).isNull();
    assertThat(reloaded.ideaStored()).isTrue();
    assertThat(reloaded.projectId()).isEqualTo(projectId);
    assertThat(reloaded.targetBoardId()).isEqualTo(boardId);
    // board-los + ideaStored -> fällt aus dem aktiven Positions-Namespace.
    assertThat(
            jdbc.queryForObject(
                "SELECT active_position FROM card WHERE id = ?", Integer.class, saved.requireId()))
        .isNull();
  }

  @Test
  void check_weistBoardOhneSpalteAb() {
    // board_id gesetzt, aber column_id NULL -> verletzt ck_card_board_consistency.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO card (project_id, board_id, number, title, position_in_column) "
                        + "VALUES (?, ?, 1, 'X', 0)",
                    projectId,
                    boardId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    return id == null ? 0L : id;
  }
}
