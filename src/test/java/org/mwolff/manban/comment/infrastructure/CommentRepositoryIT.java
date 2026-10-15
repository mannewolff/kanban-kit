package org.mwolff.manban.comment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.comment.application.CommentRepository;
import org.mwolff.manban.comment.domain.Comment;
import org.mwolff.manban.comment.domain.LaufKommentar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
    return comments.save(
        new Comment(null, cardId, userId, "A", body, createdAt, createdAt, null, null, null, null));
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

  @Test
  void laufKommentareFiltertNachLaufkennungNichtNachErstellzeitpunkt() {
    // Issue #1755, Plan #1752 E5: Der Laufstand wird je Run ersetzt und behält sein altes
    // created_at; er zählt über die Laufkennung des letzten Schreibers.
    Instant von = T2;
    Instant alterLauf = T1.minusSeconds(60);
    Instant neuerLauf = T2.plusSeconds(60);
    Comment laufstand =
        comments.save(
            new Comment(
                null, cardId, userId, "A", "## Laufstand", T1, T1, alterLauf, null, null, null));
    comments.save(laufstand.withBody("## Laufstand neu", neuerLauf));
    comments.save(
        new Comment(
            null, cardId, userId, "A", "spaet, alter Lauf", T2, T2, alterLauf, null, null, null));
    comments.save(
        new Comment(null, cardId, userId, "A", "ohne Kennung", T2, T2, null, null, null, null));
    comments.save(new Comment(null, cardId, userId, "A", "auf von", T1, T1, von, null, null, null));

    assertThat(comments.findLaufKommentare(List.of(cardId), von))
        .containsExactly(new LaufKommentar(cardId, von), new LaufKommentar(cardId, neuerLauf));
  }

  @Test
  void laufKommentareNurDerGefragtenKarten() {
    comments.save(new Comment(null, cardId, userId, "A", "Lauf", T2, T2, T2, null, null, null));

    assertThat(comments.findLaufKommentare(List.of(cardId + 1), T1)).isEmpty();
    assertThat(comments.findLaufKommentare(List.of(), T1)).isEmpty();
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
  void laufkennungUebersteht_denRundwegDerSpalteRunStartedAt() {
    // Issue #1428: die Laufkennung des letzten Schreibers liegt in comment.run_started_at.
    Instant lauf = Instant.parse("2026-10-05T08:58:22.123Z");
    Comment gespeichert =
        comments.save(
            new Comment(null, cardId, userId, "A", "## Laufstand", T1, T1, lauf, null, null, null));

    assertThat(gespeichert.laufStart()).isEqualTo(lauf);
    assertThat(comments.findById(gespeichert.requireId()))
        .get()
        .extracting(Comment::laufStart)
        .isEqualTo(lauf);
    assertThat(
            jdbc.queryForObject(
                "SELECT run_started_at FROM comment WHERE id = ?",
                Timestamp.class,
                gespeichert.requireId()))
        .isEqualTo(Timestamp.from(lauf));
  }

  @Test
  void ohneLaufkennungBleibtDieSpalteRunStartedAtLeer() {
    Comment gespeichert = save("ohne Kennung", T1);

    assertThat(comments.findById(gespeichert.requireId()))
        .get()
        .extracting(Comment::laufStart)
        .isNull();
  }

  @Test
  void herkunftUebersteht_denRundwegDerSpaltenOriginTokenNameAgent() {
    // Issue #1620: die Herkunft liegt in comment.origin, token_name und agent (V52).
    Comment gespeichert =
        comments.save(
            new Comment(
                null, cardId, userId, "A", "per Token", T1, T1, null, "TOKEN", "kit", "opus"));

    assertThat(comments.findById(gespeichert.requireId()))
        .get()
        .extracting(Comment::origin, Comment::tokenName, Comment::agent)
        .containsExactly("TOKEN", "kit", "opus");
    assertThat(
            jdbc.queryForObject(
                "SELECT origin || '/' || token_name || '/' || agent FROM comment WHERE id = ?",
                String.class,
                gespeichert.requireId()))
        .isEqualTo("TOKEN/kit/opus");
  }

  @Test
  void ohneHerkunftBleibenDieSpaltenLeer() {
    // A6: ein Kommentar ohne Stempel trägt keine Herkunft — unbekannt, nicht geraten.
    Comment gespeichert = save("ohne Herkunft", T1);

    assertThat(comments.findById(gespeichert.requireId()))
        .get()
        .extracting(Comment::origin, Comment::tokenName, Comment::agent)
        .containsOnlyNulls();
  }

  @Test
  void pruefregelWeistUnbekannteHerkunftAb() {
    // chk_comment_origin: nur SESSION, TOKEN oder NULL.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO comment (card_id, author_user_id, author_name, body, created_at, "
                        + "updated_at, origin) VALUES (?, ?, 'A', 'x', ?, ?, 'UPLOAD')",
                    cardId,
                    userId,
                    Timestamp.from(T1),
                    Timestamp.from(T1)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("chk_comment_origin");
  }

  @Test
  void findByCardIdReturnsEmptyForUnknownCard() {
    assertThat(comments.findByCardId(cardId + 999)).isEmpty();
  }
}
