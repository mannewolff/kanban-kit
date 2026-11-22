package org.mwolff.manban.fremdboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.GITLAB;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.alleKarten;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.geaenderteKarten;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.issue;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.liste;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.notiz;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.stelleNach;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Der Angriff aus Issue #2200: Ein Mitglied des kanban-kit-Projekts, das in GitLab vertrauliche
 * Inhalte nicht lesen darf — auch eines, das erst später aufgenommen wurde —, liest sie über den
 * Spiegel, weil dessen Zugang in GitLab mehr sieht. Issue #2195 verhinderte nur weitere Importe:
 * Eine schon gespiegelte, inzwischen vertrauliche Karte lag mit ihrer Beschreibung im Papierkorb
 * ({@code GET /api/boards/{boardId}/trash}), und eine schon übernommene, inzwischen interne Notiz
 * blieb als Kommentar abrufbar.
 *
 * <p>Nachgestellt ist der Altbestand nach dem Upgrade: Karte 9 liegt schon im Papierkorb, Notiz 702
 * ist noch gespiegelt, und die Migration V77 hat den letzten Vollabgleich auf den Anfang der Zeit
 * gesetzt. Dieselbe Konfiguration wie {@code AbgleichServiceIT}, damit beide einen Kontext teilen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(GitLabNachgestellt.class)
@TestPropertySource(properties = "manban.fremdboard.secret-key=" + GitLabTestServer.SCHLUESSEL)
class VertraulichImSpiegelIT extends AbstractIntegrationTest {

  private static final Instant VORHER = Instant.parse("2026-10-01T08:00:00Z");
  private static final Instant ANGEBUNDEN = Instant.parse("2026-11-01T08:00:00Z");
  private static final Instant T1 = Instant.parse("2026-11-02T09:00:00Z");
  private static final Instant T2 = Instant.parse("2026-11-03T09:00:00Z");
  private static final String GEHEIMER_TITEL = "Gehaltsliste Q4";
  private static final String GEHEIME_BESCHREIBUNG = "Anna 7200, Bernd 6900";
  private static final String GEHEIME_NOTIZ = "Kuendigung Bernd zum Quartalsende";
  private static final String MITGLIED = "spaeter-vertraulich@example.com";

  @Autowired private AbgleichService abgleich;
  @Autowired private AbgleichJob job;
  @Autowired private VerbindungRepository verbindungen;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;

  private long projectId;
  private long boardId;
  private Verbindung verbindung;

  @BeforeEach
  void aufbau() {
    GITLAB.zuruecksetzen();
    long ownerId =
        insert(
            "INSERT INTO app_user (email, display_name) VALUES ('owner-vertraulich@example.com',"
                + " 'O') RETURNING id");
    projectId =
        insert(
            "INSERT INTO project (name, owner_user_id) VALUES ('V', " + ownerId + ") RETURNING id");
    boardId =
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
  void gespiegelteVertraulicheInhalteSindNachDemVollabgleichAufKeinemLesewegMehrAbrufbar()
      throws Exception {
    // Given: Karte 7 mit zwei Notizen und Karte 9 sind gespiegelt, solange alles öffentlich war.
    String sieben = issue(7, "Export als CSV", "", List.of(), false, VORHER, T1);
    stelleNach(
        7,
        sieben,
        List.of(),
        List.of(),
        List.of(
            notiz(701, "Freigegeben", "anna", T1, T1), notiz(702, GEHEIME_NOTIZ, "anna", T1, T1)));
    stelleNach(
        9,
        issue(9, GEHEIMER_TITEL, GEHEIME_BESCHREIBUNG, List.of(), false, VORHER, T1),
        List.of(),
        List.of(),
        List.of());
    abgleich.karteAbgleichen(verbindung, 7);
    abgleich.karteAbgleichen(verbindung, 9);
    long karteSieben = karte(7);
    long karteNeun = karte(9);

    // Given: In GitLab ist Karte 9 jetzt vertraulich und Notiz 702 intern. Der Vollabgleich vor
    // der Behebung hat Karte 9 schon in den Papierkorb gelegt (Issue #2195); Notiz 702 blieb.
    String neunVertraulich =
        vertraulich(issue(9, GEHEIMER_TITEL, "", List.of(), false, VORHER, T2));
    GITLAB.antworte(GitLabNachgestellt.karte(9), 200, neunVertraulich, null);
    liste(
        GitLabNachgestellt.notizen(7),
        "["
            + notiz(701, "Freigegeben", "anna", T1, T1)
            + ","
            + intern(notiz(702, GEHEIME_NOTIZ, "anna", T1, T1))
            + "]");
    jdbc.update("UPDATE card SET deleted_at = now() WHERE id = ?", karteNeun);
    liste(alleKarten(), "[" + sieben + "," + neunVertraulich + "]");

    // Given: Stand nach der Migration V77 — der nächste Abgleich ist ein voller und liest jede
    // Karte von Anfang an, auch Karte 7, die sich in GitLab seit Langem nicht geändert hat.
    jdbc.update(
        "UPDATE fremdboard_verbindung SET letzter_abgleich_am = ?, letzter_vollabgleich_am = ?"
            + " WHERE project_id = ?",
        ANGEBUNDEN.atOffset(ZoneOffset.UTC),
        Instant.EPOCH.atOffset(ZoneOffset.UTC),
        projectId);
    liste(
        geaenderteKarten(Instant.EPOCH.minus(Duration.ofMinutes(1))),
        "[" + sieben + "," + neunVertraulich + "]");

    // Given: ein Mitglied, das erst jetzt ins Projekt kommt
    Cookie mitglied = anmeldung.anmelden(MITGLIED, PlatformRole.USER);
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role)"
            + " SELECT ?, id, 'MEMBER' FROM app_user WHERE email = ?",
        projectId,
        MITGLIED);

