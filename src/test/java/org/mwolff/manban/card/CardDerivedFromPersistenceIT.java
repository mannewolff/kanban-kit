package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistenz der Herkunft (V26): Der Wert überlebt Speichern und Laden, und die Port-Abfrage {@code
 * findByDerivedFrom} liefert <strong>alle</strong> Kinder.
 *
 * <p>Der zweite Teil ist nicht optional: PIT und JaCoCo schließen {@code
 * org.mwolff.manban.*.infrastructure.*} aus (siehe {@code pom.xml}). Ein falsches {@code WHERE} im
 * Adapter bliebe ohne diesen Test grün und fiele erst beim Aufräumen des Projektwechsels auf — dann
 * als Fehler des falschen Pakets.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardDerivedFromPersistenceIT extends AbstractIntegrationTest {

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
                + "VALUES ('h@example.com', 'x', 'H') RETURNING id");
    projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    boardId =
        insert("INSERT INTO board (project_id, name) VALUES (" + projectId + ", 'B') RETURNING id");
    columnId =
        insert(
            "INSERT INTO board_column (board_id, name, position) VALUES ("
                + boardId
                + ", 'Backlog', 0) RETURNING id");
  }

  @Test
  void herkunft_ueberlebtSpeichernUndLaden() {
    Card vorfahr = cards.save(karte("Vorfahr", 1, null, false, false));

    Card kind = cards.save(karte("Kind", 2, vorfahr.requireId(), false, false));

    assertThat(cards.findById(kind.requireId()).orElseThrow().derivedFromCardId())
        .isEqualTo(vorfahr.requireId());
  }

  @Test
  void findByDerivedFrom_liefertAlleKinder_auchArchivierteUndPoolIdeen() {
    Card vorfahr = cards.save(karte("Vorfahr", 1, null, false, false));
    long v = vorfahr.requireId();

    Card normal = cards.save(karte("Normal", 2, v, false, false));
    Card archiviert = cards.save(karte("Archiviert", 3, v, true, false));
    Card poolIdee = cards.save(karte("Pool", 4, v, false, true));
    cards.save(karte("Ohne Herkunft", 5, null, false, false));

    List<Card> kinder = cards.findByDerivedFrom(v);

    assertThat(kinder)
        .extracting(Card::requireId)
        .containsExactlyInAnyOrder(
            normal.requireId(), archiviert.requireId(), poolIdee.requireId());
  }

  @Test
  void findByDerivedFrom_liefertLeer_ohneKinder() {
    Card einsam = cards.save(karte("Einsam", 1, null, false, false));

    assertThat(cards.findByDerivedFrom(einsam.requireId())).isEmpty();
  }

  private Card karte(
      String titel, int nummer, Long herkunft, boolean archiviert, boolean ideenSpeicher) {
    return new Card(
        null,
        ideenSpeicher ? null : boardId,
        ideenSpeicher ? null : columnId,
        nummer,
        titel,
        null,
        // Position = Nummer: aktive Karten teilen sich (board_id, column_id, active_position),
        // eine feste 0 kollidierte auf uq_card_active_position.
        nummer,
        archiviert,
        ideenSpeicher,
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
        herkunft,
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
