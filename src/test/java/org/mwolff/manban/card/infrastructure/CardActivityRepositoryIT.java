package org.mwolff.manban.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardActivityRepository;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Adapter-Test für den Karten-Aktivitätsverlauf (add/findByCardId). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardActivityRepositoryIT extends AbstractIntegrationTest {

  private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-01-01T11:00:00Z");

  @Autowired private CardActivityRepository activity;
  @Autowired private JdbcTemplate jdbc;

  private long cardId;
  private long userId;

  @BeforeEach
  void seed() {
    userId =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('a@example.com', 'x', 'A') RETURNING id");
    long projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
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
  void addAndFindReturnsChronologicalHistory() {
    activity.add(cardId, userId, CardActivityType.CREATED, "Karte angelegt", T1);
    activity.add(cardId, userId, CardActivityType.MOVED, "Verschoben nach Done", T2);

    List<CardActivity> history = activity.findByCardId(cardId);

    assertThat(history).hasSize(2);
    assertThat(history.get(0).type()).isEqualTo(CardActivityType.CREATED);
    assertThat(history.get(0).detail()).isEqualTo("Karte angelegt");
    assertThat(history.get(0).actorUserId()).isEqualTo(userId);
    assertThat(history.get(0).createdAt()).isEqualTo(T1);
    assertThat(history.get(1).type()).isEqualTo(CardActivityType.MOVED);
  }

  @Test
  void findByCardIdReturnsEmptyForUnknownCard() {
    assertThat(activity.findByCardId(cardId + 999)).isEmpty();
  }
}
