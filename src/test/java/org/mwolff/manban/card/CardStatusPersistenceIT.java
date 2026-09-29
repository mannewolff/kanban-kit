package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistenz des Status (V46, Issue #1298): Der Wert überlebt Speichern und Laden als
 * Konstantenname von {@link CardStatus}, und {@code null} bleibt {@code null}.
 *
 * <p>PIT und JaCoCo schließen {@code org.mwolff.manban.*.infrastructure.*} aus (siehe {@code
 * pom.xml}); ohne diesen Test bliebe eine vergessene Übersetzung in {@code CardEntity} oder {@code
 * CardRepositoryAdapter} unbemerkt, bis das erste Paket den Status setzt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardStatusPersistenceIT extends AbstractIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Autowired private CardRepository cards;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;
  private long boardId;
  private long columnId;

  @BeforeEach
  void seed() {
    long userId =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('s@example.com', 'x', 'S') RETURNING id");
    projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    boardId =
        insert("INSERT INTO board (project_id, name) VALUES (" + projectId + ", 'B') RETURNING id");
    columnId =
        insert(
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + boardId
                + ", 'Anstehend', 0) RETURNING id");
  }

  @Test
  void status_ueberlebtSpeichernUndLaden() {
    Card gespeichert = cards.save(karte(1).withStatus(CardStatus.IN_REVIEW));

    assertThat(cards.findById(gespeichert.requireId()).orElseThrow().status())
        .isEqualTo(CardStatus.IN_REVIEW);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM card WHERE id = ?", String.class, gespeichert.requireId()))
        .as("gespeichert wird der Konstantenname")
        .isEqualTo("IN_REVIEW");
  }

  @Test
  void ohneStatus_bleibtNull() {
    Card gespeichert = cards.save(karte(2));

    assertThat(cards.findById(gespeichert.requireId()).orElseThrow().status()).isNull();
  }

  private Card karte(int nummer) {
    return new Card(
        null,
        boardId,
        columnId,
        nummer,
        "Paket " + nummer,
        null,
        nummer,
        false,
        null,
        null,
        NOW,
        NOW,
        CardType.CARD,
        null,
        null,
        null,
        projectId,
        null,
        null,
        null,
        null);
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    if (id == null) {
      throw new IllegalStateException("kein Schluessel: " + sql);
    }
    return id;
  }
}
