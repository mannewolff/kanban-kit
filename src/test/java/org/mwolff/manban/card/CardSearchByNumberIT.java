package org.mwolff.manban.card;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * End-to-End der projektübergreifenden Kartensuche nach Nummer (#489): Treffer samt Ortsangabe über
 * alle Projekte des Aufrufers, Mehrdeutigkeit über Projektgrenzen hinweg, und der Sicherheitskern —
 * eine Nummer aus einem fremden Projekt ist von einer nirgends existierenden nicht unterscheidbar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardSearchByNumberIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void findsNumberAcrossOwnProjects_withLocation_andHidesForeignProjects() throws Exception {
    Cookie owner = session("search-owner@example.com", PlatformRole.USER);
    Cookie admin = session("search-admin@example.com", PlatformRole.ADMIN);
    Cookie stranger = session("search-stranger@example.com", PlatformRole.USER);

    long projectA = createProject(admin, "Projekt A", "search-owner@example.com");
    long projectB = createProject(admin, "Projekt B", "search-owner@example.com");
    long foreign = createProject(admin, "Fremdprojekt", "search-stranger@example.com");

    JsonNode boardA = createBoard(owner, projectA, "Board A");
    JsonNode boardB = createBoard(owner, projectB, "Board B");
    JsonNode boardF = createBoard(stranger, foreign, "Fremdboard");

    long columnA = boardA.get("columns").get(0).get("id").asLong();
    JsonNode cardA = createCard(owner, boardA.get("id").asLong(), columnA, "Karte A");
    int number = cardA.get("number").asInt();

    // Dieselbe Nummer in einem zweiten eigenen Projekt (Nummern sind projektweit eindeutig).
    JsonNode cardB =
        createCard(
            owner,
            boardB.get("id").asLong(),
            boardB.get("columns").get(0).get("id").asLong(),
            "Karte B");

    // Und dieselbe Nummer in einem Projekt, in dem der Owner kein Mitglied ist.
    JsonNode cardF =
        createCard(
            stranger,
            boardF.get("id").asLong(),
            boardF.get("columns").get(0).get("id").asLong(),
            "Fremde Karte");

    // Beide eigenen Treffer, unterscheidbar am Projektnamen; der fremde fehlt.
    mvc.perform(get("/api/cards/search").param("number", String.valueOf(number)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].card.id").value(cardA.get("id").asLong()))
        .andExpect(jsonPath("$[0].projectId").value(projectA))
        .andExpect(jsonPath("$[0].projectName").value("Projekt A"))
        .andExpect(jsonPath("$[0].boardId").value(boardA.get("id").asLong()))
        .andExpect(jsonPath("$[0].boardName").value("Board A"))
        .andExpect(jsonPath("$[0].boardArchived").value(false))
        .andExpect(jsonPath("$[0].columnId").value(columnA))
        .andExpect(jsonPath("$[0].columnName").value("Backlog"))
        .andExpect(jsonPath("$[1].card.id").value(cardB.get("id").asLong()))
        .andExpect(jsonPath("$[1].projectName").value("Projekt B"))
        .andExpect(jsonPath("$[1].boardName").value("Board B"));

    // Der Fremde sieht umgekehrt nur seinen eigenen Treffer — kein 403, keine Spur der anderen.
    mvc.perform(get("/api/cards/search").param("number", String.valueOf(number)).cookie(stranger))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].card.id").value(cardF.get("id").asLong()))
        .andExpect(jsonPath("$[0].projectName").value("Fremdprojekt"));

    // Unbekannte Nummer: dieselbe leere Antwort wie bei einer Nummer aus fremdem Projekt.
    mvc.perform(
            get("/api/cards/search").param("number", String.valueOf(number + 9999)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));

    // Der Plattform-Admin findet per Definition auch die fremde Karte (Bestandsverhalten).
    mvc.perform(get("/api/cards/search").param("number", String.valueOf(number)).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.card.id == " + cardF.get("id").asLong() + ")]", hasSize(1)));
  }

  @Test
  void findsPoolIdeaWithoutBoard_andCardOnArchivedBoard_butNotTrashedCard() throws Exception {
    Cookie owner = session("search2-owner@example.com", PlatformRole.USER);
    Cookie admin = session("search-admin@example.com", PlatformRole.ADMIN);

    long projectId = createProject(admin, "Projekt C", "search2-owner@example.com");

    // Board-lose Pool-Idee: Treffer ohne Board und ohne Spalte.
    JsonNode idea =
        json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/ideas")
                        .cookie(owner)
                        .contentType("application/json")
                        .content("{\"title\":\"Idee\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

    mvc.perform(get("/api/cards/search").param("number", idea.get("number").asText()).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].card.id").value(idea.get("id").asLong()))
        .andExpect(jsonPath("$[0].projectName").value("Projekt C"))
        .andExpect(jsonPath("$[0].boardId").value(nullValue()))
        .andExpect(jsonPath("$[0].boardName").value(nullValue()))
        .andExpect(jsonPath("$[0].boardArchived").value(false))
        .andExpect(jsonPath("$[0].columnId").value(nullValue()))
        .andExpect(jsonPath("$[0].columnName").value(nullValue()));

    // Karte auf einem Board, das anschließend archiviert wird: bleibt auffindbar, Board benannt.
    JsonNode board = createBoard(owner, projectId, "Altes Board");
    long boardId = board.get("id").asLong();
    JsonNode card =
        createCard(owner, boardId, board.get("columns").get(0).get("id").asLong(), "Karte");
    mvc.perform(delete("/api/boards/" + boardId).cookie(owner)).andExpect(status().isNoContent());

    mvc.perform(get("/api/cards/search").param("number", card.get("number").asText()).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].boardName").value("Altes Board"))
        .andExpect(jsonPath("$[0].boardArchived").value(true))
        .andExpect(jsonPath("$[0].columnName").value("Backlog"));

    // Karte im Papierkorb: Nummer bleibt belegt, per Suche ist sie unsichtbar.
    mvc.perform(delete("/api/cards/" + card.get("id").asLong()).cookie(owner))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/cards/search").param("number", card.get("number").asText()).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
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
