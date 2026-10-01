package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End des Statuswechsels (Issue #1300, Plan #1294 E9/E10): {@code PUT
 * /api/cards/{cardId}/status} gegen die echte Datenbank, und {@code canSetStatus} je Board der
 * Karte — auch für eine über {@code #N} gefundene Karte auf einem fremden Board.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardStatusIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  private Cookie session(String email, PlatformRole role) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, role));
    }
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().id();
  }

  private JsonNode read(String body) throws Exception {
    return json.readTree(body);
  }

  private long project(Cookie admin, String ownerEmail, String name) throws Exception {
    return read(mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .get("id")
        .asLong();
  }

  private JsonNode board(Cookie owner, long projectId) throws Exception {
    return read(
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(owner)
                    .contentType("application/json")
                    .content("{\"name\":\"B\"}"))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode card(Cookie owner, long boardId, long columnId, String title) throws Exception {
    return read(
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(owner)
                    .contentType("application/json")
                    .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode getCard(Cookie who, long cardId) throws Exception {
    return read(
        mvc.perform(get("/api/cards/" + cardId).cookie(who))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void setztDenStatus_ohneDieKarteZuVerschieben() throws Exception {
    Cookie admin = session("status-admin@example.com", PlatformRole.ADMIN);
    Cookie owner = session("status-owner@example.com", PlatformRole.USER);
    long projectId = project(admin, "status-owner@example.com", "Status");
    JsonNode board = board(owner, projectId);
    long boardId = board.get("id").asLong();
    long backlog = board.get("columns").get(0).get("id").asLong();
    card(owner, boardId, backlog, "Vorne");
    JsonNode paket = card(owner, boardId, backlog, "Paket");
    long cardId = paket.get("id").asLong();

    mvc.perform(
            put("/api/cards/" + cardId + "/status")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"status\":\"IN_REVIEW\"}"))
        .andExpect(status().isNoContent());

    JsonNode nachher = getCard(owner, cardId);
    assertThat(nachher.get("status").asText()).isEqualTo("IN_REVIEW");
    assertThat(nachher.get("canSetStatus").asBoolean()).isTrue();
    assertThat(nachher.get("columnId").asLong()).isEqualTo(backlog);
    assertThat(nachher.get("positionInColumn").asInt())
        .isEqualTo(paket.get("positionInColumn").asInt());
    assertThat(
            jdbc.queryForObject(
                "SELECT detail FROM card_activity WHERE card_id = ? AND type = 'STATUS_CHANGED'",
                String.class,
                cardId))
        .isEqualTo("Status auf In review");

    // Auf Done: der Done-Zeitstempel entsteht aus dem Status, nicht aus der Spalte (E7).
    mvc.perform(
            put("/api/cards/" + cardId + "/status")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isNoContent());
    assertThat(getCard(owner, cardId).get("movedToDoneAt").isNull()).isFalse();

    // Die Board-Liste trägt Status und Recht ebenso.
    JsonNode liste =
        read(
            mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode k : liste) {
      if (k.get("id").asLong() == cardId) {
        assertThat(k.get("status").asText()).isEqualTo("DONE");
        assertThat(k.get("canSetStatus").asBoolean()).isTrue();
      }
    }
  }

  @Test
  void unbekannterWertUndDokumentart_ergeben400() throws Exception {
    Cookie admin = session("status-400-admin@example.com", PlatformRole.ADMIN);
    Cookie owner = session("status-400-owner@example.com", PlatformRole.USER);
    long projectId = project(admin, "status-400-owner@example.com", "Status400");
    JsonNode board = board(owner, projectId);
    long boardId = board.get("id").asLong();
    long backlog = board.get("columns").get(0).get("id").asLong();
    long paket = card(owner, boardId, backlog, "Paket").get("id").asLong();
    long plan = card(owner, boardId, backlog, "[Plan] Ein Plan").get("id").asLong();

    mvc.perform(
            put("/api/cards/" + paket + "/status")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"status\":\"FERTIG\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/cards/" + paket + "/status")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"status\":\"\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put("/api/cards/" + plan + "/status")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"status\":\"READY\"}"))
        .andExpect(status().isBadRequest());

    assertThat(getCard(owner, paket).get("status").asText()).isEqualTo("BACKLOG");
    JsonNode planSicht = getCard(owner, plan);
    assertThat(planSicht.get("status").isNull()).isTrue();
    assertThat(planSicht.get("canSetStatus").asBoolean()).isFalse();
  }

  @Test
  void fremdesBoardOhneCardMove_canSetStatusFalsch_und403() throws Exception {
    Cookie admin = session("status-fremd-admin@example.com", PlatformRole.ADMIN);
    Cookie alice = session("status-fremd-alice@example.com", PlatformRole.USER);
    Cookie bob = session("status-fremd-bob@example.com", PlatformRole.USER);
    long eigenes = project(admin, "status-fremd-alice@example.com", "Eigen");
    long fremdes = project(admin, "status-fremd-bob@example.com", "Fremd");
    JsonNode eigenesBoard = board(alice, eigenes);
    JsonNode fremdesBoard = board(bob, fremdes);
    long eigeneKarte =
        card(
                alice,
                eigenesBoard.get("id").asLong(),
                eigenesBoard.get("columns").get(0).get("id").asLong(),
                "Eigen")
            .get("id")
            .asLong();
    JsonNode fremdeKarte =
        card(
            bob,
            fremdesBoard.get("id").asLong(),
            fremdesBoard.get("columns").get(0).get("id").asLong(),
            "Fremd");
    long fremdeId = fremdeKarte.get("id").asLong();
    int fremdeNummer = fremdeKarte.get("number").asInt();
    // Alice darf im fremden Projekt nur lesen: VIEWER hat kein CARD_MOVE.
    memberships.save(
        new ProjectMembership(
            null,
            fremdes,
            userId("status-fremd-alice@example.com"),
            ProjectRole.VIEWER,
            Instant.now()));

    assertThat(getCard(alice, eigeneKarte).get("canSetStatus").asBoolean()).isTrue();

    // Über #N gefunden: die Suche und der Nummern-Lookup tragen das Recht des fremden Boards.
    JsonNode treffer =
        read(
            mvc.perform(get("/api/cards/search").param("number", "" + fremdeNummer).cookie(alice))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode fremdImTreffer = null;
    for (JsonNode t : treffer) {
      if (t.get("card").get("id").asLong() == fremdeId) {
        fremdImTreffer = t.get("card");
      }
    }
    assertThat(fremdImTreffer).isNotNull();
    assertThat(fremdImTreffer.get("status").asText()).isEqualTo("BACKLOG");
    assertThat(fremdImTreffer.get("canSetStatus").asBoolean()).isFalse();

    JsonNode perNummer =
        read(
            mvc.perform(
                    get("/api/projects/" + fremdes + "/cards/by-number/" + fremdeNummer)
                        .cookie(alice))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(perNummer.get("canSetStatus").asBoolean()).isFalse();

    mvc.perform(
            put("/api/cards/" + fremdeId + "/status")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"status\":\"READY\"}"))
        .andExpect(status().isForbidden());
    assertThat(getCard(bob, fremdeId).get("status").asText()).isEqualTo("BACKLOG");
  }
}
