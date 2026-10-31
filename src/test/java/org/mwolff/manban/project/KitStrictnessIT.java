package org.mwolff.manban.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
 * Die Strenge-Stufe des Kits am Projekt (Issue #2014): Ein board-gebundenes Token meldet sie im
 * Header {@code X-Kit-Strictness}, die Projektliste zeigt den zuletzt gemeldeten Wert. Erst die
 * echte Filterkette und die echte Datenbank belegen, dass der Filter hinter der Authentifizierung
 * läuft und der Direkt-Update samt Prüfbedingung greift.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KitStrictnessIT extends AbstractIntegrationTest {

  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String STRENGE = "X-Kit-Strictness";

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;

  @Test
  void dasGebundeneTokenMeldetDieStufe_undDerLetzteGueltigeWertBleibt() throws Exception {
    Cookie admin = anmeldung.anmelden("strenge-admin@example.com", PlatformRole.ADMIN);
    Cookie owner = anmeldung.anmelden("strenge-owner@example.com", PlatformRole.USER);
    long projectId = createProject(admin, "Strenge-Projekt", "strenge-owner@example.com");
    long ohneMeldung = createProject(admin, "Stilles Projekt", "strenge-owner@example.com");
    long boardId = createBoard(owner, projectId, "Board A");
    String token = boundToken(owner, projectId, boardId);

    items(token, "light");
    assertThat(strictness(owner, projectId)).isEqualTo("light");

    items(token, null);
    items(token, "bogus");
    assertThat(strictness(owner, projectId)).isEqualTo("light");

    items(token, "strict");
    assertThat(strictness(owner, projectId)).isEqualTo("strict");
    assertThat(strictness(owner, ohneMeldung)).isNull();
  }

  /** Eine Meldung per Session-Cookie zählt nicht — sie gehört zu keinem gebundenen Token. */
  @Test
  void eineSessionAnfrageMeldetNichts() throws Exception {
    Cookie admin = anmeldung.anmelden("strenge-s-admin@example.com", PlatformRole.ADMIN);
    Cookie owner = anmeldung.anmelden("strenge-s-owner@example.com", PlatformRole.USER);
    long projectId = createProject(admin, "Session-Projekt", "strenge-s-owner@example.com");

    mvc.perform(get("/api/projects").cookie(owner).header(STRENGE, "strict"))
        .andExpect(status().isOk());

    assertThat(strictness(owner, projectId)).isNull();
  }

  private void items(String token, @Nullable String stufe) throws Exception {
    var anfrage = get("/api/kanban/items").header(TOKEN_HEADER, token);
    if (stufe != null) {
      anfrage = anfrage.header(STRENGE, stufe);
    }
    mvc.perform(anfrage).andExpect(status().isOk());
  }

  private @Nullable String strictness(Cookie session, long projectId) throws Exception {
    JsonNode projekte =
        json.readTree(
            mvc.perform(get("/api/projects").cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode projekt : projekte) {
      if (projekt.get("id").asLong() == projectId) {
        JsonNode wert = projekt.get("strictness");
        assertThat(wert).as("strictness steht in der Antwort, auch als null").isNotNull();
        return wert.isNull() ? null : wert.asText();
      }
    }
    throw new AssertionError("Projekt " + projectId + " fehlt in der Liste");
  }

  private String boundToken(Cookie session, long projectId, long boardId) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(session)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"Kit\",\"projectId\":%d,\"boardId\":%d}"
                                .formatted(projectId, boardId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("plaintext")
        .asText();
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

  private long createBoard(Cookie session, long projectId, String name) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/boards")
                        .cookie(session)
                        .contentType("application/json")
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }
}
