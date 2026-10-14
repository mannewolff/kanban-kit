package org.mwolff.manban.nightrun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardService;
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
 * Was der Runner jetzt tut und warum ein Paket wartet, über HTTP (Issues #1727 und #1728, Plan
 * #1724 E12 bis E14): {@code GET /api/projects/{projectId}/night-runs/now} und {@code GET
 * /api/cards/{cardId}/night-wait} mit den Rechten der Auswertung der Läufe.
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
  @Autowired private BoardService boards;
  @Autowired private BoardColumnRepository columns;
  @Autowired private CardService cards;

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

  /**
   * Issue #1742: Nach dem Quittieren seiner Störung fehlt der verstummte Lauf; ein laufender Lauf
   * nennt sein Token.
   */
  @Test
  void nachDemQuittieren_fehltDerVerstummteLauf_undDieZeileNenntDasToken() throws Exception {
    Cookie owner = session("now-ack-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("now-ack-owner@example.com", "now-ack-admin@example.com");
    Cookie admin = session("now-ack-admin@example.com", PlatformRole.ADMIN);
    teilnahme(projectId, true);
    Instant start = Instant.now().minus(Duration.ofDays(20)).truncatedTo(ChronoUnit.SECONDS);
    long verstummt =
        runs.upsert(lauf(projectId, NightRunMode.CHAIN, start, false, "", List.of()), List.of())
            .id();
    long bleibt =
        runs.upsert(
                lauf(
                    projectId,
                    NightRunMode.IMPLEMENTATION,
                    start.minus(Duration.ofDays(1)),
                    false,
                    "",
                    List.of()),
                List.of())
            .id();
    jdbc.update(
        "UPDATE night_run SET updated_at = started_at WHERE id IN (?, ?)", verstummt, bleibt);

    mvc.perform(get(jetzt(projectId)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runs.length()").value(2))
        .andExpect(jsonPath("$.runs[0].runId").value(verstummt))
        .andExpect(jsonPath("$.runs[0].state").value("VERSTUMMT"))
        .andExpect(jsonPath("$.runs[0].tokenName").value("runner"));

    mvc.perform(delete("/api/admin/disruptions/" + verstummt).cookie(admin))
        .andExpect(status().isNoContent());

    mvc.perform(get(jetzt(projectId)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("AKTIV"))
        .andExpect(jsonPath("$.runs.length()").value(1))
        .andExpect(jsonPath("$.runs[0].runId").value(bleibt))
        .andExpect(jsonPath("$.lastRun").doesNotExist());
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

  // --- Wartegrund (Issue #1728) ---------------------------------------------------------------

  private static String warten(long cardId) {
    return "/api/cards/" + cardId + "/night-wait";
  }

  private long adminId(String adminEmail) {
    return users.findByEmail(adminEmail).orElseThrow().requireId();
  }

  private long spalte(long boardId, String name) {
    return columns.findByBoardId(boardId).stream()
        .filter(s -> name.equals(s.name()))
        .findFirst()
        .orElseThrow()
        .requireId();
  }

  /** Legt ein Board und darauf eine Karte in der genannten Spalte an und liefert ihre ID. */
  private long karteIn(long projectId, String adminEmail, String spaltenname) {
    long admin = adminId(adminEmail);
    long boardId = boards.createBoard(admin, projectId, "Board").id();
    return cards
        .create(admin, boardId, spalte(boardId, spaltenname), "Paket", null, null, null)
        .id();
  }

  private void setzeStatus(long cardId, String status) {
    jdbc.update("UPDATE card SET status = ? WHERE id = ?", status, cardId);
  }

  @Test
  @DisplayName("Szenario: Eine Kette belegt die Umsetzung")
  void eineKetteBelegtDieUmsetzung() throws Exception {
    Cookie owner = session("wait-kette-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("wait-kette-owner@example.com", "wait-kette-admin@example.com");
    long cardId = karteIn(projectId, "wait-kette-admin@example.com", "Ready");
    Instant start = Instant.now().minus(Duration.ofMinutes(20)).truncatedTo(ChronoUnit.SECONDS);
    runs.upsert(
        lauf(
            projectId,
            NightRunMode.CHAIN,
            start,
            false,
            "",
            List.of(
                new RunnerStufe(
                    1665,
                    "VORBEREITUNG",
                    start,
                    start.plus(Duration.ofHours(2)),
                    "WAITING_FOR_IMPLEMENTATION",
                    true))),
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

    mvc.perform(get(warten(cardId)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runReasons.length()").value(2))
        .andExpect(jsonPath("$.runReasons[0].code").value("KEINE_UMSETZUNGSNACHT"))
        .andExpect(jsonPath("$.runReasons[1].code").value("BELEGT"))
        .andExpect(jsonPath("$.runReasons[1].runMode").value("CHAIN"))
        .andExpect(jsonPath("$.runReasons[1].cardNumber").value(1665))
        .andExpect(jsonPath("$.runReasons[1].stage").value("VORBEREITUNG"))
        .andExpect(jsonPath("$.notes[0].code").value("NICHT_BEWERTET"))
        .andExpect(jsonPath("$.startable").value(false));
    mvc.perform(get(jetzt(projectId)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runs[0].state").value("LAEUFT"))
        .andExpect(jsonPath("$.runs[0].stages[0].step").value("WAITING_FOR_IMPLEMENTATION"))
        .andExpect(jsonPath("$.runs[0].stages[0].holdsImplementation").value(true))
        .andExpect(
            jsonPath("$.runs[0].stages[0].deadline")
                .value(start.plus(Duration.ofHours(2)).toString()))
        .andExpect(jsonPath("$.lastRun.noWorkReason").value("Umsetzung belegt"));
  }

  @Test
  @DisplayName("Szenario: Ein Mitglied ohne Owner-Rolle sieht nichts Neues")
  void einMitgliedOhneOwnerRolleSiehtNichtsNeues() throws Exception {
    session("wait-mem-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("wait-mem-owner@example.com", "wait-mem-admin@example.com");
    long cardId = karteIn(projectId, "wait-mem-admin@example.com", "Ready");
    Cookie member = session("wait-mem-member@example.com", PlatformRole.USER);
    long memberId = users.findByEmail("wait-mem-member@example.com").orElseThrow().requireId();
    memberships.save(
        new ProjectMembership(null, projectId, memberId, ProjectRole.MEMBER, Instant.now()));
    Cookie admin = session("wait-mem-admin@example.com", PlatformRole.ADMIN);
    teilnahme(projectId, false);

    mvc.perform(get(warten(cardId)).cookie(member)).andExpect(status().isForbidden());
    mvc.perform(get(jetzt(projectId)).cookie(member)).andExpect(status().isForbidden());
    mvc.perform(get(warten(cardId)).cookie(admin)).andExpect(status().isForbidden());
  }

  /** Ready heißt Kanban-Schlüssel READY: der eigene Status vor der Spalte (E12). */
  @Test
  void eineKarteMitStatusReadyInEinerAnderenSpalte_bekommt200() throws Exception {
    Cookie owner = session("wait-status-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("wait-status-owner@example.com", "wait-status-admin@example.com");
    long cardId = karteIn(projectId, "wait-status-admin@example.com", "Backlog");
    setzeStatus(cardId, "READY");

    mvc.perform(get(warten(cardId)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runReasons[0].code").value("KEINE_UMSETZUNGSNACHT"));
  }

  @Test
  void eineKarteMitStatusBacklogInDerSpalteReady_bekommt404() throws Exception {
    Cookie owner = session("wait-backlog-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("wait-backlog-owner@example.com", "wait-backlog-admin@example.com");
    long cardId = karteIn(projectId, "wait-backlog-admin@example.com", "Ready");
    setzeStatus(cardId, "BACKLOG");

    mvc.perform(get(warten(cardId)).cookie(owner)).andExpect(status().isNotFound());
  }

  @Test
  void einNichtmitgliedBekommtAufDenWartegrund404() throws Exception {
    session("wait-nm-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("wait-nm-owner@example.com", "wait-nm-admin@example.com");
    long cardId = karteIn(projectId, "wait-nm-admin@example.com", "Ready");
    Cookie fremd = session("wait-nm-fremd@example.com", PlatformRole.USER);

    mvc.perform(get(warten(cardId)).cookie(fremd)).andExpect(status().isNotFound());
  }
}
