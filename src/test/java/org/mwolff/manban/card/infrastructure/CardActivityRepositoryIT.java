package org.mwolff.manban.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.CardActivityRepository;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
    activity.add(
        cardId, userId, CardActivityType.CREATED, "Karte angelegt", T1, ActorStamp.unknown());
    activity.add(
        cardId, userId, CardActivityType.MOVED, "Verschoben nach Done", T2, ActorStamp.unknown());

    List<CardActivity> history = activity.findByCardId(cardId);

    assertThat(history).hasSize(2);
    assertThat(history.get(0).type()).isEqualTo(CardActivityType.CREATED);
    assertThat(history.get(0).detail()).isEqualTo("Karte angelegt");
    assertThat(history.get(0).actorUserId()).isEqualTo(userId);
    assertThat(history.get(0).createdAt()).isEqualTo(T1);
    assertThat(history.get(1).type()).isEqualTo(CardActivityType.MOVED);
  }

  @Test
  void addPersistsActorStampRoundTrip() {
    // Voller Stempel (Token-Herkunft samt Selbstauskunft) übersteht den Persistenz-Roundtrip.
    activity.add(
        cardId,
        userId,
        CardActivityType.CREATED,
        "Idee angelegt",
        T1,
        new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "claude-opus-5", null));

    CardActivity entry = activity.findByCardId(cardId).get(0);

    assertThat(entry.origin()).isEqualTo(CardActivityOrigin.TOKEN);
    assertThat(entry.tokenName()).isEqualTo("Nachtlauf");
    assertThat(entry.agent()).isEqualTo("claude-opus-5");
  }

  @Test
  void addPersistsEmptyStampAsNulls() {
    // Unbekannte Herkunft wird wie ein Alt-Eintrag gespeichert: alle drei Spalten NULL.
    activity.add(
        cardId, userId, CardActivityType.CREATED, "Karte angelegt", T1, ActorStamp.unknown());

    CardActivity entry = activity.findByCardId(cardId).get(0);

    assertThat(entry.origin()).isNull();
    assertThat(entry.tokenName()).isNull();
    assertThat(entry.agent()).isNull();
    assertThat(entry.laufStart()).isNull();
    assertThat(entry.statusAfter()).isNull();
  }

  @Test
  void addPersistsRunAndStatusAfterRoundTrip() {
    // Laufkennung aus dem Stempel und Status nach der Bewegung überstehen den Roundtrip (#1426).
    Instant laufStart = Instant.parse("2026-01-01T09:58:22.123Z");
    activity.add(
        cardId,
        userId,
        CardActivityType.MOVED,
        "Verschoben nach In review",
        T1,
        new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "claude-opus-5", laufStart),
        CardStatus.IN_REVIEW);

    CardActivity entry = activity.findByCardId(cardId).get(0);

    assertThat(entry.laufStart()).isEqualTo(laufStart);
    assertThat(entry.statusAfter()).isEqualTo(CardStatus.IN_REVIEW);
  }

  @Test
  void statusAfterOutsideValueListFailsAtConstraint() {
    // Die Werteliste von chk_card_activity_status_after ist die von chk_card_status (V46).
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO card_activity (card_id, actor_user_id, type, detail, created_at,"
                        + " status_after) VALUES (?, ?, 'MOVED', 'x', now(), 'ARCHIVED')",
                    cardId,
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("chk_card_activity_status_after");
  }

  @Test
  void findByCardIdReturnsEmptyForUnknownCard() {
    assertThat(activity.findByCardId(cardId + 999)).isEmpty();
  }
}
