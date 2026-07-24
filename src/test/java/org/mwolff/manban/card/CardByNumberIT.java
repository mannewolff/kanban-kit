package org.mwolff.manban.card;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * End-to-End des projektweiten Karten-Lookups nach Nummer: Mitglied löst Board-Karte und board-lose
 * Pool-Idee per Nummer auf; Nichtmitglied und unbekannte Nummer → 404 (#408).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardByNumberIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void resolvesBoardCardAndPoolIdeaByNumber_andRejectsNonMemberAndUnknown() throws Exception {
    Cookie owner = session("bynum-owner@example.com", PlatformRole.USER);
    Cookie admin = session("bynum-admin@example.com", PlatformRole.ADMIN);
    Cookie stranger = session("bynum-stranger@example.com", PlatformRole.USER);

    long projectId =
        json.readTree(
                mvc.perform(
                        post("/api/projects")
                            .cookie(admin)
                            .contentType("application/json")
                            .content("{\"name\":\"P\",\"ownerEmail\":\"bynum-owner@example.com\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    var boardNode =
        json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/boards")
                        .cookie(owner)
                        .contentType("application/json")
                        .content("{\"name\":\"B\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    long boardId = boardNode.get("id").asLong();
    long columnId = boardNode.get("columns").get(0).get("id").asLong();

    // Board-gebundene Karte anlegen (projektweite Nummer).
    var cardNode =
        json.readTree(
            mvc.perform(
                    post("/api/boards/" + boardId + "/cards")
                        .cookie(owner)
                        .contentType("application/json")
                        .content("{\"columnId\":" + columnId + ",\"title\":\"Karte\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    long cardId = cardNode.get("id").asLong();
    int cardNumber = cardNode.get("number").asInt();

    // Board-lose Pool-Idee anlegen (bekommt sofort eine projektweite Nummer, #402).
    var ideaNode =
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
    long ideaId = ideaNode.get("id").asLong();
    int ideaNumber = ideaNode.get("number").asInt();

    // Mitglied löst die Board-Karte per Nummer auf.
    mvc.perform(get("/api/projects/" + projectId + "/cards/by-number/" + cardNumber).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(cardId))
        .andExpect(jsonPath("$.number").value(cardNumber))
        .andExpect(jsonPath("$.boardId").value(boardId));

    // Mitglied löst auch die board-lose Pool-Idee per Nummer auf.
    mvc.perform(get("/api/projects/" + projectId + "/cards/by-number/" + ideaNumber).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(ideaId))
        .andExpect(jsonPath("$.number").value(ideaNumber))
        .andExpect(jsonPath("$.boardId").value(nullValue()))
        .andExpect(jsonPath("$.ideaStored").value(true));

    // Nichtmitglied → 404 (kein Existenz-Leak).
    mvc.perform(
            get("/api/projects/" + projectId + "/cards/by-number/" + cardNumber).cookie(stranger))
        .andExpect(status().isNotFound());

    // Unbekannte Nummer → 404.
    mvc.perform(
            get("/api/projects/" + projectId + "/cards/by-number/" + (ideaNumber + 999))
                .cookie(owner))
        .andExpect(status().isNotFound());
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
