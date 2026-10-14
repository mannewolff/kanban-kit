package org.mwolff.manban.nightrun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.RunnerStufe;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Was der Runner jetzt tut, über HTTP (Issue #1727, Plan #1724 E13, E14): {@code GET
 * /api/projects/{projectId}/night-runs/now} mit den Rechten der Auswertung der Läufe.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NightRunNowIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private NightRunRepository runs;

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

  private void teilnahme(long projectId, boolean teilnehmend) {
    jdbc.update(
        "UPDATE project SET dashboard_participation = ? WHERE id = ?", teilnehmend, projectId);
  }

  private static String jetzt(long projectId) {
    return "/api/projects/" + projectId + "/night-runs/now";
  }

  private static NightRun lauf(
      long projectId,
      NightRunMode mode,
      Instant startedAt,
      boolean complete,
      String noWorkReason,
      List<RunnerStufe> current) {
    return new NightRun(
        null,
        projectId,
        startedAt,
        mode,
        NightRunKind.NIGHT,
        2_000,
        0,
        0,
        0,
        null,
        startedAt,
        NightRunOrigin.TOKEN,
        "runner",
        complete,
        Instant.now(),
        null,
        complete ? noWorkReason : null,
        null,
        null,
        null,
        null,
        current,
        null);
  }

  /**
   * Der Owner bekommt die laufende Kette mit ihrer Stufe und die beendete Umsetzungsnacht als
   * letzten Lauf mit ihrem Grund — die Lage aus Szenario 1.
   */
  @Test
  void derOwnerBekommt200_mitDenLaeufen() throws Exception {
    Cookie owner = session("now-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("now-owner@example.com", "now-admin@example.com");
    Instant start = Instant.now().minus(Duration.ofMinutes(20)).truncatedTo(ChronoUnit.SECONDS);
    runs.upsert(
        lauf(
            projectId,
            NightRunMode.CHAIN,
            start,
            false,
            "",
            List.of(new RunnerStufe(1665, "VORBEREITUNG", start, null, "SESSION", true))),
        List.of());
    runs.upsert(
        lauf(
            projectId,
            NightRunMode.IMPLEMENTATION,
            start.plusSeconds(60),
            true,
            "Umsetzung belegt",
            List.of()),
        List.of());

    mvc.perform(get(jetzt(projectId)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("AKTIV"))
        .andExpect(jsonPath("$.runs.length()").value(1))
        .andExpect(jsonPath("$.runs[0].mode").value("CHAIN"))
        .andExpect(jsonPath("$.runs[0].state").value("LAEUFT"))
        .andExpect(jsonPath("$.runs[0].stages[0].cardNumber").value(1665))
        .andExpect(jsonPath("$.runs[0].stages[0].stage").value("VORBEREITUNG"))
        .andExpect(jsonPath("$.runs[0].stages[0].deadline").doesNotExist())
        .andExpect(jsonPath("$.runs[0].stages[0].holdsImplementation").value(true))
        .andExpect(jsonPath("$.runs[0].lastStage").doesNotExist())
        .andExpect(jsonPath("$.lastRun.mode").value("IMPLEMENTATION"))
        .andExpect(jsonPath("$.lastRun.outcome").value("NO_WORK"))
        .andExpect(jsonPath("$.lastRun.noWorkReason").value("Umsetzung belegt"))
        .andExpect(jsonPath("$.lastRun.endedAt").value(start.plusSeconds(62).toString()));
  }

  @Test
  void ohneLauf_istDieLageNie() throws Exception {
    Cookie owner = session("now-nie-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("now-nie-owner@example.com", "now-nie-admin@example.com");

    mvc.perform(get(jetzt(projectId)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("NIE"))
        .andExpect(jsonPath("$.runs.length()").value(0))
        .andExpect(jsonPath("$.lastRun").doesNotExist());
  }

  @Test
  void einMemberBekommt403() throws Exception {
    session("now-mem-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("now-mem-owner@example.com", "now-mem-admin@example.com");
    Cookie member = session("now-mem-member@example.com", PlatformRole.USER);
    long memberId = users.findByEmail("now-mem-member@example.com").orElseThrow().requireId();
    memberships.save(
        new ProjectMembership(null, projectId, memberId, ProjectRole.MEMBER, Instant.now()));

    mvc.perform(get(jetzt(projectId)).cookie(member)).andExpect(status().isForbidden());
  }

  @Test
  void einPlattformAdminOhneTeilnahmeBekommt403() throws Exception {
    session("now-pa-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("now-pa-owner@example.com", "now-pa-admin@example.com");
    Cookie admin = session("now-pa-admin@example.com", PlatformRole.ADMIN);
    teilnahme(projectId, false);

    mvc.perform(get(jetzt(projectId)).cookie(admin)).andExpect(status().isForbidden());
  }

  @Test
  void einNichtmitgliedBekommt404() throws Exception {
    session("now-nm-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("now-nm-owner@example.com", "now-nm-admin@example.com");
    Cookie fremd = session("now-nm-fremd@example.com", PlatformRole.USER);

    mvc.perform(get(jetzt(projectId)).cookie(fremd)).andExpect(status().isNotFound());
  }
}
