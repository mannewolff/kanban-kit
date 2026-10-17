package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mwolff.manban.common.KitNamen.Sache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Zugang zum Lese-Endpunkt der Kit-Namen: Session, ungebundenes Token, ohne Anmeldung. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KitNamenControllerIT extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;

  @Test
  void angemeldetPerSession_liestDieKitNamen() throws Exception {
    Cookie session = anmeldung.anmelden("kitnamen-session@example.com", PlatformRole.USER);
    mvc.perform(get("/api/kit-namen").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sachen[0].sache").value("DURCHZIEHEN"))
        .andExpect(jsonPath("$.sachen[0].namen[1]").value("kit:carry-through"))
        .andExpect(jsonPath("$.sachen[0].schutz").value("KEINER"))
        .andExpect(jsonPath("$.titelPraefixe[0].namen[1]").value("[Requirement]"))
        .andExpect(jsonPath("$.namensraum[6]").value("goal:"));
  }

  @Test
  void ungebundenesToken_liestDieKitNamen() throws Exception {
    Cookie session = anmeldung.anmelden("kitnamen-token@example.com", PlatformRole.USER);
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"Kit-Namen\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = json.readTree(body).get("plaintext").asText();

    mvc.perform(get("/api/kit-namen").header("X-Kanban-Token", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sachen[0].sache").value("DURCHZIEHEN"));
  }

  @Test
  void ohneAnmeldung_istNichtAngemeldet() throws Exception {
    mvc.perform(get("/api/kit-namen")).andExpect(status().isUnauthorized());
  }

  @Test
  void schutz_stehtFuerJedeSacheGleichDerRegelDerSache() throws Exception {
    Cookie session = anmeldung.anmelden("kitnamen-schutz@example.com", PlatformRole.USER);
    String body =
        mvc.perform(get("/api/kit-namen").cookie(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode sachen = json.readTree(body).get("sachen");
    assertThat(sachen).hasSize(Sache.values().length);
    for (JsonNode sache : sachen) {
      assertThat(sache.get("schutz").asText())
          .as(sache.get("sache").asText())
          .isEqualTo(Sache.valueOf(sache.get("sache").asText()).schutz().name());
    }
  }
}
