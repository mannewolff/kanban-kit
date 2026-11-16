package org.mwolff.manban.fremdboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.GITLAB;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.alleKarten;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.geaenderteKarten;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.issue;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.labelEreignis;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.liste;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.notiz;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.statusEreignis;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.stelleNach;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.fremdboard.application.AbgleichJob;
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
 * Abgleich einer Karte mit GitLab (Issue #2167, Plan #2152 A4, A6, A11, A12, A19) und regelmäßiger
 * Abgleich samt gelöschter Karten und Ausfall (Issue #2168, A7, A8, A9) gegen eine echte Datenbank
 * und ein nachgestelltes GitLab: Zeitpunkte aus GitLab, jeder Wechsel genau einmal, es gilt der
 * Stand dort. Dieselbe Konfiguration wie {@code FremdBoardAnstossIT}, damit beide einen Kontext
 * teilen.
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
  @Autowired private AbgleichJob job;
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

  @Test
  @DisplayName("Szenario: Jemand ändert eine Karte von Hand in GitLab")
  void szenarioJemandAendertEineKarteVonHandInGitLab() {
    // Given: ein Projekt mit Board in GitLab, Karte 7 steht dort und hier in „In progress“.
    List<String> wechsel =
        List.of(
            labelEreignis(401, "Ready", "add", T1, "anna"),
            labelEreignis(402, "Ready", "remove", T2, "kit-bot"),
            labelEreignis(403, "In progress", "add", T2, "kit-bot"));
    stelleNach(
        7,
        issue(7, PAKET, "", List.of("In progress"), false, VORHER, T2),
        wechsel,
        List.of(),
        List.of());
    abgleich.karteAbgleichen(verbindung, 7);
    long cardId = karte(7);
    assertThat(status(cardId)).isEqualTo("IN_PROGRESS");

    // When: Ein Teammitglied setzt die Karte in GitLab auf Done (schließt sie), und der
    // regelmäßige Abgleich liest die seit dem letzten Mal geänderten Karten.
    String geschlossen = issue(7, PAKET, "", List.of("In progress"), true, VORHER, T3);
    stelleNach(
        7, geschlossen, wechsel, List.of(statusEreignis(510, "closed", T3, "anna")), List.of());
    liste(geaenderteKarten(T2), "[" + geschlossen + "]");
    abgleich.inkrementellAbgleichen(verbindung, T2);

    // Then: Die Karte steht in „Done“, der Fluss zählt sie einmal fertig, zum Zeitpunkt aus GitLab.
    assertThat(status(cardId)).isEqualTo("DONE");
    assertThat(spalte(cardId)).isEqualTo("Done");
    assertThat(fertig(cardId)).containsExactly(aufenthalt("DONE", T3, null));
    assertThat(
            jdbc.queryForObject(
                    "SELECT moved_to_done_at FROM card WHERE id = ?", OffsetDateTime.class, cardId)
                .toInstant())
        .isEqualTo(T3);

    // When: Der nächste Abgleich sieht dieselbe Änderung noch einmal (Überlappung).
    abgleich.inkrementellAbgleichen(verbindung, T2);

    // Then: Sie zählt nicht doppelt.
    assertThat(fertig(cardId)).containsExactly(aufenthalt("DONE", T3, null));
  }

  @Test
  void gitLabAntwortetNicht_fehlerSteht_kartenBleiben_keineGeloescht() {
    // Given: Karte 7 ist gespiegelt, der letzte Abgleich gelang vor zehn Minuten.
    stelleNach(
        7,
        issue(7, PAKET, "", List.of("Ready"), false, VORHER, T1),
        List.of(labelEreignis(401, "Ready", "add", T1, "anna")),
        List.of(),
        List.of());
    abgleich.karteAbgleichen(verbindung, 7);
    long cardId = karte(7);
    Instant zuletzt = Instant.now().minus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.MICROS);
    jdbc.update(
        "UPDATE fremdboard_verbindung SET letzter_abgleich_am = ? WHERE project_id = ?",
        zuletzt.atOffset(ZoneOffset.UTC),
        projectId);
    GITLAB.antworte(alleKarten(), 503, "{\"message\":\"503 Service Unavailable\"}", null);
    List<Map<String, Object>> vorher = aufenthalte(cardId);

    // When
    job.run();

    // Then
    Verbindung danach = verbindungen.finde(projectId).orElseThrow();
    assertThat(danach.letzterFehler()).contains("503");
    assertThat(danach.letzterAbgleichAm()).isEqualTo(zuletzt);
    assertThat(danach.letzterVollabgleichAm()).isNull();
    assertThat(status(cardId)).isEqualTo("READY");
    assertThat(aufenthalte(cardId)).isEqualTo(vorher);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM card WHERE project_id = ? AND deleted_at IS NOT NULL",
                Integer.class,
                projectId))
        .isZero();
  }

  @Test
  void eineKarteFehltInDerListe_istGeloescht_flussUndVerlaufBleiben() {
    // Given: Karten 7 und 8 sind gespiegelt; in GitLab gibt es nur noch 7.
    String sieben = issue(7, PAKET, "", List.of("Ready"), false, VORHER, T1);
    stelleNach(
        7, sieben, List.of(labelEreignis(401, "Ready", "add", T1, "anna")), List.of(), List.of());
    stelleNach(
        8,
        issue(8, "Import", "", List.of("In progress"), false, VORHER, T2),
        List.of(labelEreignis(411, "In progress", "add", T2, "anna")),
        List.of(),
        List.of());
    abgleich.karteAbgleichen(verbindung, 7);
    abgleich.karteAbgleichen(verbindung, 8);
    long acht = karte(8);
    List<Map<String, Object>> fluss = aufenthalte(acht);
    int verlauf = verlauf(acht);
    liste(alleKarten(), "[" + sieben + "]");
    liste(geaenderteKarten(ANGEBUNDEN), "[]");

    // When: der erste regelmäßige Abgleich, ein Vollabgleich
    job.run();

    // Then
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at FROM card WHERE id = ?", OffsetDateTime.class, acht))
        .isNotNull();
    // Die Aufenthalte bleiben; der offene endet wie bei jeder gelöschten Karte.
    assertThat(aufenthalte(acht))
        .extracting(a -> a.get("status"), a -> a.get("betreten"))
        .containsExactlyElementsOf(
            fluss.stream().map(a -> Tuple.tuple(a.get("status"), a.get("betreten"))).toList());
    assertThat(aufenthalte(acht).getFirst()).isEqualTo(fluss.getFirst());
    assertThat(aufenthalte(acht).getLast()).containsKey("verlassen");
    assertThat(verlauf(acht)).isEqualTo(verlauf);
    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_at FROM card WHERE id = ?", OffsetDateTime.class, karte(7)))
        .isNull();
    Verbindung danach = verbindungen.finde(projectId).orElseThrow();
    assertThat(danach.letzterFehler()).isNull();
    assertThat(danach.letzterAbgleichAm()).isNotNull();
    assertThat(danach.letzterVollabgleichAm()).isEqualTo(danach.letzterAbgleichAm());
  }

  private List<Map<String, Object>> fertig(long cardId) {
    return aufenthalte(cardId).stream().filter(a -> "DONE".equals(a.get("status"))).toList();
  }

  private int verlauf(long cardId) {
    Integer anzahl =
        jdbc.queryForObject(
            "SELECT count(*) FROM card_column_transition WHERE card_id = ?", Integer.class, cardId);
    return anzahl == null ? 0 : anzahl;
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
