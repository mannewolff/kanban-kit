package org.mwolff.manban.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.CardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Adapter-Test der schmalen Existenzabfrage zu Kartennummern (Issue #1169).
 *
 * <p>Belegt die Sichtbarkeitsgrenze gegen Postgres: dieselbe wie bei {@code
 * findByProjectIdAndNumber} — Papierkorb-Karten zählen nicht, archivierte zählen mit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CardRepositoryExistingNumbersIT extends AbstractIntegrationTest {

  @Autowired private CardRepository cards;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;
  private long fremdesProjekt;

  @BeforeEach
  void seedCards() {
    long userId =
        insert(
            "INSERT INTO app_user (email, password_hash, display_name) "
                + "VALUES ('t@example.com', 'x', 'T') RETURNING id");
    projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    fremdesProjekt =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('Q', " + userId + ") RETURNING id");

    long spalte = boardMitSpalte(projectId);
    long fremdeSpalte = boardMitSpalte(fremdesProjekt);

    karte(spalte, 964, 0, false, false);
    karte(spalte, 965, 1, true, false);
    karte(spalte, 966, 2, false, true);
    karte(fremdeSpalte, 970, 0, false, false);
  }

  private long boardMitSpalte(long project) {
    long boardId =
        insert("INSERT INTO board (project_id, name) VALUES (" + project + ", 'B') RETURNING id");
    return insert(
        "INSERT INTO board_column (board_id, name, position) "
            + "VALUES ("
            + boardId
            + ", 'Ready', 0) RETURNING id");
  }

  private void karte(
      long columnId, int number, int position, boolean archiviert, boolean geloescht) {
    Long boardId =
        jdbc.queryForObject("SELECT board_id FROM board_column WHERE id = ?", Long.class, columnId);
    jdbc.update(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column, "
            + "archived, deleted_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        boardId,
        columnId,
        number,
        "Karte " + number,
        position,
        archiviert,
        geloescht
            ? java.sql.Timestamp.from(java.time.Instant.parse("2026-01-01T00:00:00Z"))
            : null);
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    return id == null ? 0L : id;
  }

  @Test
  void archivierteZaehltMitPapierkorbNicht() {
    assertThat(cards.findExistingNumbers(projectId, List.of(964, 965, 966)))
        .containsExactlyInAnyOrder(964, 965);
  }

  @Test
  void nummernFremderProjekteWerdenNichtGeliefert() {
    assertThat(cards.findExistingNumbers(projectId, List.of(970))).isEmpty();
    assertThat(cards.findExistingNumbers(fremdesProjekt, List.of(964, 970))).containsExactly(970);
  }

  @Test
  void unbekannteNummernFehlenImErgebnis() {
    assertThat(cards.findExistingNumbers(projectId, List.of(964, 4711))).containsExactly(964);
  }

  @Test
  void eineLeereNummernmengeLiefertEineLeereMenge() {
    assertThat(cards.findExistingNumbers(projectId, Set.of())).isEmpty();
  }
}
