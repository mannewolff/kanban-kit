package org.mwolff.manban.fremdboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.GITLAB;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.issue;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.labelEreignis;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.notiz;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.statusEreignis;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.stelleNach;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.fremdboard.application.AbgleichService;
import org.mwolff.manban.fremdboard.application.VerbindungRepository;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Abgleich einer Karte mit GitLab (Issue #2167, Plan #2152 A4, A6, A11, A12, A19) gegen eine echte
 * Datenbank und ein nachgestelltes GitLab: Zeitpunkte aus GitLab, jeder Wechsel genau einmal, es
 * gilt der Stand dort. Dieselbe Konfiguration wie {@code FremdBoardAnstossIT}, damit beide einen
 * Kontext teilen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(GitLabNachgestellt.class)
@TestPropertySource(properties = "manban.fremdboard.secret-key=" + GitLabTestServer.SCHLUESSEL)
class AbgleichServiceIT extends AbstractIntegrationTest {

  private static final Instant VORHER = Instant.parse("2026-10-01T08:00:00Z");
  private static final Instant ANGEBUNDEN = Instant.parse("2026-11-01T08:00:00Z");
  private static final Instant T1 = Instant.parse("2026-11-02T09:00:00Z");
  private static final Instant T2 = Instant.parse("2026-11-02T11:30:00Z");
  private static final Instant T3 = Instant.parse("2026-11-03T14:45:00Z");
  private static final String PAKET = "Export als CSV";

  @Autowired private AbgleichService abgleich;
  @Autowired private VerbindungRepository verbindungen;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;
  private Verbindung verbindung;

  @BeforeEach
  void aufbau() {
    GITLAB.zuruecksetzen();
    long userId =
        insert(
            "INSERT INTO app_user (email, display_name) VALUES ('a@example.com', 'A')"
                + " RETURNING id");
    projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', " + userId + ") RETURNING id");
    long boardId =
        insert(
            "INSERT INTO board (project_id, name) VALUES ("
                + projectId
                + ", 'GitLab') RETURNING id");
    List<String> spalten = List.of("Backlog", "Ready", "In progress", "In review", "Done");
    for (int i = 0; i < spalten.size(); i++) {
      jdbc.update(
          "INSERT INTO board_column (board_id, name, position) VALUES (?, ?, ?)",
          boardId,
          spalten.get(i),
          i);
    }
    verbindung = GitLabNachgestellt.verbindung(projectId, boardId, ANGEBUNDEN);
    verbindungen.speichere(verbindung);
  }

  @Test
  @DisplayName("drei Wechsel in einem Abgleich ergeben drei Aufenthalte mit GitLab-Zeiten")
  void dreiWechselInEinemAbgleichErgebenDreiAufenthalteMitGitLabZeiten() {
    // Given: vor der Anbindung angelegt, seitdem Backlog → Ready → In progress → In review; das
    // Label kit:nightrun stammt von vor der Anbindung.
    stelleNach(
        7,
        issue(7, PAKET, "", List.of("In review", "kit:nightrun"), false, VORHER, T3),
        List.of(
            labelEreignis(400, "kit:nightrun", "add", VORHER, "anna"),
            labelEreignis(401, "Ready", "add", T1, "anna"),
            labelEreignis(402, "Ready", "remove", T2, "kit-bot"),
            labelEreignis(403, "In progress", "add", T2, "kit-bot"),
            labelEreignis(404, "In progress", "remove", T3, "kit-bot"),
            labelEreignis(405, "In review", "add", T3, "kit-bot")),
        List.of(),
        List.of());

    // When
    abgleich.karteAbgleichen(verbindung, 7);

    // Then: Eintritt zur Anbindung in Backlog, danach drei Wechsel mit den Zeiten aus GitLab.
    long cardId = karte(7);
    assertThat(aufenthalte(cardId))
        .containsExactly(
            aufenthalt("BACKLOG", ANGEBUNDEN, T1),
            aufenthalt("READY", T1, T2),
            aufenthalt("IN_PROGRESS", T2, T3),
            aufenthalt("IN_REVIEW", T3, null));
    assertThat(status(cardId)).isEqualTo("IN_REVIEW");
    assertThat(labels(cardId)).containsExactly("kit:nightrun");
    assertThat(
            jdbc.queryForList(
                "SELECT origin FROM card_activity WHERE card_id = ? AND type = 'MOVED'"
                    + " ORDER BY created_at",
                String.class,
                cardId))
        .containsExactly("SESSION", "TOKEN", "TOKEN");

    // When: ein zweiter Abgleich ohne neue Wechsel
    abgleich.karteAbgleichen(verbindung, 7);

    // Then: nichts zählt doppelt.
    assertThat(aufenthalte(cardId)).hasSize(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM fremdboard_ereignis WHERE project_id = ?",
                Integer.class,
                projectId))
        .isEqualTo(5);
  }

  @Test
  @DisplayName("Szenario: Meldung und GitLab weichen ab")
  void szenarioMeldungUndGitLabWeichenAb() {
    // Given: Der Durchlauf hat „In Review“ gemeldet (als Kommentar des Kit-Kontos), in GitLab steht
    // die Karte aber in „In progress“.
    stelleNach(
        7,
        issue(7, PAKET, "", List.of("In progress"), false, VORHER, T3),
        List.of(
            labelEreignis(401, "Ready", "add", T1, "anna"),
            labelEreignis(402, "Ready", "remove", T2, "kit-bot"),
            labelEreignis(403, "In progress", "add", T2, "kit-bot")),
        List.of(),
        List.of(notiz(701, "Paket nach In review gezogen", "kit-bot", T3, T3)));

    // When: kanban-kit liest den Stand aus GitLab
    abgleich.karteAbgleichen(verbindung, 7);

    // Then: Das Board zeigt „In Progress“, der Fluss zählt keinen Wechsel nach „In Review“.
    long cardId = karte(7);
    assertThat(status(cardId)).isEqualTo("IN_PROGRESS");
    assertThat(spalte(cardId)).isEqualTo("In progress");
    assertThat(aufenthalte(cardId))
        .extracting(a -> a.get("status"))
        .containsExactly("BACKLOG", "READY", "IN_PROGRESS")
        .doesNotContain("IN_REVIEW");
    assertThat(
            jdbc.queryForObject(
                "SELECT origin FROM comment WHERE card_id = ?", String.class, cardId))
        .isEqualTo("TOKEN");
  }

  @Test
  void spaeterAngelegteKarteTrittMitIhremAnlagezeitpunktEinUndSchliessenIstDone() {
    // Given: nach der Anbindung angelegt, gleich auf Ready gesetzt, später geschlossen
    stelleNach(
        8,
        issue(8, PAKET, "", List.of("Ready"), true, T1, T3),
        List.of(labelEreignis(410, "Ready", "add", T1, "anna")),
        List.of(statusEreignis(510, "closed", T3, "anna")),
        List.of());

    // When
    abgleich.karteAbgleichen(verbindung, 8);

    // Then
    long cardId = karte(8);
    assertThat(aufenthalte(cardId))
        .containsExactly(
            aufenthalt("BACKLOG", T1, T1),
            aufenthalt("READY", T1, T3),
            aufenthalt("DONE", T3, null));
    assertThat(status(cardId)).isEqualTo("DONE");
  }

  @Test
  void herkunftNotizenUndGeloeschteMeldung() {
    // Given: Plan 3 und Paket 7, das aus ihm stammt; am Paket eine Meldung des Kits
    stelleNach(
        3,
        issue(3, "[Plan] Export", "", List.of(), false, VORHER, VORHER),
        List.of(),
        List.of(),
        List.of());
    stelleNach(
        7,
        issue(7, PAKET, "Text\nPlan: #3", List.of(), false, VORHER, VORHER),
        List.of(),
        List.of(),
        List.of(notiz(701, "## Kette angehalten", "kit-bot", T1, T1)));
    abgleich.karteAbgleichen(verbindung, 3);

    // When
    abgleich.karteAbgleichen(verbindung, 7);

    // Then
    long paket = karte(7);
    assertThat(
            jdbc.queryForObject(
                "SELECT derived_from_card_id FROM card WHERE id = ?", Long.class, paket))
        .isEqualTo(karte(3));
    assertThat(jdbc.queryForObject("SELECT extern_url FROM card WHERE id = ?", String.class, paket))
        .isEqualTo(GITLAB.basis() + "/gruppe/projekt/-/issues/7");

    // When: Die Meldung wird in GitLab gelöscht.
    stelleNach(
        7,
        issue(7, PAKET, "Text\nPlan: #3", List.of(), false, VORHER, T2),
        List.of(),
        List.of(),
        List.of());
    abgleich.karteAbgleichen(verbindung, 7);

    // Then: Sie bleibt stehen und ist als von Hand geändert gekennzeichnet (A19).
    Map<String, Object> meldung =
        jdbc.queryForMap(
            "SELECT body, von_hand_geaendert_am FROM comment WHERE card_id = ?", paket);
    assertThat(meldung.get("body")).isEqualTo("## Kette angehalten");
    assertThat(meldung.get("von_hand_geaendert_am")).isNotNull();
  }

  @Test
  void eineInGitLabFehlendeKarteBleibtUnberuehrt() {
    // When: GitLab kennt Karte 9 nicht (404)
    abgleich.karteAbgleichen(verbindung, 9);

    // Then
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM card WHERE project_id = ?", Integer.class, projectId))
        .isZero();
  }

  private long karte(int nummer) {
    return jdbc.queryForObject(
        "SELECT id FROM card WHERE project_id = ? AND number = ?", Long.class, projectId, nummer);
  }

  private String status(long cardId) {
    return jdbc.queryForObject("SELECT status FROM card WHERE id = ?", String.class, cardId);
  }

  private String spalte(long cardId) {
    return jdbc.queryForObject(
        "SELECT c.name FROM card k JOIN board_column c ON c.id = k.column_id WHERE k.id = ?",
        String.class,
        cardId);
  }

  private List<String> labels(long cardId) {
    return jdbc.queryForList(
        "SELECT l.name FROM card_label cl JOIN label l ON l.id = cl.label_id WHERE cl.card_id = ?",
        String.class,
        cardId);
  }

  private List<Map<String, Object>> aufenthalte(long cardId) {
    return jdbc
        .queryForList(
            "SELECT status, entered_at, left_at FROM card_flow_stay WHERE card_id = ?"
                + " ORDER BY entered_at, id",
            cardId)
        .stream()
        .map(
            z ->
                aufenthalt(
                    (String) z.get("status"),
                    instant(z.get("entered_at")),
                    instant(z.get("left_at"))))
        .toList();
  }

  private static Map<String, Object> aufenthalt(
      String status, Instant betreten, @Nullable Instant verlassen) {
    return verlassen == null
        ? Map.of("status", status, "betreten", betreten)
        : Map.of("status", status, "betreten", betreten, "verlassen", verlassen);
  }

  private static @Nullable Instant instant(@Nullable Object wert) {
    if (wert == null) {
      return null;
    }
    return wert instanceof java.sql.Timestamp ts
        ? ts.toInstant()
        : ((OffsetDateTime) wert).toInstant();
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    return id == null ? 0L : id;
  }
}
