package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardArchiveService;
import org.mwolff.manban.card.application.CardBestandRepository;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.application.TrashRetentionService;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Herkunftsnummer vor dem Purge (Issue #1576, Plan #1574 E12/E17/E18): Alle drei Purge-Wege —
 * einzeln, Papierkorb-Frist, Board — bewahren die Nummer des gelöschten Vorfahren an seinen Kindern
 * in {@code card.derived_from_lost_number}; das Neusetzen der Herkunft über {@code save} setzt sie
 * zurück.
 *
 * <p>Wie bei {@code CardDerivedFromPersistenceIT} nicht optional: PIT und JaCoCo schließen {@code
 * org.mwolff.manban.*.infrastructure.*} aus, die nativen Abfragen des Adapters prüft nur dieser
 * Test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardPurgeHerkunftIT extends AbstractIntegrationTest {

  private static final Instant JETZT = Instant.parse("2026-07-14T00:00:00Z");

  @Autowired private CardRepository cards;
  @Autowired private CardBestandRepository bestand;
  @Autowired private CardArchiveService archiv;
  @Autowired private TrashRetentionService papierkorb;
  @Autowired private BoardService boardService;
  @Autowired private JdbcTemplate jdbc;

  private long userId;
  private long projectId;
  private long boardA;
  private long spalteA;
  private long boardB;
  private long spalteB;
  private int naechsteNummer = 1;

  @BeforeEach
  void seed() {
    userId =
        id(
            "INSERT INTO app_user (email, display_name)"
                + " VALUES ('herkunft@example.com', 'H') RETURNING id");
    projectId =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", userId);
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role) VALUES (?, ?, 'OWNER')",
        projectId,
        userId);
    boardA = id("INSERT INTO board (project_id, name) VALUES (?, 'A') RETURNING id", projectId);
    spalteA = spalte(boardA);
    boardB = id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", projectId);
    spalteB = spalte(boardB);
  }

  @Test
  void purgeEinzeln_bewahrtNummerAmKindAufAnderemBoard() {
    Card vorfahr = karte(boardA, spalteA, null);
    Card kind = karte(boardB, spalteB, vorfahr.requireId());

    archiv.purge(userId, vorfahr.requireId());

    assertThat(bestand.verloreneHerkunftsnummern(boardB))
        .containsExactlyEntriesOf(Map.of(kind.requireId(), vorfahr.number()));
    assertThat(cards.findById(kind.requireId()).orElseThrow().derivedFromCardId()).isNull();
  }

  @Test
  void papierkorbFrist_bewahrtNummerAmKind() {
    Card vorfahr = karte(boardA, spalteA, null);
    Card kind = karte(boardB, spalteB, vorfahr.requireId());
    cards.softDelete(vorfahr.requireId(), JETZT.minusSeconds(40L * 24 * 3600));

    assertThat(papierkorb.purgeExpiredTrash(JETZT, 30)).isEqualTo(1);

    assertThat(bestand.verloreneHerkunftsnummern(boardB))
        .containsExactlyEntriesOf(Map.of(kind.requireId(), vorfahr.number()));
  }

  @Test
  void boardPurge_bewahrtNummerAmKindAufAnderemBoard() {
    Card vorfahr = karte(boardA, spalteA, null);
    Card kind = karte(boardB, spalteB, vorfahr.requireId());
    jdbc.update("UPDATE board SET archived_at = now() WHERE id = ?", boardA);

    boardService.purgeBoard(userId, boardA);

    assertThat(bestand.verloreneHerkunftsnummern(boardB))
        .containsExactlyEntriesOf(Map.of(kind.requireId(), vorfahr.number()));
  }

  @Test
  void neusetzenDerHerkunft_setztVerloreneNummerZurueck() {
    Card vorfahr = karte(boardA, spalteA, null);
    Card neu = karte(boardA, spalteA, null);
    Card kind = karte(boardB, spalteB, vorfahr.requireId());
    archiv.purge(userId, vorfahr.requireId());

    Card geladen = cards.findById(kind.requireId()).orElseThrow();
    cards.save(geladen.withDerivedFrom(neu.requireId()));
    // Herkunft wieder entfernen: Hätte save die Spalte nicht zurückgesetzt, erschiene die alte
    // Nummer jetzt erneut als verloren.
    cards.save(cards.findById(kind.requireId()).orElseThrow().withDerivedFrom(null));

    assertThat(bestand.verloreneHerkunftsnummern(boardB)).isEmpty();
  }

  @Test
  void karteMitGesetzterHerkunft_erscheintNieAlsVerloren() {
    Card vorfahr = karte(boardA, spalteA, null);
    Card kind = karte(boardB, spalteB, vorfahr.requireId());
    // Bewahren ohne Purge: Die Spalte trägt eine Nummer, die Herkunft steht aber noch.
    bestand.bewahreHerkunftsnummern(List.of(vorfahr.requireId()));

    assertThat(
            jdbc.queryForObject(
                "SELECT derived_from_lost_number FROM card WHERE id = ?",
                Integer.class,
                kind.requireId()))
        .isEqualTo(vorfahr.number());
    assertThat(bestand.verloreneHerkunftsnummern(boardB)).isEmpty();
  }

  @Test
  void bewahren_mitLeererSammlung_istNoOp() {
    bestand.bewahreHerkunftsnummern(List.of());

    assertThat(bestand.verloreneHerkunftsnummern(boardB)).isEmpty();
  }

  @Test
  void verloreneHerkunftsnummern_nurDesGefragtenBoards() {
    Card vorfahr = karte(boardA, spalteA, null);
    karte(boardB, spalteB, vorfahr.requireId());

    archiv.purge(userId, vorfahr.requireId());

    assertThat(bestand.verloreneHerkunftsnummern(boardA)).isEmpty();
  }

  private Card karte(long board, long spalte, Long herkunft) {
    int nummer = naechsteNummer;
    naechsteNummer++;
    return cards.save(
        new Card(
            null,
            board,
            spalte,
            nummer,
            "Karte " + nummer,
            null,
            nummer,
            false,
            null,
            null,
            JETZT,
            JETZT,
            CardType.CARD,
            null,
            null,
            null,
            projectId,
            null,
            herkunft,
            null,
            null));
  }

  private long spalte(long board) {
    return id(
        "INSERT INTO board_column (board_id, name, position) VALUES (?, 'Ready', 0) RETURNING id",
        board);
  }

  private long id(String sql, Object... args) {
    Long wert = jdbc.queryForObject(sql, Long.class, args);
    if (wert == null) {
      throw new IllegalStateException("kein Schluessel: " + sql);
    }
    return wert;
  }
}
