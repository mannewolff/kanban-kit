package org.mwolff.manban.nightrun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
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
 * Die Teilnahme am Plattform-Leitstand als Schranke der lesenden Nachtlauf-Wege (Issue #1079,
 * fachliche Quelle #1064, Frage 9).
 *
 * <p>Über HTTP und nicht am Dienst, weil der <b>Statuscode</b> die Zusage ist: {@code
 * frontend/src/lib/useLeitstandDaten.ts} macht aus 403 den stillen Zustand „ohne Recht" und aus
 * jedem anderen Fehler eine kaputte Anzeige. Ein Test, der nur die Ausnahme prüft, ließe ein
 * versehentliches 404 durchgehen — und der Board-Leitstand eines Plattform-Admins sähe dann bei
 * „Letzter Lauf", „Abbruchgründe" und Verbrauch defekt aus statt still.
 *
 * <p>Alle sechs Lesewege stehen einzeln in den Tests, nicht in einer Schleife über eine Liste: Ein
 * vergessener Endpunkt fällt so beim Lesen auf, und der Fehlschlag nennt den Weg beim Namen.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NightRunTeilnahmeZugriffIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String ZONE = "Europe/Berlin";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  /** Die sechs lesenden Wege, wie sie der Browser aufruft. */
  private List<String> leseWege(long projectId) {
    String p = "/api/projects/" + projectId;
    return List.of(
        p + "/night-runs",
        p + "/night-runs/error-class-counts",
        p + "/night-runs/items?cardNumber=721",
        p + "/night-run-usage/night?date=2026-09-15&zone=" + ZONE,
        p + "/night-run-usage?type=WEEK&stepsBack=0&zone=" + ZONE,
        p + "/night-run-usage/total");
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

  /** Direkt in der Spalte, nicht über den Endpunkt: Hier geht es um Lesen, nicht um Schalten. */
  private void teilnahme(long projectId, boolean teilnehmend) {
    jdbc.update(
        "UPDATE project SET dashboard_participation = ? WHERE id = ?", teilnehmend, projectId);
  }

  private void erwarteAlleSechs(Cookie wer, long projectId, int status) throws Exception {
    for (String weg : leseWege(projectId)) {
      mvc.perform(get(weg).cookie(wer)).andExpect(status().is(status));
    }
  }

  @Test
  void derPlattformAdminBekommtAmNichtTeilnehmendenProjektAufAllenSechsWegen403() throws Exception {
    session("t403-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("t403-owner@example.com", "t403-admin@example.com");
    Cookie admin = session("t403-admin@example.com", PlatformRole.ADMIN);
    teilnahme(projectId, false);

    erwarteAlleSechs(admin, projectId, 403);
  }

  @Test
  void derPlattformAdminKommtAmTeilnehmendenProjektAufAllenSechsWegenDurch() throws Exception {
    session("t200-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("t200-owner@example.com", "t200-admin@example.com");
    Cookie admin = session("t200-admin@example.com", PlatformRole.ADMIN);
    teilnahme(projectId, true);

    erwarteAlleSechs(admin, projectId, 200);
  }

  @Test
  void derEchteOwnerKommtAuchOhneTeilnahmeAufAllenSechsWegenDurch() throws Exception {
    Cookie owner = session("towner-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("towner-owner@example.com", "towner-admin@example.com");
    teilnahme(projectId, false);

    erwarteAlleSechs(owner, projectId, 200);
  }

  /**
   * Eine Mitgliedschaft unterhalb von OWNER trägt den Nachtlauf-Zugriff auch heute nicht — der
   * Admin-Status macht daraus keinen Owner, sondern führt in die Teilnahme-Prüfung.
   */
  @Test
  void derPlattformAdminMitEchtemMemberBekommtOhneTeilnahme403() throws Exception {
    session("tmem-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("tmem-owner@example.com", "tmem-admin@example.com");
    Cookie admin = session("tmem-admin@example.com", PlatformRole.ADMIN);
    long adminId = users.findByEmail("tmem-admin@example.com").orElseThrow().requireId();
    memberships.save(
        new ProjectMembership(
            null, projectId, adminId, ProjectRole.MEMBER, java.time.Instant.now()));
    teilnahme(projectId, false);

    erwarteAlleSechs(admin, projectId, 403);
  }

  /**
   * Die schreibenden Wege bleiben unberührt: Der Nachtlauf liefert seine Ergebnisse ein, egal ob
   * das Projekt am Plattform-Leitstand teilnimmt. Die Teilnahme regelt das Lesen durch den
   * Betreiber, nicht das Melden durch das Projekt.
   */
  @Test
  void dasEinliefernBleibtOhneTeilnahmeMoeglich() throws Exception {
    session("tsubmit-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("tsubmit-owner@example.com", "tsubmit-admin@example.com");
    Cookie admin = session("tsubmit-admin@example.com", PlatformRole.ADMIN);
    teilnahme(projectId, false);

    mvc.perform(
            post("/api/projects/" + projectId + "/night-runs")
                .cookie(admin)
                .contentType("application/json")
                .content(
                    """
                    {"runs":[{"startedAt":"2026-09-15T21:00:00Z","mode":"IMPLEMENTATION",
                              "durationMs":1,"processedCount":1,"skippedCount":0,
                              "unparsedCount":0,
                              "items":[{"cardNumber":721,"title":"P","state":"GREEN"}]}]}
                    """))
        .andExpect(status().isOk());
  }
}
