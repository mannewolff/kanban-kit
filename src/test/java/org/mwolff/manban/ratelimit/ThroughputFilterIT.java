package org.mwolff.manban.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die Durchsatzbremse in der echten Filterkette (Issue #1002, Plan #995 E1): Sie greift auf dem
 * PAT-Pfad, und die Weboberfläche derselben Person bleibt davon unberührt — strukturell, weil sie
 * über die Session-Authority kommt.
 *
 * <p>Eigener Kontext mit eingeschalteter, eng eingestellter Bremse; die übrigen ITs schalten sie
 * ab. Der Pool ist klein gehalten, damit der zusätzliche Kontext das Verbindungsbudget des
 * geteilten Postgres kaum belastet (Issue #900).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
// @TestPropertySource statt @SpringBootTest(properties): Nur so überschreibt die Subklasse den
// Basiswert, der die Bremse in allen übrigen ITs abschaltet (siehe AbstractIntegrationTest).
@TestPropertySource(
    properties = {
      "manban.ratelimit.throughput.enabled=true",
      "manban.ratelimit.throughput.per-minute=2",
      "manban.ratelimit.throughput.concurrent=10",
      "spring.datasource.hikari.maximum-pool-size=3",
      "spring.datasource.hikari.minimum-idle=1"
    })
@AutoConfigureMockMvc
class ThroughputFilterIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String EMAIL = "throughput@example.com";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void exhaustedPatQuota_rejectsThePat_butTheWebInterfaceKeepsAnswering() throws Exception {
    // Given: eine Person mit Sitzung und einem ungebundenen Token.
    Cookie session = session();
    String token = unboundToken(session);

    // When: Das Kontingent (2 je Minute) wird über das Token ausgeschöpft.
    mvc.perform(get("/api/projects").header("X-Kanban-Token", token)).andExpect(status().isOk());
    mvc.perform(get("/api/projects").header("X-Kanban-Token", token)).andExpect(status().isOk());
    mvc.perform(get("/api/projects").header("X-Kanban-Token", token))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.type").value("urn:manban:overload"));

    // Then: Dieselbe Person arbeitet in der Oberfläche ungebremst weiter.
    for (int i = 0; i < 10; i++) {
      mvc.perform(get("/api/projects").cookie(session)).andExpect(status().is2xxSuccessful());
    }
  }

  private String unboundToken(Cookie session) throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"cli\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("plaintext").asText();
  }

  private Cookie session() throws Exception {
    users.save(
        new AppUser(null, EMAIL, passwordEncoder.encode(PASSWORD), "P", true, PlatformRole.USER));
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }
}
