package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
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
 * Persistenz der Anforderungskarte am Vorhaben (V27): Der Wert überlebt Speichern und Laden, und
 * {@code ON DELETE SET NULL} räumt den Verweis auf, statt das Löschen zu verhindern.
 *
 * <p>Warum als Integrationstest und nicht als Unit-Test: Beides — das Durchreichen durch Entity und
 * Adapter und die Wirkung des Fremdschlüssels — entsteht erst in {@code
 * org.mwolff.manban.*.infrastructure.*} bzw. in der Datenbank. Genau dieses Paket schließen PIT und
 * JaCoCo aus (siehe {@code pom.xml}); ein vergessenes Feld im Lesepfad bliebe ohne diesen Test
 * grün. Dieselbe Begründung trägt {@link CardDerivedFromPersistenceIT} für V26.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardRequirementPersistenceIT extends AbstractIntegrationTest {

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
                + "VALUES ('a@example.com', 'x', 'A') RETURNING id");
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
  void anforderung_ueberlebtSpeichernUndLaden() {
    Card anforderung = cards.save(karte("Anforderung", 1, CardType.CARD, null));

    Card vorhaben = cards.save(karte("Vorhaben", 2, CardType.EPIC, anforderung.requireId()));

    assertThat(cards.findById(vorhaben.requireId()).orElseThrow().requirementCardId())
        .isEqualTo(anforderung.requireId());
  }

  @Test
  void ohneAnforderung_bleibtDerVerweisNull() {
    // Gegenprobe zum Test darüber: Ein Lesepfad, der stur die eigene ID oder einen Vorgabewert
    // einsetzt, wäre dort grün und hier rot.
    Card vorhaben = cards.save(karte("Vorhaben ohne Anforderung", 1, CardType.EPIC, null));

    assertThat(cards.findById(vorhaben.requireId()).orElseThrow().requirementCardId()).isNull();
  }

  @Test
  void endgueltigesLoeschenDerAnforderung_raeumtDenVerweisAufUndLaesstDasVorhabenStehen() {
    Card anforderung = cards.save(karte("Anforderung", 1, CardType.CARD, null));
    Card vorhaben = cards.save(karte("Vorhaben", 2, CardType.EPIC, anforderung.requireId()));

    cards.deleteById(anforderung.requireId());

    // Beide Seiten: Das Vorhaben überlebt das Löschen (kein RESTRICT), und sein Verweis ist
    // aufgeräumt (kein verwaister Fremdschlüssel).
    Card neuGeladen = cards.findById(vorhaben.requireId()).orElseThrow();
    assertThat(neuGeladen.requirementCardId()).isNull();
    assertThat(cards.findById(anforderung.requireId())).isEmpty();
  }

  private Card karte(String titel, int nummer, CardType typ, @Nullable Long anforderung) {
    return new Card(
        null,
        boardId,
        columnId,
        nummer,
        titel,
        null,
        // Position = Nummer: aktive Karten teilen sich (board_id, column_id, active_position),
        // eine feste 0 kollidierte auf uq_card_active_position.
        nummer,
        false,
        false,
        null,
        null,
        NOW,
        NOW,
        typ,
        null,
        null,
        null,
        projectId,
        null,
        null,
        null,
        anforderung);
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    if (id == null) {
      throw new IllegalStateException("kein Schluessel: " + sql);
    }
    return id;
  }
}
