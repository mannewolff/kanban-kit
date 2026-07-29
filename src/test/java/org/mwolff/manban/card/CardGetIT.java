package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * End-to-End des Einzelkarten-Endpoints {@code GET /api/cards/{id}} (#515): Mitglieder lesen eine
 * Karte direkt, und der Sicherheitskern — eine Karte in einem fremden Projekt ist von einer
 * nirgends existierenden nicht unterscheidbar (404, kein Existenz-Leak).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardGetIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void memberReadsCard_strangerAndUnknownGetSame404() throws Exception {
    Cookie owner = session("cardget-owner@example.com", PlatformRole.USER);
    Cookie admin = session("cardget-admin@example.com", PlatformRole.ADMIN);
    Cookie stranger = session("cardget-stranger@example.com", PlatformRole.USER);

    long projectId = createProject(admin, "Projekt", "cardget-owner@example.com");
    JsonNode board = createBoard(owner, projectId, "Board");
    long columnId = board.get("columns").get(0).get("id").asLong();
    JsonNode card = createCard(owner, board.get("id").asLong(), columnId, "Karte");
    long cardId = card.get("id").asLong();

    // Mitglied: 200 mit der vollen CardView.
    mvc.perform(get("/api/cards/" + cardId).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(cardId))
        .andExpect(jsonPath("$.title").value("Karte"))
        .andExpect(jsonPath("$.columnId").value(columnId));

    // Nichtmitglied und unbekannte Karte sind nicht unterscheidbar: beide 404.
    mvc.perform(get("/api/cards/" + cardId).cookie(stranger)).andExpect(status().isNotFound());
    mvc.perform(get("/api/cards/999999").cookie(owner)).andExpect(status().isNotFound());
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

  private JsonNode createCard(Cookie session, long boardId, long columnId, String title)
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
            .getContentAsString());
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
