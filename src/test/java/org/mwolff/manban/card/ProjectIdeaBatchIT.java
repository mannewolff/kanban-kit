package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.TextLimits;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End des Stapel-Anlegens von Pool-Ideen (#492): Mengen-Import einer Spezifikation, dessen
 * Obergrenzen, Rechte und das dokumentierte Alles-oder-nichts-Verhalten.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ProjectIdeaBatchIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  // Spiegelt die Mengengrenze aus ProjectIdeaController (dort package-private, hier nicht
  // sichtbar). Die Textgrenze kommt seit #572 aus der geteilten Konstante, damit dieser Test nicht
  // gegen einen eigenen Zahlenwert prueft.
  private static final int MAX_IDEAS_PER_BATCH = 200;
  private static final int MAX_DESCRIPTION_LENGTH = TextLimits.MAX_TEXT;

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void batch_createsEveryIdea_withConsecutiveNumbersAndSharedTargetBoard() throws Exception {
    Cookie owner = session("batch-owner@example.com", PlatformRole.USER);
    Cookie admin = session("batch-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "batch-owner@example.com");
    long boardId = createBoard(owner, projectId);

    var created =
        json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/ideas/batch")
                        .cookie(owner)
                        .contentType("application/json")
                        .content(
                            """
                            {"ideas":[{"title":"Kapitel 1","description":"Text 1"},
                                      {"title":"Kapitel 2","description":"Text 2"},
                                      {"title":"Kapitel 3"}],
                             "targetBoardId":%d}
                            """
                                .formatted(boardId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("Kapitel 1"))
                .andExpect(jsonPath("$[0].description").value("Text 1"))
                .andExpect(jsonPath("$[2].description").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString());

    // Jede Idee traegt eine eigene, fortlaufende projektweite Nummer und ist board-los im Pool.
    int first = created.get(0).get("number").asInt();
    for (int i = 0; i < 3; i++) {
      assertThat(created.get(i).get("number").asInt()).isEqualTo(first + i);
      assertThat(created.get(i).get("boardId").isNull()).isTrue();
      assertThat(created.get(i).get("targetBoardId").asLong()).isEqualTo(boardId);
    }

    mvc.perform(get("/api/projects/" + projectId + "/ideas").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
    // Nichts landet ungefragt auf dem Board.
    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void batch_rejectsEmptyAndOversizedList() throws Exception {
    Cookie owner = session("batch-limit-owner@example.com", PlatformRole.USER);
    Cookie admin = session("batch-limit-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "batch-limit-owner@example.com");

    mvc.perform(
            post("/api/projects/" + projectId + "/ideas/batch")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"ideas\":[]}"))
        .andExpect(status().isBadRequest());

    String tooMany =
        IntStream.rangeClosed(0, MAX_IDEAS_PER_BATCH)
            .mapToObj(i -> "{\"title\":\"Kapitel %d\"}".formatted(i))
            .collect(Collectors.joining(",", "{\"ideas\":[", "]}"));
    mvc.perform(
            post("/api/projects/" + projectId + "/ideas/batch")
                .cookie(owner)
                .contentType("application/json")
                .content(tooMany))
        .andExpect(status().isBadRequest());

    mvc.perform(get("/api/projects/" + projectId + "/ideas").cookie(owner))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void batch_withOneInvalidItem_createsNothing() throws Exception {
    Cookie owner = session("batch-invalid-owner@example.com", PlatformRole.USER);
    Cookie admin = session("batch-invalid-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "batch-invalid-owner@example.com");

    // Alles-oder-nichts: ein leerer Titel im zweiten Element verwirft den gesamten Import.
    mvc.perform(
            post("/api/projects/" + projectId + "/ideas/batch")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"ideas\":[{\"title\":\"Gut\"},{\"title\":\"   \"}]}"))
        .andExpect(status().isBadRequest());

    // Ein zu langer Titel bzw. eine zu lange Beschreibung ebenso.
    mvc.perform(
            post("/api/projects/" + projectId + "/ideas/batch")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"ideas\":[{\"title\":\"%s\"}]}".formatted("t".repeat(301))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/projects/" + projectId + "/ideas/batch")
                .cookie(owner)
                .contentType("application/json")
                .content(
                    "{\"ideas\":[{\"title\":\"Gut\",\"description\":\"%s\"}]}"
                        .formatted("d".repeat(MAX_DESCRIPTION_LENGTH + 1))))
        .andExpect(status().isBadRequest());

    mvc.perform(get("/api/projects/" + projectId + "/ideas").cookie(owner))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void batch_forbiddenForMemberWithoutTicketCreate_andNotFoundForNonMember() throws Exception {
    Cookie owner = session("batch-rights-owner@example.com", PlatformRole.USER);
    Cookie viewer = session("batch-rights-viewer@example.com", PlatformRole.USER);
    Cookie stranger = session("batch-rights-stranger@example.com", PlatformRole.USER);
    Cookie admin = session("batch-rights-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "batch-rights-owner@example.com");
    memberships.save(
        new ProjectMembership(
            null,
            projectId,
            userId("batch-rights-viewer@example.com"),
            ProjectRole.VIEWER,
            Instant.now()));

    String body = "{\"ideas\":[{\"title\":\"Kapitel 1\"}]}";
    mvc.perform(
            post("/api/projects/" + projectId + "/ideas/batch")
                .cookie(viewer)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/projects/" + projectId + "/ideas/batch")
                .cookie(stranger)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isNotFound());

    mvc.perform(get("/api/projects/" + projectId + "/ideas").cookie(owner))
        .andExpect(jsonPath("$.length()").value(0));
  }

  private long createProject(Cookie admin, String ownerEmail) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/projects")
                        .cookie(admin)
                        .contentType("application/json")
                        .content("{\"name\":\"P\",\"ownerEmail\":\"%s\"}".formatted(ownerEmail)))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private long createBoard(Cookie owner, long projectId) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/boards")
                        .cookie(owner)
                        .contentType("application/json")
                        .content("{\"name\":\"B\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().id();
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
