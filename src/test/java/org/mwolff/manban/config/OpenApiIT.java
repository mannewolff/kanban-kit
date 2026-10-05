package org.mwolff.manban.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die API-Beschreibung unter {@code /api/openapi} (Issue #1402, Plan #1400), Teil (a): der Zugang.
 * Abrufen darf sie nur, wer per Session angemeldet ist (E1) — ein Zugriffs-Token wird wie bei
 * {@code /api/access-tokens} mit 403 abgewiesen, ein ungültiges oder widerrufenes mit 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OpenApiIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;

  @Autowired private AppUserRepository users;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ObjectMapper json;

  private Cookie loginAs(String email) throws Exception {
    users.save(
        new AppUser(
            null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.USER));
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }

  private JsonNode tokenAnlegen(Cookie session) throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"Spezifikation\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  @Test
  void ohneAnmeldungAntwortet401() throws Exception {
    mvc.perform(get("/api/openapi")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/openapi.yaml")).andExpect(status().isUnauthorized());
  }

  @Test
  void mitGueltigemTokenAntwortet403() throws Exception {
    String token = tokenAnlegen(loginAs("spec-token@example.com")).get("plaintext").asText();

    mvc.perform(get("/api/openapi").header("X-Kanban-Token", token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/openapi.yaml").header("X-Kanban-Token", token))
        .andExpect(status().isForbidden());
  }

  @Test
  void mitUngueltigemTokenAntwortet401() throws Exception {
    mvc.perform(get("/api/openapi").header("X-Kanban-Token", "tk_deadbeef"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void mitWiderrufenemTokenAntwortet401() throws Exception {
    Cookie session = loginAs("spec-revoked@example.com");
    JsonNode token = tokenAnlegen(session);
    mvc.perform(delete("/api/access-tokens/" + token.get("id").asLong()).cookie(session))
        .andExpect(status().is2xxSuccessful());

    mvc.perform(get("/api/openapi").header("X-Kanban-Token", token.get("plaintext").asText()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void mitSessionLiefertDieBeschreibungAlsJson() throws Exception {
    Cookie session = loginAs("spec-json@example.com");

    String body =
        mvc.perform(get("/api/openapi").cookie(session))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.info.title").value(OpenApiConfig.TITEL))
            .andExpect(
                jsonPath("$.components.securitySchemes.sessionCookie.name").value("manban_session"))
            .andExpect(
                jsonPath("$.components.securitySchemes.projektToken.name").value("X-Kanban-Token"))
            .andExpect(jsonPath("$.components.schemas.ProblemDetail.type").value("object"))
            .andExpect(
                jsonPath("$.components.schemas.ProblemDetail.properties.fieldErrors.type")
                    .value("object"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode spec = json.readTree(body);

    // Version aus build-info (E8), nicht der Rückfall.
    assertThat(spec.at("/info/version").asText())
        .isNotBlank()
        .isNotEqualTo(OpenApiConfig.VERSION_UNBEKANNT);
    // Zugang je Pfad aus derselben Tabelle wie die Security-Regeln.
    assertThat(spec.at("/paths/~1api~1auth~1login/post/x-zugang").asText())
        .isEqualTo("oeffentlich");
    assertThat(spec.at("/paths/~1api~1kanban~1items/get/x-zugang").asText())
        .isEqualTo("nur-projekt-token");
    assertThat(spec.at("/paths/~1api~1kanban~1items/get/responses/401").isMissingNode()).isFalse();
    // springdoc blendet @AuthenticationPrincipal-Parameter selbst aus (E13).
    JsonNode archivieren = spec.at("/paths/~1api~1cards~1{cardId}~1archive/post");
    assertThat(archivieren.isMissingNode()).isFalse();
    assertThat(archivieren.at("/parameters").findValuesAsText("name")).containsExactly("cardId");
  }

  @Test
  void mitSessionLiefertDieBeschreibungAlsYaml() throws Exception {
    Cookie session = loginAs("spec-yaml@example.com");

    String body =
        mvc.perform(get("/api/openapi.yaml").cookie(session))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/vnd.oai.openapi"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(body).startsWith("openapi: ").contains("title: " + OpenApiConfig.TITEL);
  }
}
