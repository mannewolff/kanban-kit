package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die drei Pool-Routen an der Karte sind ersatzlos fort (Issue #1204, Plan-Entscheidung E7): {@code
 * POST /api/cards/{id}/idea-storage}, {@code PUT /api/cards/{id}/plan} und {@code PUT
 * /api/cards/{id}/to-pool} antworten mit 404 — nicht mit einem stillen No-op, der einem Altclient
 * Erfolg meldete, ohne etwas zu tun.
 *
 * <p>Geprüft wird mit gültiger Sitzung und an einer <b>existierenden</b> Karte: So bleibt 404 die
 * Aussage über die Route und nicht über Anmeldung oder Karte.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardPoolRoutenEntfallenIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void poolRoutenAntwortenMit404() throws Exception {
    Cookie owner = session("pool-owner@example.com", PlatformRole.USER);
    Cookie admin = session("pool-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "Pool fort", "pool-owner@example.com");
    JsonNode board = createBoard(owner, projectId, "Board");
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    long cardId = createCard(owner, boardId, columnId, "Karte");

    mvc.perform(post("/api/cards/" + cardId + "/idea-storage").cookie(owner))
        .andExpect(status().isNotFound());
    mvc.perform(
            put("/api/cards/" + cardId + "/plan")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"targetBoardId\":%d}".formatted(boardId)))
        .andExpect(status().isNotFound());
    mvc.perform(put("/api/cards/" + cardId + "/to-pool").cookie(owner))
        .andExpect(status().isNotFound());
  }

  private long createProject(Cookie admin, String name, String ownerEmail) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/projects")
                        .cookie(admin)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private JsonNode createBoard(Cookie session, long projectId, String name) throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private long createCard(Cookie session, long boardId, long columnId, String title)
      throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/boards/" + boardId + "/cards")
                        .cookie(session)
                        .contentType("application/json")
                        .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

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
}
