package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End der Herkunft in der Board-Liste (Issue #1930): {@code GET /api/boards/{boardId}/cards}
 * liefert je Karte die Vorfahren bis zum Ursprung, über Boards desselben Projekts, archivierte
 * eingeschlossen, und eine Karte im Papierkorb als Lücke, über die die Kette weitergeht.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardHerkunftListeIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;

  @Test
  void boardListe_liefertDieKetteUeberZweiBoards_undDenPapierkorbAlsLuecke() throws Exception {
    String email = "herkunft-liste-owner@example.com";
    Cookie session = anmeldung.anmelden(email, PlatformRole.USER);
    long projectId = createProject(email);
    JsonNode boardA = createBoard(session, projectId, "A");
    JsonNode boardB = createBoard(session, projectId, "B");
    long a = boardA.get("id").asLong();
    long spalteA = boardA.get("columns").get(0).get("id").asLong();
    long b = boardB.get("id").asLong();
    long spalteB = boardB.get("columns").get(0).get("id").asLong();

    JsonNode idee = createCard(session, b, spalteB, "[Idee] Ursprung", null);
    JsonNode fachlich =
        createCard(session, a, spalteA, "[Fachlich] Anforderung", idee.get("number").asInt());
    JsonNode plan = createCard(session, a, spalteA, "[Plan] Plan", fachlich.get("number").asInt());
    JsonNode paket = createCard(session, a, spalteA, "Paket", plan.get("number").asInt());
    mvc.perform(post("/api/cards/" + plan.get("id").asLong() + "/archive").cookie(session))
        .andExpect(status().isOk());

    assertThat(herkunft(session, a, paket.get("id").asLong()))
        .containsExactly(
            plan.get("number").asInt() + "|[Plan] Plan|true|null",
            fachlich.get("number").asInt() + "|[Fachlich] Anforderung|false|null",
            idee.get("number").asInt() + "|[Idee] Ursprung|false|null");

    mvc.perform(delete("/api/cards/" + fachlich.get("id").asLong()).cookie(session))
        .andExpect(status().is2xxSuccessful());

    assertThat(herkunft(session, a, paket.get("id").asLong()))
        .containsExactly(
            plan.get("number").asInt() + "|[Plan] Plan|true|null",
            fachlich.get("number").asInt() + "|null|false|PAPIERKORB",
            idee.get("number").asInt() + "|[Idee] Ursprung|false|null");
  }

  /**
   * Die Herkunft einer Karte der Board-Liste, je Glied als {@code number|title|archived|luecke}.
   */
  private List<String> herkunft(Cookie session, long boardId, long cardId) throws Exception {
    JsonNode liste =
        json.readTree(
            mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    List<String> glieder = new ArrayList<>();
    for (JsonNode karte : liste) {
      if (karte.get("id").asLong() == cardId) {
        for (JsonNode g : karte.get("herkunft")) {
          glieder.add(
              g.get("number").asInt()
                  + "|"
                  + text(g.get("title"))
                  + "|"
                  + g.get("archived").asBoolean()
                  + "|"
                  + text(g.get("luecke")));
        }
      }
    }
    return glieder;
  }

  private static String text(@Nullable JsonNode n) {
    return n == null || n.isNull() ? "null" : n.asText();
  }

  private long createProject(String ownerEmail) throws Exception {
    Cookie admin = anmeldung.anmelden("herkunft-liste-admin@example.com", PlatformRole.ADMIN);
    String body =
        mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"P\",\"ownerEmail\":\"%s\"}".formatted(ownerEmail)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private JsonNode createBoard(Cookie session, long projectId, String name) throws Exception {
    String body =
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private JsonNode createCard(
      Cookie session, long boardId, long columnId, String title, @Nullable Integer derivedFrom)
      throws Exception {
    String herkunft = derivedFrom == null ? "" : ",\"derivedFrom\":" + derivedFrom;
    String body =
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"columnId\":%d,\"title\":\"%s\"%s}"
                            .formatted(columnId, title, herkunft)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }
}
