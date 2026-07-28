package org.mwolff.manban.comment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.comment.application.CommentRepository;
import org.mwolff.manban.comment.domain.Comment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Adapter-Test für die Kommentar-Persistenz — insbesondere die zugesicherte chronologische
 * Reihenfolge von {@link CommentRepository#findByCardId} inklusive Tie-Break auf der ID (#472).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CommentRepositoryIT extends AbstractIntegrationTest {

  private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-01-01T11:00:00Z");

  @Autowired private CommentRepository comments;
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

  private Comment save(String body, Instant createdAt) {
    return comments.save(new Comment(null, cardId, userId, "A", body, createdAt, createdAt));
  }

  @Test
  void findByCardIdReturnsChronologicalOrder() {
    save("zuerst", T1);
    save("danach", T2);

    assertThat(comments.findByCardId(cardId))
        .extracting(Comment::body)
        .containsExactly("zuerst", "danach");
  }

  @Test
  void findByCardIdBreaksTiesById_whenTimestampsAreIdentical() {
    // Given: zwei Kommentare mit identischem Zeitstempel (Batch-Import in einer Transaktion,
    // gemockte Clock). Die Zeile mit der kleineren ID liegt physisch hinter der größeren — genau
    // die Lage, die in der Praxis nach UPDATE/VACUUM oder unter einem anderen Ausführungsplan
    // entsteht. Ohne Tie-Break sortiert Postgres bei gleichem Schlüssel nur die Scan-Reihenfolge
    // durch und liefert dann die größere ID zuerst.
    insertComment(9002L, "zweiter", T1);
    insertComment(9001L, "erster", T1);

    // Then: die ID entscheidet, nicht die physische Zeilenlage.
    assertThat(comments.findByCardId(cardId))
        .extracting(Comment::body)
        .containsExactly("erster", "zweiter");
  }

  /** Legt einen Kommentar mit fest vorgegebener ID an — die Sequenz vergibt nur aufsteigend. */
  private void insertComment(long id, String body, Instant createdAt) {
    jdbc.update(
        "INSERT INTO comment (id, card_id, author_user_id, author_name, body, created_at, "
            + "updated_at) VALUES (?, ?, ?, 'A', ?, ?, ?)",
        id,
        cardId,
        userId,
        body,
        Timestamp.from(createdAt),
        Timestamp.from(createdAt));
  }

  @Test
  void findByCardIdReturnsEmptyForUnknownCard() {
    assertThat(comments.findByCardId(cardId + 999)).isEmpty();
  }
}
