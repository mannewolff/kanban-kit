package org.mwolff.manban.deployment;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Die Platte „Auslieferung“ von der Meldung bis zur gelesenen Kennzahl (Issue #1835, Plan #1831 E1,
 * E5, E8–E11, fachlich #1784): Meldungen über {@code POST /api/kanban/deployments} mit
 * board-gebundenem Token, Lesen über {@code GET /api/boards/{boardId}/dashboard/delivery} mit der
 * Sitzung eines Projektmitglieds.
 *
 * <p>Die Uhr ist stellbar und steht während eines Falls still; jeder Fall rechnet seine Zeitpunkte
 * von ihrem Stand aus, auf Sekunden gekürzt, damit die gelesenen Zeitpunkte wortgleich vergleichbar
 * sind.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(StellbareUhrConfig.class)
class AuslieferungSzenarienIT extends AbstractIntegrationTest {

  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String MELDEN = "/api/kanban/deployments";
  private static final Duration TAG = Duration.ofDays(1);
  private static final Duration STUNDE = Duration.ofHours(1);

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private MutableClock uhr;

  private Instant jetzt;

  @BeforeEach
  void uhrAblesen() {
    jetzt = uhr.instant().truncatedTo(ChronoUnit.SECONDS);
  }

  @Test
  @DisplayName("Szenario: Erfolgreiche Auslieferung erscheint auf der Platte")
  void erfolgreicheAuslieferungErscheintAufDerPlatte() throws Exception {
    Projekt projekt = projekt("ausl-erfolg");
    Instant donnerstag = jetzt.minus(STUNDE);
    melde(projekt, "lauf-0", "SUCCESS", donnerstag.minus(Duration.ofDays(10)));
    lies(projekt).andExpect(jsonPath("$.deploymentCount").value(1));

    melde(
        projekt,
        "lauf-1",
        "SUCCESS",
        donnerstag,
        commit("montag", donnerstag.minus(Duration.ofDays(3))),
        commit("dienstag", donnerstag.minus(Duration.ofDays(2))),
        commit("mittwoch", donnerstag.minus(TAG)));

    lies(projekt)
        .andExpect(jsonPath("$.deploymentCount").value(2))
        .andExpect(jsonPath("$.leadTimeMedianSeconds").value(Duration.ofDays(2).toSeconds()));
  }

  @Test
  @DisplayName("Szenario: Gescheiterte Auslieferung und Wiederherstellung")
  void gescheiterteAuslieferungUndWiederherstellung() throws Exception {
    Projekt projekt = projekt("ausl-restore");
    Instant gescheitert = jetzt.minus(Duration.ofHours(3));
    melde(projekt, "lauf-1", "FAILURE", gescheitert);
    melde(projekt, "lauf-2", "SUCCESS", gescheitert.plus(Duration.ofHours(2)));

    lies(projekt)
        .andExpect(jsonPath("$.changeFailurePercent").value(50))
        .andExpect(jsonPath("$.timeToRestoreMedianSeconds").value(Duration.ofHours(2).toSeconds()))
        .andExpect(jsonPath("$.outageSince").value(nullValue()));
  }

  @Test
  @DisplayName("Szenario: Ausfall läuft noch")
  void ausfallLaeuftNoch() throws Exception {
    Projekt projekt = projekt("ausl-ausfall");
    Instant gescheitert = jetzt.minus(Duration.ofHours(3));
    melde(projekt, "lauf-1", "FAILURE", gescheitert);

    lies(projekt)
        .andExpect(jsonPath("$.outageSince").value(gescheitert.toString()))
        .andExpect(jsonPath("$.timeToRestoreMedianSeconds").value(nullValue()));
  }

  @Test
  @DisplayName("Szenario: Projekt ohne bisherige Auslieferung")
  void projektOhneBisherigeAuslieferung() throws Exception {
    Projekt projekt = projekt("ausl-leer");

    lies(projekt)
        .andExpect(jsonPath("$.hasReports").value(false))
        .andExpect(jsonPath("$.dataSince").value(nullValue()))
        .andExpect(jsonPath("$.leadTimeMedianSeconds").value(nullValue()))
        .andExpect(jsonPath("$.changeFailurePercent").value(nullValue()))
        .andExpect(jsonPath("$.timeToRestoreMedianSeconds").value(nullValue()));
  }

  @Test
  @DisplayName("Szenario: Daten beginnen innerhalb des Fensters")
  void datenBeginnenInnerhalbDesFensters() throws Exception {
    Projekt projekt = projekt("ausl-seit");
    Instant ersteMeldung = jetzt.minus(Duration.ofDays(21));
    melde(projekt, "lauf-1", "SUCCESS", ersteMeldung);
    melde(projekt, "lauf-2", "SUCCESS", jetzt.minus(TAG));

    lies(projekt)
        .andExpect(jsonPath("$.hasReports").value(true))
        .andExpect(jsonPath("$.dataSince").value(ersteMeldung.toString()));
  }

  @Test
  @DisplayName("Szenario: Meldungen bleiben im eigenen Projekt")
  void meldungenBleibenImEigenenProjekt() throws Exception {
    Projekt eins = projekt("ausl-eins");
    Projekt zwei = projekt("ausl-zwei");
    melde(eins, "lauf-1", "SUCCESS", jetzt.minus(Duration.ofHours(5)));
    melde(eins, "lauf-2", "SUCCESS", jetzt.minus(STUNDE));
    melde(zwei, "lauf-1", "FAILURE", jetzt.minus(Duration.ofHours(2)));

    lies(eins)
        .andExpect(jsonPath("$.deploymentCount").value(2))
        .andExpect(jsonPath("$.changeFailurePercent").value(0))
        .andExpect(jsonPath("$.outageSince").value(nullValue()));
    lies(zwei)
        .andExpect(jsonPath("$.deploymentCount").value(0))
        .andExpect(jsonPath("$.changeFailurePercent").value(100))
        .andExpect(jsonPath("$.outageSince").value(jetzt.minus(Duration.ofHours(2)).toString()));
  }

  // --- Recht (AK 16, Plan E5) ----------------------------------------------------------------

  @Test
  void einNichtmitgliedBekommt404() throws Exception {
    Projekt projekt = projekt("ausl-fremd");
    melde(projekt, "lauf-1", "SUCCESS", jetzt.minus(STUNDE));
    Cookie fremd = anmeldung.anmelden("ausl-fremd-leser@example.com", PlatformRole.USER);

    mvc.perform(get(pfad(projekt)).cookie(fremd)).andExpect(status().isNotFound());
  }

  /** Lesen braucht kein Recht über die Mitgliedschaft hinaus: Auch VIEWER sieht die Platte. */
  @Test
  void einMitgliedMitRolleViewerLiest() throws Exception {
    Projekt projekt = projekt("ausl-viewer");
    melde(projekt, "lauf-1", "SUCCESS", jetzt.minus(STUNDE));
    String email = "ausl-viewer-leser@example.com";
    Cookie viewer = anmeldung.anmelden(email, PlatformRole.USER);
    long userId = users.findByEmail(email).orElseThrow().requireId();
    memberships.save(
        new ProjectMembership(null, projekt.id(), userId, ProjectRole.VIEWER, Instant.now()));

    mvc.perform(get(pfad(projekt)).cookie(viewer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deploymentCount").value(1));
  }

  // --- Hilfen --------------------------------------------------------------------------------

  private record Projekt(long id, long boardId, Cookie owner, String token) {}

  private static String commit(String sha, Instant committedAt) {
    return "{\"sha\":\"%s\",\"committedAt\":\"%s\"}".formatted(sha, committedAt);
  }

  private void melde(
      Projekt projekt, String attemptId, String outcome, Instant finishedAt, String... commits)
      throws Exception {
    String rumpf =
        """
        {"attemptId":"%s","outcome":"%s","finishedAt":"%s","revision":"rev-%s","commits":[%s]}"""
            .formatted(attemptId, outcome, finishedAt, attemptId, String.join(",", commits));
    mvc.perform(
            post(MELDEN)
                .header(TOKEN_HEADER, projekt.token())
                .contentType("application/json")
                .content(rumpf))
        .andExpect(status().isCreated());
  }

  private ResultActions lies(Projekt projekt) throws Exception {
    return mvc.perform(get(pfad(projekt)).cookie(projekt.owner())).andExpect(status().isOk());
  }

  private static String pfad(Projekt projekt) {
    return "/api/boards/" + projekt.boardId() + "/dashboard/delivery";
  }

  /**
   * Ein Projekt mit einem Board, dessen Owner ein gewöhnlicher Nutzer ist — ein Plattform-Admin
   * läse auch ohne Mitgliedschaft und belegte die Leseregel nicht.
   */
  private Projekt projekt(String kennung) throws Exception {
    Cookie admin = anmeldung.anmelden(kennung + "-admin@example.com", PlatformRole.ADMIN);
    String ownerEmail = kennung + "@example.com";
    Cookie owner = anmeldung.anmelden(ownerEmail, PlatformRole.USER);
    long projectId =
        id(
            mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"%s\",\"ownerEmail\":\"%s\"}"
                            .formatted(kennung + "-Projekt", ownerEmail))));
    long boardId =
        id(
            mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(owner)
                    .contentType("application/json")
                    .content("{\"name\":\"Board\"}")));
    JsonNode token =
        json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(owner)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"Auslieferung\",\"projectId\":%d,\"boardId\":%d}"
                                .formatted(projectId, boardId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return new Projekt(projectId, boardId, owner, token.get("plaintext").asText());
  }

  private long id(ResultActions angelegt) throws Exception {
    return json.readTree(
            angelegt.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
        .get("id")
        .asLong();
  }
}
