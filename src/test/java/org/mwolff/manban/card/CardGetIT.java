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
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End des Einzelkarten-Endpoints {@code GET /api/cards/{id}} (#515): Mitglieder lesen eine
 * Karte direkt, und der Sicherheitskern — eine Karte in einem fremden Projekt ist von einer
 * nirgends existierenden nicht unterscheidbar (404, kein Existenz-Leak).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardGetIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;

  @Test
  void memberReadsCard_strangerAndUnknownGetSame404() throws Exception {
    Cookie owner = anmeldung.anmelden("cardget-owner@example.com", PlatformRole.USER);
    Cookie admin = anmeldung.anmelden("cardget-admin@example.com", PlatformRole.ADMIN);
    Cookie stranger = anmeldung.anmelden("cardget-stranger@example.com", PlatformRole.USER);

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
        .andExpect(jsonPath("$.columnId").value(columnId))
        // Die Herkunftskette rechnet nur die Board-Liste (Issue #1930): null, nicht berechnet.
        .andExpect(jsonPath("$.herkunft").doesNotExist());

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
}
