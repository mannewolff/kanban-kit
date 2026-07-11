package org.mwolff.manban.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** End-to-End-Test der Projekt-Verwaltung inkl. Owner-Isolation. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class ProjectIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;

  @Autowired private AppUserRepository users;

  @Autowired private ProjectMembershipRepository memberships;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ObjectMapper json;

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().id();
  }

  private Cookie loginAs(String email) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.USER));
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

  private long createProject(String ownerEmail, String name) throws Exception {
    if (users.findByEmail(ownerEmail).isEmpty()) {
      users.save(
          new AppUser(
              null,
              ownerEmail,
              passwordEncoder.encode(PASSWORD),
              "Person",
              true,
              PlatformRole.USER));
    }
    Cookie admin = platformAdminSession();
    String body =
        mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private Cookie platformAdminSession() throws Exception {
    String email = "project-admin@example.com";
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.ADMIN));
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

  @Test
  void adminCreatesProjectWithChosenOwner() throws Exception {
    Cookie alice = loginAs("alice-p1@example.com");
    createProject("alice-p1@example.com", "Alices Projekt");

    // Alice (normaler USER) ist OWNER und sieht das Projekt.
    String body =
        mvc.perform(get("/api/projects").cookie(alice))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<List<Object>>read(body, "$[?(@.name=='Alices Projekt')].role"))
        .contains("OWNER");
  }

  @Test
  void nonAdminCannotCreateProject() throws Exception {
    Cookie alice = loginAs("alice-nonadmin@example.com");
    mvc.perform(
            post("/api/projects")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"name\":\"X\",\"ownerEmail\":\"alice-nonadmin@example.com\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void unknownOwnerEmailIsBadRequest() throws Exception {
    mvc.perform(
            post("/api/projects")
                .cookie(platformAdminSession())
                .contentType("application/json")
                .content("{\"name\":\"X\",\"ownerEmail\":\"ghost@example.com\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void nonMemberCannotSeeOrModify() throws Exception {
    loginAs("alice-iso@example.com");
    Cookie bob = loginAs("bob-iso@example.com");
    long projectId = createProject("alice-iso@example.com", "Geheim");

    // Bobs Liste enthält Alices Projekt nicht.
    mvc.perform(get("/api/projects").cookie(bob))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='Geheim')]").isEmpty());

    // Umbenennen durch Nichtmitglied -> 404 (kein Existenz-Leak).
    mvc.perform(
            patch("/api/projects/" + projectId)
                .cookie(bob)
                .contentType("application/json")
                .content("{\"name\":\"gekapert\"}"))
        .andExpect(status().isNotFound());

    // Löschen ist System-Admin-Sache -> Nicht-Admin 403.
    mvc.perform(delete("/api/projects/" + projectId).cookie(bob)).andExpect(status().isForbidden());
  }

  @Test
  void ownerRenamesButOnlyAdminDeletes() throws Exception {
    Cookie alice = loginAs("alice-crud@example.com");
    long projectId = createProject("alice-crud@example.com", "Alt");

    // Owner darf umbenennen.
    mvc.perform(
            patch("/api/projects/" + projectId)
                .cookie(alice)
                .contentType("application/json")
                .content("{\"name\":\"Neu\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Neu"));

    // Owner darf NICHT löschen (nur System-Admin).
    mvc.perform(delete("/api/projects/" + projectId).cookie(alice))
        .andExpect(status().isForbidden());

    // System-Admin löscht.
    mvc.perform(delete("/api/projects/" + projectId).cookie(platformAdminSession()))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/projects").cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id==" + projectId + ")]").isEmpty());
  }

  @Test
  void memberCannotModify() throws Exception {
    loginAs("alice-role@example.com");
    Cookie carol = loginAs("carol-role@example.com");
    long projectId = createProject("alice-role@example.com", "Team");

    // Carol als MEMBER hinzufügen.
    memberships.save(
        new ProjectMembership(
            null, projectId, userId("carol-role@example.com"), ProjectRole.MEMBER, Instant.now()));

    // Sieht das Projekt (Mitglied), darf es aber nicht umbenennen (nicht OWNER) bzw. löschen (nicht
    // Admin).
    String projectsBody =
        mvc.perform(get("/api/projects").cookie(carol))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<List<Object>>read(projectsBody, "$[?(@.id==" + projectId + ")].role"))
        .contains("MEMBER");
    mvc.perform(
            patch("/api/projects/" + projectId)
                .cookie(carol)
                .contentType("application/json")
                .content("{\"name\":\"x\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/projects/" + projectId).cookie(carol))
        .andExpect(status().isForbidden());
  }
}
