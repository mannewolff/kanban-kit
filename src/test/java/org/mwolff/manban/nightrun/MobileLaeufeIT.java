package org.mwolff.manban.nightrun;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.RunnerStufe;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die Läufe auf dem Handy über HTTP gegen Postgres (Issue #1994, Plan #1989 E9 und E19): {@code GET
 * /api/mobil/laeufe} und {@code GET /api/mobil/laeufe/{laufId}} mit den Rechten der Auswertung der
 * Läufe.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MobileLaeufeIT extends AbstractIntegrationTest {

  private static final String LISTE = "/api/mobil/laeufe?zone=Europe/Berlin";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private NightRunRepository runs;
  @Autowired private BoardService boards;

  private Cookie session(String email, PlatformRole role) {
    return anmeldung.anmelden(email, role);
  }

  /** Legt das Projekt an; der Owner wird dafür vorher angemeldet, damit es sein Konto gibt. */
  private long projectOf(String ownerEmail, String adminEmail) throws Exception {
    session(ownerEmail, PlatformRole.USER);
    Cookie admin = session(adminEmail, PlatformRole.ADMIN);
    return json.readTree(
            mvc.perform(
                    post("/api/projects")
                        .cookie(admin)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"Mobil\",\"ownerEmail\":\"%s\"}".formatted(ownerEmail)))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().requireId();
  }

  private void teilnahme(long projectId, boolean teilnehmend) {
    jdbc.update(
        "UPDATE project SET dashboard_participation = ? WHERE id = ?", teilnehmend, projectId);
  }

  private static Instant vorMinuten(long minuten) {
    return Instant.now().minus(Duration.ofMinutes(minuten)).truncatedTo(ChronoUnit.SECONDS);
  }

  private long lauf(
      long projectId,
      Instant start,
      boolean complete,
      @Nullable Long boardId,
      List<RunnerStufe> current,
      List<NightRunItem> items) {
    return runs.upsert(
            new NightRun(
                null,
                projectId,
                start,
                NightRunMode.IMPLEMENTATION,
                NightRunKind.NIGHT,
                1_800_000,
                items.size(),
                0,
                0,
                null,
                start,
                NightRunOrigin.TOKEN,
                "runner-" + start.getEpochSecond(),
                complete,
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                current,
                null,
                boardId),
            items)
        .id();
  }

  private static NightRunItem paket(
      long projectId,
      Instant start,
      int karte,
      NightRunState state,
      @Nullable NightRunErrorClass errorClass,
      @Nullable String excerpt) {
    return new NightRunItem(
        null,
        null,
        projectId,
        start,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        karte,
        "Paket " + karte,
        state,
        errorClass,
        1_000L,
        null,
        excerpt,
        null,
        null,
        null,
        List.of(),
        List.of());
  }

  @Test
  void derOwnerSiehtLaufendeHaengendeUndVerstummteLaeufe_mitBoard() throws Exception {
    Cookie owner = session("mobil-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("mobil-owner@example.com", "mobil-admin@example.com");
    long boardId = boards.createBoard(userId("mobil-owner@example.com"), projectId, "Produkt").id();
    Instant laufStart = vorMinuten(20);
    long laeuft =
        lauf(
            projectId,
            laufStart,
            false,
            boardId,
            List.of(new RunnerStufe(1975, "umsetzung", laufStart, null, "SESSION", true)),
            List.of());
    Instant haengtStart = vorMinuten(40);
    long haengt =
        lauf(
            projectId,
            haengtStart,
            true,
            null,
            List.of(),
            List.of(
                paket(
                    projectId,
                    haengtStart,
                    1976,
                    NightRunState.GREY,
                    NightRunErrorClass.AWAITING_DECISION,
                    "wartet auf eine Entscheidung")));
    Instant stummStart = vorMinuten(200);
    Instant stille = vorMinuten(100);
    long verstummt = lauf(projectId, stummStart, false, boardId, List.of(), List.of());
    jdbc.update(
        "UPDATE night_run SET updated_at = ? WHERE id = ?",
        java.sql.Timestamp.from(stille),
        verstummt);
    Instant gelungenStart = vorMinuten(60);
    long gelungen =
        lauf(
            projectId,
            gelungenStart,
            true,
            boardId,
            List.of(),
            List.of(paket(projectId, gelungenStart, 1977, NightRunState.GREEN, null, "ok")));

    mvc.perform(get(LISTE).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].laufId").value(laeuft))
        .andExpect(jsonPath("$[0].zustand").value("LAEUFT"))
        .andExpect(jsonPath("$[0].projectId").value(projectId))
        .andExpect(jsonPath("$[0].projectName").value("Mobil"))
        .andExpect(jsonPath("$[0].boardId").value(boardId))
        .andExpect(jsonPath("$[0].boardName").value("Produkt"))
        .andExpect(jsonPath("$[0].startedAt").value(laufStart.toString()))
        .andExpect(jsonPath("$[1].laufId").value(haengt))
        .andExpect(jsonPath("$[1].zustand").value("HAENGT"))
        .andExpect(jsonPath("$[1].boardId").doesNotExist())
        .andExpect(jsonPath("$[1].durationMs").value(1_800_000))
        .andExpect(jsonPath("$[2].laufId").value(verstummt))
        .andExpect(jsonPath("$[2].zustand").value("GESCHEITERT"))
        .andExpect(
            jsonPath("$[2].durationMs").value(Duration.between(stummStart, stille).toMillis()));

    mvc.perform(get("/api/mobil/laeufe/" + laeuft).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lauf.zustand").value("LAEUFT"))
        .andExpect(jsonPath("$.meldung.art").value("STUFE"))
        .andExpect(jsonPath("$.meldung.cardNumber").value(1975))
        .andExpect(jsonPath("$.meldung.stage").value("umsetzung"));
    mvc.perform(get("/api/mobil/laeufe/" + haengt).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meldung.art").value("PAKET"))
        .andExpect(jsonPath("$.meldung.text").value("wartet auf eine Entscheidung"))
        .andExpect(jsonPath("$.meldung.cardNumber").value(1976));
    mvc.perform(get("/api/mobil/laeufe/" + verstummt).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.meldung.art").value("STILLE"))
        .andExpect(jsonPath("$.meldung.seit").value(stille.toString()));
    mvc.perform(get("/api/mobil/laeufe/" + gelungen).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lauf.zustand").value("BEENDET"))
        .andExpect(jsonPath("$.meldung").doesNotExist());
  }

  @Test
  void einMitgliedUnterhalbVonOwnerSiehtNichts_undBekommt404() throws Exception {
    long projectId = projectOf("mobil-mem-owner@example.com", "mobil-mem-admin@example.com");
    Cookie member = session("mobil-mem-member@example.com", PlatformRole.USER);
    memberships.save(
        new ProjectMembership(
            null,
            projectId,
            userId("mobil-mem-member@example.com"),
            ProjectRole.ADMIN,
            Instant.now()));
    long laeuft = lauf(projectId, vorMinuten(10), false, null, List.of(), List.of());

    mvc.perform(get(LISTE).cookie(member))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get("/api/mobil/laeufe/" + laeuft).cookie(member)).andExpect(status().isNotFound());
  }

  @Test
  void derPlattformAdminSiehtNurTeilnehmendeProjekte() throws Exception {
    Cookie admin = session("mobil-pa-admin@example.com", PlatformRole.ADMIN);
    long mit = projectOf("mobil-pa-owner1@example.com", "mobil-pa-admin@example.com");
    long ohne = projectOf("mobil-pa-owner2@example.com", "mobil-pa-admin@example.com");
    teilnahme(mit, true);
    teilnahme(ohne, false);
    long sichtbar = lauf(mit, vorMinuten(10), false, null, List.of(), List.of());
    long verborgen = lauf(ohne, vorMinuten(10), false, null, List.of(), List.of());

    mvc.perform(get(LISTE).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].laufId").value(hasItem((int) sichtbar)))
        .andExpect(jsonPath("$[*].laufId").value(not(hasItem((int) verborgen))));
    mvc.perform(get("/api/mobil/laeufe/" + sichtbar).cookie(admin)).andExpect(status().isOk());
    mvc.perform(get("/api/mobil/laeufe/" + verborgen).cookie(admin))
        .andExpect(status().isNotFound());
  }

  @Test
  void einUnbekannterLaufIst404_undOhneAnmeldungGibtEs401() throws Exception {
    Cookie owner = session("mobil-404-owner@example.com", PlatformRole.USER);

    mvc.perform(get("/api/mobil/laeufe/999999999").cookie(owner)).andExpect(status().isNotFound());
    mvc.perform(get(LISTE)).andExpect(status().isUnauthorized());
  }

  @Test
  void eineZoneMitFestemVersatzIst400() throws Exception {
    Cookie owner = session("mobil-zone-owner@example.com", PlatformRole.USER);

    mvc.perform(get("/api/mobil/laeufe").param("zone", "+02:00").cookie(owner))
        .andExpect(status().isBadRequest());
  }
}
