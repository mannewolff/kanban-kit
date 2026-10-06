package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.web.security.ApiAusprobierFilter;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Ende zu Ende des Ausprobier-Kennzeichens {@value ApiAusprobierFilter#HEADER} über die echte
 * Filterkette (Issue #1366, #1439): Mit Kennzeichen kommen nur aktive Plattform-Admins durch, ein
 * mitgeschicktes Projekt-Token hat dann Vorrang vor der Session, und die Rechte an den Daten prüfen
 * weiterhin die Endpunkte. Ohne Kennzeichen ändert sich nichts.
 *
 * <p>Mit stellbarer Uhr ({@link StellbareUhrConfig}, Issue #1460): Nur so lässt sich die
 * Stempel-Drosselung zwischen den Methoden vergessen, siehe {@link
 * #letTheThrottleForgetPreviousMethods()}. Die Klasse bekommt damit einen eigenen Spring-Kontext.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(StellbareUhrConfig.class)
class ApiAusprobierenIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String KENNZEICHEN = ApiAusprobierFilter.HEADER;
  private static final String ADMIN = "try-admin@example.com";
  private static final String OWNER = "try-owner@example.com";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private DataSource dataSource;
  @Autowired private MutableClock clock;

  /**
   * Die Stempel-Drosselung merkt sich je Token-ID die letzte Minute im Arbeitsspeicher des
   * geteilten Kontexts, und nach {@code RESTART IDENTITY} beginnen die Token-IDs jeder Methode
   * wieder bei 1. Ohne vorgestellte Uhr fände ein früherer Stempel derselben Minute den Eintrag
   * schon belegt, und {@code last_used_at} bliebe leer (Issue #1460) — dasselbe Muster wie in
   * {@code AccessTokenLastUsedThrottleIT}.
   */
  @BeforeEach
  void letTheThrottleForgetPreviousMethods() {
    clock.advance(Duration.ofMinutes(10));
  }

  @Test
  void adminWithMarkerReadsProjects() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);

    mvc.perform(get("/api/projects").cookie(admin).header(KENNZEICHEN, "1"))
        .andExpect(status().isOk());
  }

  @Test
  void nonAdminWithMarkerIsRejectedAndNothingIsCreated() throws Exception {
    Cookie user = session(OWNER, PlatformRole.USER);

    mvc.perform(
            post("/api/projects")
                .cookie(user)
                .header(KENNZEICHEN, "1")
                .contentType("application/json")
                .content("{\"name\":\"Versuch\",\"ownerEmail\":\"%s\"}".formatted(OWNER)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(
            jsonPath("$.detail")
                .value("Das Ausprobieren aus der API-Übersicht ist Plattform-Admins vorbehalten."));

    assertThat(count("SELECT count(*) FROM project")).isZero();
  }

  @Test
  void markerWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(get("/api/projects").header(KENNZEICHEN, "1")).andExpect(status().isUnauthorized());
  }

  /**
   * Gegenprobe ohne Kennzeichen: Ein Nicht-Admin schreibt, was er darf, wie bisher. {@code POST
   * /api/projects} taugt dafür nicht — das Anlegen eines Projekts ist ohnehin Plattform-Admins
   * vorbehalten und antwortet ihm darum mit 403 vom Endpunkt; also das Anlegen eines Boards in
   * seinem eigenen Projekt.
   */
  @Test
  void nonAdminWithoutMarkerKeepsHisRights() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    Cookie owner = session(OWNER, PlatformRole.USER);
    long projectId = createProject(admin, "Eigenes Projekt", OWNER);

    mvc.perform(
            post("/api/projects/" + projectId + "/boards")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"name\":\"Board B\"}"))
        .andExpect(status().isCreated());
    // Ohne Kennzeichen kommt die Ablehnung beim Projekt-Anlegen vom Endpunkt, nicht vom Filter.
    mvc.perform(
            post("/api/projects")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"name\":\"Versuch\",\"ownerEmail\":\"%s\"}".formatted(OWNER)))
        .andExpect(status().isForbidden())
        .andExpect(content().string(not(containsString("Ausprobieren"))));
  }

  @Test
  void adminWithMarkerAndBoundTokenRunsWithTheTokensRights() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    Cookie owner = session(OWNER, PlatformRole.USER);
    long projectId = createProject(admin, "Token-Projekt", OWNER);
    long boardId = createBoard(owner, projectId, "Board T").get("id").asLong();
    JsonNode token = boundToken(owner, projectId, boardId, "Ausprobier-Token");
    long tokenId = token.get("id").asLong();

    String created =
        mvc.perform(
                post("/api/kanban/items")
                    .cookie(admin)
                    .header(KENNZEICHEN, "1")
                    .header(TOKEN_HEADER, token.get("plaintext").asText())
                    .contentType("application/json")
                    .content("{\"title\":\"Ausprobiert\",\"direct\":true}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long cardId = json.readTree(created).get("id").asLong();

    assertThat(count("SELECT count(*) FROM card WHERE id = " + cardId)).isOne();
    assertThat(
            jdbc()
                .queryForList(
                    "SELECT origin FROM card_activity WHERE card_id = ?", String.class, cardId))
        .isNotEmpty()
        .containsOnly("TOKEN");
    assertThat(
            jdbc()
                .queryForObject(
                    "SELECT last_used_at IS NOT NULL FROM kanban_access_token WHERE id = ?",
                    Boolean.class,
                    tokenId))
        .isTrue();
  }

  @Test
  void adminWithMarkerWithoutTokenGetsTheSameRejectionOnTheKanbanApi() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);

    MockHttpServletResponse ohne = perform(get("/api/kanban/items").cookie(admin));
    MockHttpServletResponse mit =
        perform(get("/api/kanban/items").cookie(admin).header(KENNZEICHEN, "1"));

    assertThat(ohne.getStatus()).isGreaterThanOrEqualTo(400);
    assertThat(mit.getStatus()).isEqualTo(ohne.getStatus());
    assertThat(mit.getContentAsString()).isEqualTo(ohne.getContentAsString());
  }

  @Test
  void adminWithMarkerCannotSwitchParticipationOfForeignProject() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    session(OWNER, PlatformRole.USER);
    long projectId = createProject(admin, "Fremdes Projekt", OWNER);
    String path = "/api/projects/" + projectId + "/dashboard-participation";

    mvc.perform(
            put(path)
                .cookie(admin)
                .contentType("application/json")
                .content("{\"participating\":true}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            put(path)
                .cookie(admin)
                .header(KENNZEICHEN, "1")
                .contentType("application/json")
                .content("{\"participating\":true}"))
        .andExpect(status().isNotFound());

    assertThat(
            jdbc()
                .queryForObject(
                    "SELECT dashboard_participation FROM project WHERE id = ?",
                    Boolean.class,
                    projectId))
        .isFalse();
  }

  /**
   * Regression: Ohne Kennzeichen gewinnt die Session wie bisher. Das gebundene Token allein
   * erreicht {@code /api/projects} nicht (403); mit der Session daneben antwortet der Endpunkt.
   */
  @Test
  void withoutMarkerTheSessionWinsOverTheToken() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    Cookie owner = session(OWNER, PlatformRole.USER);
    long projectId = createProject(admin, "Vorrang-Projekt", OWNER);
    long boardId = createBoard(owner, projectId, "Board V").get("id").asLong();
    String token = boundToken(owner, projectId, boardId, "Vorrang-Token").get("plaintext").asText();

    mvc.perform(get("/api/projects").header(TOKEN_HEADER, token)).andExpect(status().isForbidden());
    mvc.perform(get("/api/projects").cookie(admin).header(TOKEN_HEADER, token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value((int) projectId));
  }

  // --- Fixtures ------------------------------------------------------------

  private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request).andReturn().getResponse();
  }

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  private long count(String sql) {
    Long result = jdbc().queryForObject(sql, Long.class);
    return result == null ? 0 : result;
  }

  private JsonNode boundToken(Cookie session, long projectId, long boardId, String name)
      throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"%s\",\"projectId\":%d,\"boardId\":%d}"
                            .formatted(name, projectId, boardId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
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
