package org.mwolff.manban.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.Texte;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Die Melde-Schnittstelle der Auslieferungen über die echte Strecke (Issue #1834, Plan #1831 E2,
 * E3, E4, E6, E7, fachlich #1784 AK 10, 12, 13).
 *
 * <p>Jeder Aufruf trägt allein {@code X-Kanban-Token}, ohne Sitzungs-Cookie; die Sitzung dient nur
 * dazu, Projekt, Board und Token anzulegen. Den Szenario-Test „Meldungen bleiben im eigenen
 * Projekt“ trägt das Paket der Lese-Schnittstelle; hier ist belegt, dass eine Meldung im Projekt
 * des Token-Boards landet und in keinem anderen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DeploymentIngestIT extends AbstractIntegrationTest {

  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String PFAD = "/api/kanban/deployments";
  private static final String LATEST = "/api/kanban/deployments/latest";
  private static final String ENDE = "2026-10-17T12:00:00Z";
  private static final String FRUEHER = "2026-10-16T12:00:00Z";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private Texte texte;

  private static String meldung(
      String attemptId, String outcome, String finishedAt, String revision, String... commits) {
    return """
        {"attemptId":"%s","outcome":"%s","finishedAt":"%s","revision":"%s","commits":[%s]}"""
        .formatted(attemptId, outcome, finishedAt, revision, String.join(",", commits));
  }

  private static String commit(String sha, String committedAt) {
    return "{\"sha\":\"%s\",\"committedAt\":\"%s\"}".formatted(sha, committedAt);
  }

  private static String erfolg(String attemptId, String... commits) {
    return meldung(attemptId, "SUCCESS", ENDE, "rev-" + attemptId, commits);
  }

  @Test
  void eineMeldungMitGebundenemTokenLandetImProjektDesBoards_undInKeinemAnderen() throws Exception {
    Aufbau eigen = aufbau("deploy-eigen");
    Aufbau fremd = aufbau("deploy-fremd");

    melde(eigen.token(), erfolg("lauf-1", commit("aaa", "2026-10-17T09:00:00Z")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.attemptId").value("lauf-1"))
        .andExpect(jsonPath("$.status").value("CREATED"));

    assertThat(versuche(eigen.projectId())).containsExactly("lauf-1");
    assertThat(versuche(fremd.projectId())).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT token_name FROM deployment_attempt WHERE project_id = ?",
                String.class,
                eigen.projectId()))
        .isEqualTo(eigen.tokenName());
    assertThat(commits(eigen.projectId())).containsExactly("aaa@2026-10-17T09:00:00Z");
  }

  @Test
  void einUngebundenesTokenWirdMit409AbgewiesenUndErscheintInKeinemProjekt() throws Exception {
    Cookie session = session("deploy-frei@example.com", PlatformRole.ADMIN);
    String frei = plaintext(session, "{\"name\":\"Ungebunden\"}");

    melde(frei, erfolg("lauf-frei"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(text("deployment.token-ohne-board")));
    mvc.perform(get(LATEST).header(TOKEN_HEADER, frei))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(text("deployment.token-ohne-board")));

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM deployment_attempt WHERE attempt_id = 'lauf-frei'",
                Integer.class))
        .isZero();
  }

  @Test
  void dieselbeMeldungAuchMitCommitsInAndererReihenfolgeIstUnchangedUndZaehltEinmal()
      throws Exception {
    Aufbau aufbau = aufbau("deploy-wdh");
    String a = commit("aaa", "2026-10-17T09:00:00Z");
    String b = commit("bbb", "2026-10-17T10:00:00Z");

    melde(aufbau.token(), erfolg("lauf-1", a, b)).andExpect(status().isCreated());
    melde(aufbau.token(), erfolg("lauf-1", b, a))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attemptId").value("lauf-1"))
        .andExpect(jsonPath("$.status").value("UNCHANGED"));

    assertThat(versuche(aufbau.projectId())).containsExactly("lauf-1");
    assertThat(commits(aufbau.projectId()))
        .containsExactlyInAnyOrder("aaa@2026-10-17T09:00:00Z", "bbb@2026-10-17T10:00:00Z");
  }

  @Test
  void eineAndereKennungIstEinEigenerVersuch() throws Exception {
    Aufbau aufbau = aufbau("deploy-neu");

    melde(aufbau.token(), erfolg("lauf-1")).andExpect(status().isCreated());
    melde(aufbau.token(), erfolg("lauf-2"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CREATED"));

    assertThat(versuche(aufbau.projectId())).containsExactlyInAnyOrder("lauf-1", "lauf-2");
  }

  @Test
  void dieselbeKennungMitAnderemInhaltIstEinKonfliktUndAendertNichts() throws Exception {
    Aufbau aufbau = aufbau("deploy-konflikt");

    melde(aufbau.token(), erfolg("lauf-1")).andExpect(status().isCreated());
    melde(aufbau.token(), meldung("lauf-1", "FAILURE", ENDE, "rev-lauf-1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value(text("deployment.attempt.conflict")));

    assertThat(
            jdbc.queryForObject(
                "SELECT outcome FROM deployment_attempt WHERE project_id = ?",
                String.class,
                aufbau.projectId()))
        .isEqualTo("SUCCESS");
  }

  /**
   * Das gebundene Token entstand, als der Inhaber noch Karten anlegen durfte; jetzt ist er nur
   * VIEWER. Ein Token kann nie mehr als sein Inhaber (E4).
   */
  @Test
  void einInhaberMitRolleViewerWirdMit403Abgewiesen() throws Exception {
    Mitglied mitglied = mitglied("deploy-viewer");
    jdbc.update(
        "UPDATE project_membership SET role = 'VIEWER' WHERE project_id = ? AND user_id = ?",
        mitglied.projectId(),
        mitglied.userId());

    melde(mitglied.token(), erfolg("lauf-1")).andExpect(status().isForbidden());
    mvc.perform(get(LATEST).header(TOKEN_HEADER, mitglied.token()))
        .andExpect(status().isForbidden());

    assertThat(versuche(mitglied.projectId())).isEmpty();
  }

  @Test
  void dasTokenEinesEntferntenMitgliedsWirdMit404Abgewiesen() throws Exception {
    Mitglied mitglied = mitglied("deploy-entfernt");
    jdbc.update(
        "DELETE FROM project_membership WHERE project_id = ? AND user_id = ?",
        mitglied.projectId(),
        mitglied.userId());

    melde(mitglied.token(), erfolg("lauf-1")).andExpect(status().isNotFound());
    mvc.perform(get(LATEST).header(TOKEN_HEADER, mitglied.token()))
        .andExpect(status().isNotFound());

    assertThat(versuche(mitglied.projectId())).isEmpty();
  }

  @Test
  void einFinishedAtInDerZukunftWirdMit400Abgewiesen() throws Exception {
    Aufbau aufbau = aufbau("deploy-zukunft");
    String zukunft = Instant.now().plus(1, ChronoUnit.HOURS).toString();

    melde(aufbau.token(), meldung("lauf-1", "SUCCESS", zukunft, "rev"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(text("deployment.finished-at.zukunft")));

    assertThat(versuche(aufbau.projectId())).isEmpty();
  }

  @Test
  void einFehlendesPflichtfeldWirdMit400Abgewiesen() throws Exception {
    Aufbau aufbau = aufbau("deploy-pflicht");

    melde(aufbau.token(), "{\"attemptId\":\"lauf-1\",\"outcome\":\"SUCCESS\",\"commits\":[]}")
        .andExpect(status().isBadRequest());

    assertThat(versuche(aufbau.projectId())).isEmpty();
  }

  @Test
  void ohneGemeldetenVersuchAntwortetLatestMit204() throws Exception {
    Aufbau aufbau = aufbau("deploy-leer");

    mvc.perform(get(LATEST).header(TOKEN_HEADER, aufbau.token()))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }

  /** Der späteste Ausgang zählt, nicht der zuletzt gemeldete und nicht nur der erfolgreiche. */
  @Test
  void latestLiefertDenVersuchMitDemGroesstenFinishedAtGleichWelchenAusgangs() throws Exception {
    Aufbau aufbau = aufbau("deploy-latest");

    melde(aufbau.token(), meldung("lauf-2", "FAILURE", ENDE, "rev-2"))
        .andExpect(status().isCreated());
    melde(aufbau.token(), meldung("lauf-1", "SUCCESS", FRUEHER, "rev-1"))
        .andExpect(status().isCreated());

    mvc.perform(get(LATEST).header(TOKEN_HEADER, aufbau.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value("rev-2"))
        .andExpect(jsonPath("$.outcome").value("FAILURE"))
        .andExpect(jsonPath("$.finishedAt").value(ENDE));
  }

  /**
   * E6: Zwei Versuche melden denselben Commit. Ob sie geordnet oder vertauscht eingehen, der
   * gespeicherte Bestand ist derselbe — die Reihenfolge des Eingangs entscheidet nichts.
   */
  @Test
  void vertauschtGemeldeteVersucheHinterlassenDenselbenBestandWieGeordnete() throws Exception {
    Aufbau geordnet = aufbau("deploy-geordnet");
    Aufbau vertauscht = aufbau("deploy-vertauscht");
    String gescheitert =
        meldung("lauf-1", "FAILURE", FRUEHER, "rev-1", commit("aaa", "2026-10-16T09:00:00Z"));
    String erfolgreich =
        meldung(
            "lauf-2",
            "SUCCESS",
            ENDE,
            "rev-2",
            commit("aaa", "2026-10-16T09:00:00Z"),
            commit("bbb", "2026-10-17T09:00:00Z"));

    melde(geordnet.token(), gescheitert).andExpect(status().isCreated());
    melde(geordnet.token(), erfolgreich).andExpect(status().isCreated());
    melde(vertauscht.token(), erfolgreich).andExpect(status().isCreated());
    melde(vertauscht.token(), gescheitert).andExpect(status().isCreated());

    assertThat(bestand(vertauscht.projectId())).isEqualTo(bestand(geordnet.projectId()));
    assertThat(bestand(geordnet.projectId()))
        .containsExactly(
            "lauf-1|FAILURE|" + FRUEHER + "|rev-1|aaa@2026-10-16T09:00:00Z",
            "lauf-2|SUCCESS|" + ENDE + "|rev-2|aaa@2026-10-16T09:00:00Z",
            "lauf-2|SUCCESS|" + ENDE + "|rev-2|bbb@2026-10-17T09:00:00Z");
  }

  @Test
  void eineSitzungOhneTokenWirdAbgewiesenWieDieUebrigeKanbanSchnittstelle() throws Exception {
    Aufbau aufbau = aufbau("deploy-session");

    mvc.perform(
            post(PFAD)
                .cookie(aufbau.session())
                .contentType("application/json")
                .content(erfolg("lauf-1")))
        .andExpect(status().isForbidden());
    mvc.perform(get(LATEST).cookie(aufbau.session())).andExpect(status().isForbidden());
    mvc.perform(get("/api/kanban/items").cookie(aufbau.session()))
        .andExpect(status().isForbidden());

    assertThat(versuche(aufbau.projectId())).isEmpty();
  }

  private ResultActions melde(String token, String rumpf) throws Exception {
    return mvc.perform(
        post(PFAD).header(TOKEN_HEADER, token).contentType("application/json").content(rumpf));
  }

  private String text(String schluessel) {
    return texte.text(Sprache.DE, schluessel);
  }

  private List<String> versuche(long projectId) {
    return jdbc.queryForList(
        "SELECT attempt_id FROM deployment_attempt WHERE project_id = ? ORDER BY attempt_id",
        String.class,
        projectId);
  }

  private List<String> commits(long projectId) {
    return jdbc
        .queryForList(
            "SELECT sha, committed_at FROM deployment_commit WHERE project_id = ? ORDER BY sha",
            projectId)
        .stream()
        .map(z -> z.get("sha") + "@" + zeit(z, "committed_at"))
        .toList();
  }

  /** Der gespeicherte Bestand ohne interne Kennungen und Eingangszeitpunkte, sortiert. */
  private List<String> bestand(long projectId) {
    return jdbc
        .queryForList(
            "SELECT a.attempt_id, a.outcome, a.finished_at, a.revision, c.sha, c.committed_at"
                + " FROM deployment_attempt a JOIN deployment_commit c ON c.attempt_ref = a.id"
                + " WHERE a.project_id = ? ORDER BY a.attempt_id, c.sha",
            projectId)
        .stream()
        .map(
            z ->
                String.join(
                    "|",
                    String.valueOf(z.get("attempt_id")),
                    String.valueOf(z.get("outcome")),
                    zeit(z, "finished_at"),
                    String.valueOf(z.get("revision")),
                    z.get("sha") + "@" + zeit(z, "committed_at")))
        .toList();
  }

  private static String zeit(Map<String, Object> zeile, String spalte) {
    return ((Timestamp) zeile.get(spalte)).toInstant().toString();
  }

  private record Aufbau(Cookie session, long projectId, String token, String tokenName) {}

  private record Mitglied(long projectId, long userId, String token) {}

  private Aufbau aufbau(String kennung) throws Exception {
    Cookie session = session(kennung + "@example.com", PlatformRole.ADMIN);
    long projectId = createProject(session, kennung + "-Projekt", kennung + "@example.com");
    long boardId = createBoard(session, projectId);
    String name = kennung + "-Token";
    String token =
        plaintext(
            session,
            "{\"name\":\"%s\",\"projectId\":%d,\"boardId\":%d}"
                .formatted(name, projectId, boardId));
    return new Aufbau(session, projectId, token, name);
  }

  /** Ein MEMBER mit board-gebundenem Token; die Rolle ändert der Fall danach selbst. */
  private Mitglied mitglied(String kennung) throws Exception {
    Cookie admin = session(kennung + "-owner@example.com", PlatformRole.ADMIN);
    Cookie mitglied = session(kennung + "@example.com", PlatformRole.USER);
    long projectId = createProject(admin, kennung + "-Projekt", kennung + "-owner@example.com");
    long boardId = createBoard(admin, projectId);
    long userId = users.findByEmail(kennung + "@example.com").orElseThrow().requireId();
    // Direkt angelegt statt ueber eine Einladung: Gebraucht wird allein die Rolle, nicht der Weg
    // dorthin — dieselbe Abkuerzung wie in NightRunIngestIT.
    memberships.save(
        new ProjectMembership(null, projectId, userId, ProjectRole.MEMBER, Instant.now()));
    String token =
        plaintext(
            mitglied,
            "{\"name\":\"Mitglied\",\"projectId\":%d,\"boardId\":%d}"
                .formatted(projectId, boardId));
    return new Mitglied(projectId, userId, token);
  }

  private String plaintext(Cookie session, String body) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(session)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("plaintext")
        .asText();
  }

  private long createProject(Cookie admin, String name, String ownerEmail) throws Exception {
    return id(
        mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
            .andExpect(status().isCreated()));
  }

  private long createBoard(Cookie session, long projectId) throws Exception {
    return id(
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"Board\"}"))
            .andExpect(status().isCreated()));
  }

  private long id(ResultActions angelegt) throws Exception {
    JsonNode rumpf = json.readTree(angelegt.andReturn().getResponse().getContentAsString());
    return rumpf.get("id").asLong();
  }

  private Cookie session(String email, PlatformRole role) {
    return anmeldung.anmelden(email, role);
  }
}
