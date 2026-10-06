package org.mwolff.manban.comment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.LaufstandView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Laufstand-Kommentare eines Projekts für den Fortschritt eines laufenden Laufs (Issue #1373,
 * Plan #1372 E5).
 *
 * <p>Gegen die echte Datenbank, weil die Abfrage je Karte genau einen Kommentar auswählt und über
 * die Karte auf das Projekt eingrenzt — das entscheidet ihr SQL, nicht der Code darum herum.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CommentLaufstandIT extends AbstractIntegrationTest {

  private static final Instant LANGE_HER = Instant.parse("2020-01-01T00:00:00Z");

  @Autowired private CommentService comments;
  @Autowired private JdbcTemplate jdbc;

  private long userId;
  private long projectId;
  private long boardId;
  private long columnId;
  private int naechsteNummer = 1;

  private long id(String sql, Object... args) {
    Long wert = jdbc.queryForObject(sql, Long.class, args);
    return wert == null ? 0L : wert;
  }

  @BeforeEach
  void seed() {
    userId =
        id(
            "INSERT INTO app_user (email, password_hash, display_name)"
                + " VALUES ('laufstand@example.com', 'x', 'L') RETURNING id");
    projectId =
        id("INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id", userId);
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role) VALUES (?, ?, 'OWNER')",
        projectId,
        userId);
    boardId = id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", projectId);
    columnId =
        id(
            "INSERT INTO board_column (board_id, name, position) VALUES (?, 'Ready', 0)"
                + " RETURNING id",
            boardId);
  }

  private long karte(long board, long spalte) {
    int nummer = naechsteNummer;
    naechsteNummer++;
    return id(
        "INSERT INTO card (board_id, column_id, number, title, position_in_column)"
            + " VALUES (?, ?, ?, 'Karte', ?) RETURNING id",
        board,
        spalte,
        nummer,
        nummer);
  }

  private long karte() {
    return karte(boardId, columnId);
  }

  private long kommentar(long cardId, String body) {
    return comments.create(userId, cardId, body).id();
  }

  @Test
  void liefertJeKarteDenLaufstandMitKarteUndBody() {
    long karte = karte();
    kommentar(karte, "Erst ein anderer Kommentar");
    kommentar(karte, "## Laufstand\n\nplan begonnen für #1 um 2026-10-03T15:01:00Z");

    assertThat(comments.laufstaendeImProjekt(projectId))
        .containsExactly(
            new LaufstandView(
                karte, "## Laufstand\n\nplan begonnen für #1 um 2026-10-03T15:01:00Z", null));
  }

  /** AK: Das Kit ersetzt den Laufstand über {@code update} — geliefert wird der neue Body (E5). */
  @Test
  void einErsetzterLaufstandKommtMitDemNeuenBody() {
    long karte = karte();
    long laufstand = kommentar(karte, "## Laufstand\n\nplan begonnen");

    comments.update(userId, laufstand, "## Laufstand\n\nplan fertig");

    assertThat(comments.laufstaendeImProjekt(projectId))
        .containsExactly(new LaufstandView(karte, "## Laufstand\n\nplan fertig", null));
  }

  /** Issue #1428: Der Laufstand trägt die Kennung seines letzten Schreibers (E3). */
  @Test
  void einLaufstandTraegtDieLaufkennungSeinesLetztenSchreibers() {
    long karte = karte();
    Instant erster = Instant.parse("2026-10-05T08:00:00Z");
    Instant zweiter = Instant.parse("2026-10-05T09:00:00.123Z");
    long laufstand = comments.create(userId, karte, "## Laufstand\n\nplan begonnen", erster).id();

    comments.update(userId, laufstand, "## Laufstand\n\nplan fertig", zweiter);

    assertThat(comments.laufstaendeImProjekt(projectId))
        .containsExactly(new LaufstandView(karte, "## Laufstand\n\nplan fertig", zweiter));
  }

  /** {@code updatedAt} wird nicht ausgewertet (A1/E5): Ein alter Zeitstempel hält nichts zurück. */
  @Test
  void einLaufstandMitAltemUpdatedAtWirdTrotzdemGeliefert() {
    long karte = karte();
    long laufstand = kommentar(karte, "## Laufstand\n\nreview fertig");
    jdbc.update(
        "UPDATE comment SET created_at = ?, updated_at = ? WHERE id = ?",
        OffsetDateTime.ofInstant(LANGE_HER, ZoneOffset.UTC),
        OffsetDateTime.ofInstant(LANGE_HER, ZoneOffset.UTC),
        laufstand);

    assertThat(comments.laufstaendeImProjekt(projectId))
        .extracting(LaufstandView::cardId)
        .containsExactly(karte);
  }

  @Test
  void kommentareOhneLaufstandAnkerWerdenNichtGeliefert() {
    long karte = karte();
    kommentar(karte, "Kein Laufstand");
    kommentar(karte, "Text davor\n## Laufstand");

    assertThat(comments.laufstaendeImProjekt(projectId)).isEmpty();
  }

  @Test
  void stehenZweiLaufstaendeAnEinerKarteGiltDerJuengste() {
    long karte = karte();
    long alt = kommentar(karte, "## Laufstand\n\nalt");
    kommentar(karte, "## Laufstand\n\nneu");
    jdbc.update(
        "UPDATE comment SET created_at = ? WHERE id = ?",
        OffsetDateTime.ofInstant(LANGE_HER, ZoneOffset.UTC),
        alt);

    assertThat(comments.laufstaendeImProjekt(projectId))
        .containsExactly(new LaufstandView(karte, "## Laufstand\n\nneu", null));
  }

  @Test
  void laufstaendeEinesFremdenProjektsWerdenNichtGeliefert() {
    long fremdesProjekt =
        id("INSERT INTO project (name, owner_user_id) VALUES ('Q', ?) RETURNING id", userId);
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role) VALUES (?, ?, 'OWNER')",
        fremdesProjekt,
        userId);
    long fremdesBoard =
        id("INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id", fremdesProjekt);
    long fremdeSpalte =
        id(
            "INSERT INTO board_column (board_id, name, position) VALUES (?, 'Ready', 0)"
                + " RETURNING id",
            fremdesBoard);
    kommentar(karte(fremdesBoard, fremdeSpalte), "## Laufstand\n\nfremd");

    assertThat(comments.laufstaendeImProjekt(projectId)).isEmpty();
  }
}
