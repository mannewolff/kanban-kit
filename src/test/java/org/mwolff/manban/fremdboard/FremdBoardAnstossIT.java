package org.mwolff.manban.fremdboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.GITLAB;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.issue;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.labelEreignis;
import static org.mwolff.manban.fremdboard.GitLabNachgestellt.stelleNach;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.fremdboard.application.VerbindungRepository;
import org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code POST /api/kanban/fremdboard/anstoss} (Issue #2167, Plan #2152 A6, A16): Das Kit stößt nach
 * einem eigenen Schritt an, kanban-kit liest die Karte sofort aus GitLab — nachgestellt — und zählt
 * jeden Wechsel genau einmal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(GitLabNachgestellt.class)
@TestPropertySource(properties = "manban.fremdboard.secret-key=" + GitLabTestServer.SCHLUESSEL)
class FremdBoardAnstossIT extends AbstractIntegrationTest {

  private static final String ANSTOSS = "/api/kanban/fremdboard/anstoss";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String OWNER = "owner-anstoss@example.com";
  private static final Instant VORHER = Instant.parse("2026-10-01T08:00:00Z");
  private static final Instant ANGEBUNDEN = Instant.parse("2026-11-01T08:00:00Z");
  private static final Instant READY = Instant.parse("2026-11-02T09:00:00Z");
  private static final Instant BEGONNEN = Instant.parse("2026-11-02T09:05:00Z");
  private static final Instant REVIEW = Instant.parse("2026-11-02T09:40:00Z");

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private VerbindungRepository verbindungen;

  private Cookie owner;
  private long projekt;
  private long board;
  private String token;

  @BeforeEach
  void aufbau() throws Exception {
    GITLAB.zuruecksetzen();
    owner = anmeldung.anmelden(OWNER, PlatformRole.ADMIN);
    projekt =
        lies(mvc.perform(
                    post("/api/projects")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fremd\",\"ownerEmail\":\"" + OWNER + "\"}"))
                .andExpect(status().isCreated()))
            .get("id")
            .asLong();
    board = jdbc.queryForObject("SELECT id FROM board WHERE project_id = ?", Long.class, projekt);
    token =
        lies(mvc.perform(
                    post("/api/access-tokens")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"name\":\"Kit\",\"projectId\":%d,\"boardId\":%d}"
                                .formatted(projekt, board)))
                .andExpect(status().isCreated()))
            .get("plaintext")
            .asText();
  }

  private void anbinden() {
    verbindungen.speichere(GitLabNachgestellt.verbindung(projekt, board, ANGEBUNDEN));
  }

  private ResultActions anstossen(String rumpf) throws Exception {
    return mvc.perform(
        post(ANSTOSS)
            .header(TOKEN_HEADER, token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(rumpf));
  }

  @Test
  @DisplayName("Szenario: Ein automatischer Durchlauf zieht eine Karte weiter")
  void szenarioEinAutomatischerDurchlaufZiehtEineKarteWeiter() throws Exception {
    // Given: Projekt mit Board in GitLab; der Durchlauf hat Paket 7 in GitLab nach „In Review“
    // gezogen und seinen Lauf mit Kosten an der Nummer gemeldet.
    anbinden();
    stelleNach(
        7,
        issue(7, "Export als CSV", "", List.of("In review"), false, VORHER, REVIEW),
        List.of(
            labelEreignis(401, "Ready", "add", READY, "anna"),
            labelEreignis(402, "Ready", "remove", BEGONNEN, "kit-bot"),
            labelEreignis(403, "In progress", "add", BEGONNEN, "kit-bot"),
            labelEreignis(404, "In progress", "remove", REVIEW, "kit-bot"),
            labelEreignis(405, "In review", "add", REVIEW, "kit-bot")),
        List.of(),
        List.of());
    mvc.perform(
            post("/api/kanban/night-runs")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"startedAt":"2026-11-02T09:00:00Z","mode":"IMPLEMENTATION",
                     "durationMs":2400000,"processedCount":1,"skippedCount":0,
                     "unparsedCount":0,"complete":true,
                     "usage":{"costUsd":1.25},
                     "items":[{"cardNumber":7,"title":"Export als CSV","state":"GREEN",
                               "durationMs":2100000,"usage":{"costUsd":1.25}}]}"""))
        .andExpect(status().isOk());

    // When: Das Kit stößt nach dem Zug an.
    anstossen("{\"nummer\":7}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.abgeglichen").value(true));

    // Then: Die Karte steht beim nächsten Laden in „In Review“, ohne auf den Abgleich zu warten,
    // und der Fluss zählt den Wechsel einmal.
    long cardId =
        jdbc.queryForObject(
            "SELECT id FROM card WHERE project_id = ? AND number = 7", Long.class, projekt);
    assertThat(jdbc.queryForObject("SELECT status FROM card WHERE id = ?", String.class, cardId))
        .isEqualTo("IN_REVIEW");
    assertThat(wechselNachReview(cardId)).isEqualTo(1);
    int abrufe = GITLAB.abrufe().size();

    // When: Ein zweiter Anstoß derselben Karte gleich danach liest nicht und zählt nichts.
    anstossen("{\"nummer\":7}")
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.abgeglichen").value(false));

    // Then
    assertThat(GITLAB.abrufe()).hasSize(abrufe);
    assertThat(wechselNachReview(cardId)).isEqualTo(1);
    mvc.perform(
            get("/api/projects/" + projekt + "/night-runs/items")
                .cookie(owner)
                .param("cardNumber", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void ohneFremdesBoardAntwortet409() throws Exception {
    anstossen("{\"nummer\":7}").andExpect(status().isConflict());
    assertThat(GITLAB.abrufe()).isEmpty();
  }

  @Test
  void ohneGueltigeNummerAntwortet400() throws Exception {
    anbinden();

    anstossen("{\"nummer\":0}").andExpect(status().isBadRequest());
    anstossen("{}").andExpect(status().isBadRequest());
  }

  @Test
  void ungebundenesTokenAntwortet409() throws Exception {
    // Given
    anbinden();
    String ungebunden =
        lies(mvc.perform(
                    post("/api/access-tokens")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ungebunden\"}"))
                .andExpect(status().isCreated()))
            .get("plaintext")
            .asText();

    // When / Then
    mvc.perform(
            post(ANSTOSS)
                .header(TOKEN_HEADER, ungebunden)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nummer\":7}"))
        .andExpect(status().isConflict());
  }

  @Test
  void nichtErreichbaresGitLabAntwortet502() throws Exception {
    // Given: GitLab antwortet auf die Karte mit einem Serverfehler.
    anbinden();
    // Eigene Nummer: Die Sperrzeit je Karte liegt im Arbeitsspeicher des geteilten Kontexts.
    GITLAB.antworte(GitLabNachgestellt.karte(17), 500, "{}", null);

    // When / Then
    anstossen("{\"nummer\":17}").andExpect(status().isBadGateway());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM card WHERE project_id = ?", Integer.class, projekt))
        .isZero();
  }

  @Test
  void sessionOhneTokenWirdAbgewiesen() throws Exception {
    anbinden();

    mvc.perform(
            post(ANSTOSS)
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nummer\":7}"))
        .andExpect(status().isForbidden());
  }

  private int wechselNachReview(long cardId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM card_flow_stay WHERE card_id = ? AND status = 'IN_REVIEW'",
        Integer.class,
        cardId);
  }

  private JsonNode lies(ResultActions antwort) throws Exception {
    return json.readTree(
        antwort.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
  }
}
