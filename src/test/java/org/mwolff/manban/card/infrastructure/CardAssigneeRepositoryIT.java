package org.mwolff.manban.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardAssigneeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Adapter-Test für die Zuständigen einer Karte (replace/find/delete). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardAssigneeRepositoryIT extends AbstractIntegrationTest {

  @Autowired private CardAssigneeRepository assignees;
  @Autowired private JdbcTemplate jdbc;

  private long cardId;
  private long userA;
  private long userB;

  @BeforeEach
  void seed() {
    userA =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('a@example.com', 'x', 'A') RETURNING id");
    userB =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('b@example.com', 'x', 'B') RETURNING id");
    long projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userA + ") RETURNING id");
    long boardId =
        insert("INSERT INTO board (project_id, name) VALUES (" + projectId + ", 'B') RETURNING id");
    long columnId =
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
  void replaceAndFindReturnsAssignees() {
    assignees.replaceAssignees(cardId, List.of(userB, userA));

    assertThat(assignees.findByCardId(cardId)).containsExactly(userA, userB); // sortiert nach id
  }

  @Test
  void replaceOverwritesPreviousAssignees() {
    assignees.replaceAssignees(cardId, List.of(userA, userB));
    assignees.replaceAssignees(cardId, List.of(userB));

    assertThat(assignees.findByCardId(cardId)).containsExactly(userB);
  }

  @Test
  void replaceWithEmptyClearsAssignees() {
    assignees.replaceAssignees(cardId, List.of(userA));
    assignees.replaceAssignees(cardId, List.of());

    assertThat(assignees.findByCardId(cardId)).isEmpty();
  }

  @Test
  void deleteByCardIdRemovesAll() {
    assignees.replaceAssignees(cardId, List.of(userA, userB));
    assignees.deleteByCardId(cardId);

    assertThat(assignees.findByCardId(cardId)).isEmpty();
  }
}
