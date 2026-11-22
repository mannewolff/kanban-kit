package org.mwolff.manban.anbieter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Gesamttest „Das Rechenzentrum ist nicht erreichbar“ (Issue #2216, Plan #2206 E12, Fachplan #2202
 * Kriterium 10): Der Zugang des Rechenzentrums — ein gewöhnlicher Zugang des Owners, an das Board
 * gebunden — hat die Karten zuletzt vor 10 Minuten gelesen; der Kunde sieht „gerade nicht
 * erreichbar“. Nach dem nächsten Lesen mit diesem Zugang ist der Hinweis weg. Der frisch benutzte
 * gewöhnliche Zugang eines Mitglieds nimmt den Hinweis nicht weg.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AnbieterLageIT extends AbstractIntegrationTest {

  private static final String JSON = "application/json";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String OWNER_EMAIL = "lage-owner@example.com";
  private static final String KUNDE_EMAIL = "lage-kunde@example.com";
  private static final String MITGLIED_EMAIL = "lage-mitglied@example.com";

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("Szenario: Das Rechenzentrum ist nicht erreichbar")
  void dasRechenzentrumIstNichtErreichbar() throws Exception {
    Cookie owner = anmeldung.anmelden(OWNER_EMAIL, PlatformRole.USER);
    Cookie kunde = anmeldung.anmelden(KUNDE_EMAIL, PlatformRole.USER);
    Cookie mitglied = anmeldung.anmelden(MITGLIED_EMAIL, PlatformRole.USER);
    Cookie admin = anmeldung.anmelden("lage-admin@example.com", PlatformRole.ADMIN);
    long projekt =
        anlegen(
                admin,
                "/api/projects",
                "{\"name\":\"Lage\",\"ownerEmail\":\"%s\"}".formatted(OWNER_EMAIL))
            .get("id")
            .asLong();
    mitgliedMitRolle(projekt, KUNDE_EMAIL, ProjectRole.CUSTOMER);
    mitgliedMitRolle(projekt, MITGLIED_EMAIL, ProjectRole.MEMBER);
    long board =
        anlegen(owner, "/api/projects/" + projekt + "/boards", "{\"name\":\"B\"}")
            .get("id")
            .asLong();
    anlegen(
        owner,
        "/api/boards/" + board + "/labels",
        "{\"name\":\"kit:provider\",\"color\":\"#111\"}");
    String lage = "/api/projects/" + projekt + "/anbieter/lage";

    // Vor dem ersten Lesen steht kein Hinweis.
    JsonNode rechenzentrum = zugang(owner, projekt, board, "Rechenzentrum");
    JsonNode zugangDesMitglieds = zugang(mitglied, projekt, board, "CLI");
    mvc.perform(get(lage).cookie(kunde))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nichtErreichbar").value(false));

    // Angenommen das Rechenzentrum hat die Karten des Boards seit 10 Minuten nicht gelesen —
    // und das Mitglied hat mit seinem gewöhnlichen Zugang gerade gelesen.
    jdbc.update(
        "update kanban_access_token set last_used_at = ? where id = ?",
        Timestamp.from(Instant.now().minus(Duration.ofMinutes(10))),
        rechenzentrum.get("id").asLong());
    lesen(zugangDesMitglieds);

    // Wenn der Kunde sein Projekt öffnet, dann steht oben „gerade nicht erreichbar“.
    mvc.perform(get(lage).cookie(kunde))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nichtErreichbar").value(true))
        .andExpect(jsonPath("$.boards[0].boardId").value(board))
        .andExpect(jsonPath("$.boards[0].nichtErreichbar").value(true));
    // Ein Mitglied ohne die Rolle Kunde sieht den Hinweis nicht.
    mvc.perform(get(lage).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nichtErreichbar").value(false));

    // Und nach dem nächsten Lesen durch das Rechenzentrum ist der Hinweis verschwunden.
    lesen(rechenzentrum);
    mvc.perform(get(lage).cookie(kunde))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nichtErreichbar").value(false))
        .andExpect(jsonPath("$.boards[0].nichtErreichbar").value(false));
  }

  private void mitgliedMitRolle(long projekt, String email, ProjectRole rolle) {
    memberships.save(
        new ProjectMembership(
            null, projekt, users.findByEmail(email).orElseThrow().id(), rolle, Instant.now()));
  }

  /** Ein gewöhnlicher, an das Board gebundener Zugang. */
  private JsonNode zugang(Cookie session, long projekt, long board, String name) throws Exception {
    return anlegen(
        session,
        "/api/access-tokens",
        "{\"name\":\"%s\",\"projectId\":%d,\"boardId\":%d}".formatted(name, projekt, board));
  }

  /** Liest die Karten des Boards mit dem Zugang — so liest das Rechenzentrum. */
  private void lesen(JsonNode zugang) throws Exception {
    mvc.perform(get("/api/kanban/items").header(TOKEN_HEADER, zugang.get("plaintext").asText()))
        .andExpect(status().isOk());
  }

  private JsonNode anlegen(Cookie session, String pfad, String inhalt) throws Exception {
    return json.readTree(
        mvc.perform(post(pfad).cookie(session).contentType(JSON).content(inhalt))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8));
  }
}
