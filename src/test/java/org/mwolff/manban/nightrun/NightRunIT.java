package org.mwolff.manban.nightrun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.nightrun.domain.NightRunLimits;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-End der Nachtlauf-Endpoints (Issue #723) gegen die echte Datenbank: Duplikatserkennung,
 * Reihenfolge der Antwort, Feldgrenzen samt {@code fieldErrors} und die Rechtetrennung.
 *
 * <p>Was hier steht und nicht im Controller-Unit-Test stehen kann: Die Meldung „lag schon vor"
 * entsteht am {@code ON CONFLICT} der Datenbank, und die {@code fieldErrors}-Extension liefert der
 * package-private {@code GlobalExceptionHandler} — beides ist ohne den vollen Kontext nicht
 * belegbar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NightRunIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String ERSTER = "2026-08-31T22:00:00Z";
  private static final String ZWEITER = "2026-09-01T22:00:00Z";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void submit_reportsKnownRunAsExisting_andCreatesTheNewOne_inRequestOrder() throws Exception {
    Cookie owner = session("nr-dup-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("nr-dup-owner@example.com", "nr-dup-admin@example.com");

    submit(owner, projectId, run(ERSTER, item(721, "Persistenz", "GREEN", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].created").value(true));

    // Derselbe Lauf noch einmal, zusammen mit einem neuen: eine Antwort, Anfragereihenfolge.
    submit(
            owner,
            projectId,
            run(ERSTER, item(721, "Persistenz", "GREEN", null)),
            run(ZWEITER, item(722, "Service", "RED", "CHECKS_RED")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].startedAt").value(ERSTER))
        .andExpect(jsonPath("$[0].created").value(false))
        .andExpect(jsonPath("$[1].startedAt").value(ZWEITER))
        .andExpect(jsonPath("$[1].created").value(true));

    mvc.perform(get(path(projectId)).cookie(owner)).andExpect(jsonPath("$.length()").value(2));
  }

  /**
   * Der Client dedupliziert nicht vorab — zwei gleiche Läufe in einer Anfrage lösen sich am {@code
   * ON CONFLICT} auf, ohne 409 und ohne 500.
   */
  @Test
  void submit_withDuplicateStartedAtInSameRequest_createsFirstAndReportsSecond() throws Exception {
    Cookie owner = session("nr-same-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("nr-same-owner@example.com", "nr-same-admin@example.com");

    submit(
            owner,
            projectId,
            run(ERSTER, item(721, "Persistenz", "GREEN", null)),
            run(ERSTER, item(721, "Persistenz", "GREEN", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].created").value(true))
        .andExpect(jsonPath("$[1].created").value(false));

    mvc.perform(get(path(projectId)).cookie(owner)).andExpect(jsonPath("$.length()").value(1));
  }

  /** Zu lange Auszüge sind 400 mit {@code fieldErrors} — nicht 500 an der Spaltengrenze. */
  @Test
  void submit_rejectsExcerptAndUnparsedSampleAboveLimit_withFieldErrors() throws Exception {
    Cookie owner = session("nr-limit-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("nr-limit-owner@example.com", "nr-limit-admin@example.com");
    String zuLang = "x".repeat(NightRunLimits.EXCERPT_MAX + 1);

    mvc.perform(
            post(path(projectId))
                .cookie(owner)
                .contentType("application/json")
                .content(
                    """
                    {"runs":[{"startedAt":"%s","mode":"IMPLEMENTATION","durationMs":1,
                              "processedCount":1,"skippedCount":0,"unparsedCount":0,
                              "items":[{"cardNumber":721,"title":"Persistenz","state":"RED",
                                        "errorClass":"CHECKS_RED","excerpt":"%s"}]}]}
                    """
                        .formatted(ERSTER, zuLang)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['runs[0].items[0].excerpt']").exists());

    mvc.perform(
            post(path(projectId))
                .cookie(owner)
                .contentType("application/json")
                .content(
                    """
                    {"runs":[{"startedAt":"%s","mode":"IMPLEMENTATION","durationMs":1,
                              "processedCount":0,"skippedCount":0,"unparsedCount":1,
                              "unparsedSample":"%s","items":[]}]}
                    """
                        .formatted(ERSTER, zuLang)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['runs[0].unparsedSample']").exists());

    mvc.perform(get(path(projectId)).cookie(owner)).andExpect(jsonPath("$.length()").value(0));
  }

  /** Ein Auszug genau auf der Grenze passt — die Spalte ist so lang wie die Zusicherung. */
  @Test
  void submit_acceptsExcerptAtLimit() throws Exception {
    Cookie owner = session("nr-edge-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("nr-edge-owner@example.com", "nr-edge-admin@example.com");

    mvc.perform(
            post(path(projectId))
                .cookie(owner)
                .contentType("application/json")
                .content(
                    """
                    {"runs":[{"startedAt":"%s","mode":"IMPLEMENTATION","durationMs":1,
                              "processedCount":1,"skippedCount":0,"unparsedCount":1,
                              "unparsedSample":"%s",
                              "items":[{"cardNumber":721,"title":"Persistenz","state":"RED",
                                        "errorClass":"CHECKS_RED","excerpt":"%s"}]}]}
                    """
                        .formatted(
                            ERSTER,
                            "u".repeat(NightRunLimits.EXCERPT_MAX),
                            "x".repeat(NightRunLimits.EXCERPT_MAX))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].created").value(true));
  }

  @Test
  void list_returnsRunsNewestFirst_withTheirItems() throws Exception {
    Cookie owner = session("nr-list-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("nr-list-owner@example.com", "nr-list-admin@example.com");

    submit(
            owner,
            projectId,
            run(ERSTER, item(721, "Persistenz", "GREEN", null)),
            run(ZWEITER, item(722, "Service", "RED", "CHECKS_RED")))
        .andExpect(status().isOk());

    mvc.perform(get(path(projectId)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].startedAt").value(ZWEITER))
        .andExpect(jsonPath("$[0].items.length()").value(1))
        .andExpect(jsonPath("$[0].items[0].cardNumber").value(722))
        .andExpect(jsonPath("$[0].items[0].errorClass").value("CHECKS_RED"))
        .andExpect(jsonPath("$[1].startedAt").value(ERSTER))
        .andExpect(jsonPath("$[1].items[0].cardNumber").value(721))
        .andExpect(jsonPath("$[1].items[0].state").value("GREEN"));
  }

  @Test
  void errorClassCounts_countsEveryRunThatCarriesTheClassOnce() throws Exception {
    Cookie owner = session("nr-count-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("nr-count-owner@example.com", "nr-count-admin@example.com");

    submit(
            owner,
            projectId,
            run(ERSTER, item(721, "Persistenz", "RED", "CHECKS_RED")),
            run(ZWEITER, item(722, "Service", "RED", "CHECKS_RED")))
        .andExpect(status().isOk());

    mvc.perform(get(path(projectId) + "/error-class-counts").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.CHECKS_RED").value(2));
  }

  @Test
  void allEndpoints_areForbiddenForNonOwnerMember_andNotFoundForStranger() throws Exception {
    Cookie owner = session("nr-rights-owner@example.com", PlatformRole.USER);
    Cookie viewer = session("nr-rights-viewer@example.com", PlatformRole.USER);
    Cookie stranger = session("nr-rights-stranger@example.com", PlatformRole.USER);
    long projectId = projectOf("nr-rights-owner@example.com", "nr-rights-admin@example.com");
    memberships.save(
        new ProjectMembership(
            null,
            projectId,
            userId("nr-rights-viewer@example.com"),
            ProjectRole.VIEWER,
            Instant.now()));

    String body = "{\"runs\":[%s]}".formatted(run(ERSTER, item(721, "P", "GREEN", null)));
    mvc.perform(post(path(projectId)).cookie(viewer).contentType("application/json").content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path(projectId)).cookie(stranger).contentType("application/json").content(body))
        .andExpect(status().isNotFound());

    mvc.perform(get(path(projectId)).cookie(viewer)).andExpect(status().isForbidden());
    mvc.perform(get(path(projectId)).cookie(stranger)).andExpect(status().isNotFound());
    mvc.perform(get(path(projectId) + "/error-class-counts").cookie(viewer))
        .andExpect(status().isForbidden());
    mvc.perform(get(path(projectId) + "/error-class-counts").cookie(stranger))
        .andExpect(status().isNotFound());

    // Der Owner darf, und keine der abgewiesenen Anfragen hat etwas hinterlassen.
    mvc.perform(get(path(projectId)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  private static String path(long projectId) {
    return "/api/projects/" + projectId + "/night-runs";
  }

  private ResultActions submit(Cookie owner, long projectId, String... runs) throws Exception {
    return mvc.perform(
        post(path(projectId))
            .cookie(owner)
            .contentType("application/json")
            .content("{\"runs\":[%s]}".formatted(String.join(",", runs))));
  }

  private static String run(String startedAt, String items) {
    return """
        {"startedAt":"%s","mode":"IMPLEMENTATION","durationMs":1234,"processedCount":1,
         "skippedCount":0,"unparsedCount":0,"items":[%s]}"""
        .formatted(startedAt, items);
  }

  private static String item(int cardNumber, String title, String state, String errorClass) {
    String klasse = errorClass == null ? "null" : "\"" + errorClass + "\"";
    return """
        {"cardNumber":%d,"title":"%s","state":"%s","errorClass":%s,"excerpt":"Auszug"}"""
        .formatted(cardNumber, title, state, klasse);
  }

  private long projectOf(String ownerEmail, String adminEmail) throws Exception {
    Cookie admin = session(adminEmail, PlatformRole.ADMIN);
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
