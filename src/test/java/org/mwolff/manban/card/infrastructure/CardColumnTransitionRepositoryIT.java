package org.mwolff.manban.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardColumnTransitionRepository;
import org.mwolff.manban.card.domain.CardColumnTransition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Adapter-Test für die Spaltenaufenthalts-Historie (open/closeOpen/findByCardId). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardColumnTransitionRepositoryIT extends AbstractIntegrationTest {

  private static final Instant ENTERED = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant LEFT = ENTERED.plusSeconds(90);

  @Autowired private CardColumnTransitionRepository transitions;
  @Autowired private JdbcTemplate jdbc;

  private long cardId;
  private long columnId;

  @BeforeEach
  void seedCard() {
    long userId =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('t@example.com', 'x', 'T') RETURNING id");
    long projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    long boardId =
        insert("INSERT INTO board (project_id, name) VALUES (" + projectId + ", 'B') RETURNING id");
    columnId =
        insert(
            "INSERT INTO board_column (board_id, name, position) "
                + "VALUES ("
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
  void openCreatesSingleOpenRow() {
    transitions.open(cardId, columnId, "Ready", ENTERED);

    List<CardColumnTransition> rows = transitions.findByCardId(cardId);
    assertThat(rows).hasSize(1);
    CardColumnTransition row = rows.get(0);
    assertThat(row.id()).isNotNull();
    assertThat(row.cardId()).isEqualTo(cardId);
    assertThat(row.columnId()).isEqualTo(columnId);
    assertThat(row.columnName()).isEqualTo("Ready");
    assertThat(row.enteredAt()).isEqualTo(ENTERED);
    assertThat(row.leftAt()).isNull();
    assertThat(row.durationSeconds()).isNull();
  }

  @Test
  void closeOpenSetsLeftAtAndDuration() {
    transitions.open(cardId, columnId, "Ready", ENTERED);

    transitions.closeOpen(cardId, LEFT);

    CardColumnTransition row = transitions.findByCardId(cardId).get(0);
    assertThat(row.leftAt()).isEqualTo(LEFT);
    assertThat(row.durationSeconds()).isEqualTo(90L);
  }

  @Test
  void closeOpenWithoutOpenRowIsNoop() {
    transitions.open(cardId, columnId, "Ready", ENTERED);
    transitions.closeOpen(cardId, LEFT);

    // Zweiter Aufruf ohne offene Zeile darf die geschlossene Zeile nicht verändern.
    transitions.closeOpen(cardId, LEFT.plusSeconds(1000));

    List<CardColumnTransition> rows = transitions.findByCardId(cardId);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).leftAt()).isEqualTo(LEFT);
    assertThat(rows.get(0).durationSeconds()).isEqualTo(90L);
  }

  @Test
  void closeOpenOnCardWithNoRowsIsNoop() {
    transitions.closeOpen(cardId, LEFT);

    assertThat(transitions.findByCardId(cardId)).isEmpty();
  }

  @Test
  void reopenKeepsHistoryInChronologicalOrder() {
    transitions.open(cardId, columnId, "Ready", ENTERED);
    transitions.closeOpen(cardId, LEFT);
    transitions.open(cardId, columnId, "Done", LEFT);

    List<CardColumnTransition> rows = transitions.findByCardId(cardId);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).columnName()).isEqualTo("Ready");
    assertThat(rows.get(0).leftAt()).isEqualTo(LEFT);
    assertThat(rows.get(1).columnName()).isEqualTo("Done");
    assertThat(rows.get(1).leftAt()).isNull();
    assertThat(rows.get(1).durationSeconds()).isNull();
  }

  @Test
  void findByCardIdReturnsEmptyForUnknownCard() {
    assertThat(transitions.findByCardId(cardId + 999)).isEmpty();
  }
}