    // When: der regelmäßige Abgleich
    job.run();

    // Then: Der Abgleich ist gelungen.
    assertThat(verbindungen.finde(projectId).orElseThrow().letzterFehler()).isNull();

    // Then: Der Papierkorb zeigt Karte 9 weder mit Titel noch mit Beschreibung.
    String papierkorb = lies(mitglied, "/api/boards/" + boardId + "/trash", 200);
    assertThat(papierkorb).doesNotContain(GEHEIMER_TITEL).doesNotContain(GEHEIME_BESCHREIBUNG);

    // Then: Karte 9 und ihre Kommentare sind auch einzeln nicht abrufbar.
    lies(mitglied, "/api/cards/" + karteNeun, 404);
    lies(mitglied, "/api/cards/" + karteNeun + "/comments", 404);

    // Then: Die interne Notiz ist kein Kommentar mehr, die öffentliche bleibt.
    String kommentare = lies(mitglied, "/api/cards/" + karteSieben + "/comments", 200);
    assertThat(kommentare).contains("Freigegeben").doesNotContain(GEHEIME_NOTIZ);

    // Then: Auch in der Datenbank steht nichts davon mehr.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM card WHERE project_id = ? AND (title = ? OR description = ?)",
                Integer.class,
                projectId,
                GEHEIMER_TITEL,
                GEHEIME_BESCHREIBUNG))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM comment WHERE body = ?", Integer.class, GEHEIME_NOTIZ))
        .isZero();
  }

  @Test
  void eineVertraulichGewordeneKarteAufDemBoardWirdBeimVollabgleichEntfernt() throws Exception {
    // Given: Karte 9 ist gespiegelt und steht auf dem Board.
    stelleNach(
        9,
        issue(9, GEHEIMER_TITEL, GEHEIME_BESCHREIBUNG, List.of(), false, VORHER, T1),
        List.of(),
        List.of(),
        List.of(notiz(901, GEHEIME_NOTIZ, "anna", T1, T1)));
    abgleich.karteAbgleichen(verbindung, 9);
    long karteNeun = karte(9);
    Cookie mitglied = anmeldung.anmelden(MITGLIED, PlatformRole.USER);
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role)"
            + " SELECT ?, id, 'MEMBER' FROM app_user WHERE email = ?",
        projectId,
        MITGLIED);

    // When: In GitLab wird sie vertraulich, danach läuft ein Vollabgleich.
    String neunVertraulich =
        vertraulich(issue(9, GEHEIMER_TITEL, "", List.of(), false, VORHER, T2));
    liste(alleKarten(), "[" + neunVertraulich + "]");
    liste(geaenderteKarten(T1), "[" + neunVertraulich + "]");
    abgleich.vollAbgleichen(verbindung, T1);

    // Then: Sie liegt nicht im Papierkorb, sondern ist samt Kommentar entfernt.
    String papierkorb = lies(mitglied, "/api/boards/" + boardId + "/trash", 200);
    assertThat(papierkorb).doesNotContain(GEHEIMER_TITEL);
    lies(mitglied, "/api/cards/" + karteNeun + "/comments", 404);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM comment WHERE body = ?", Integer.class, GEHEIME_NOTIZ))
        .isZero();
  }

  private String lies(Cookie cookie, String pfad, int erwartet) throws Exception {
    return mvc.perform(get(pfad).cookie(cookie))
        .andExpect(status().is(erwartet))
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  private static String vertraulich(String issue) {
    return issue.replaceFirst("\\{", "{\"confidential\":true,");
  }

  private static String intern(String notiz) {
    return notiz.replaceFirst("\\{", "{\"internal\":true,");
  }

  private long karte(int nummer) {
    return jdbc.queryForObject(
        "SELECT id FROM card WHERE project_id = ? AND number = ?", Long.class, projectId, nummer);
  }

  private long insert(String sql) {
    Long id = jdbc.queryForObject(sql, Long.class);
    return id == null ? 0L : id;
  }
}
