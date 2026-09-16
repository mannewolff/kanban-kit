package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunLimits;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
  @Autowired private NightRunRepository runs;
  @Autowired private JdbcTemplate jdbc;

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

  /**
   * Der Ketten-Lauf samt Abbruch am Zeitbudget geht durch die ganze Kette — Bindung, Service und
   * die {@code CHECK}-Constraints aus {@code V30__night_run_kette.sql} (Issue #853). Fehlte einer
   * der beiden Werte in der Migration, käme hier ein 500 an der Spaltenzusicherung statt eines 200.
   */
  @Test
  void submit_acceptsChainRunWithTimeBudgetExceeded_andListsItBack() throws Exception {
    Cookie owner = session("nr-chain-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("nr-chain-owner@example.com", "nr-chain-admin@example.com");

    mvc.perform(
            post(path(projectId))
                .cookie(owner)
                .contentType("application/json")
                .content(
                    """
                    {"runs":[{"startedAt":"%s","mode":"CHAIN","durationMs":1234,
                              "processedCount":1,"skippedCount":0,"unparsedCount":0,
                              "items":[{"cardNumber":853,"title":"Kette","state":"RED",
                                        "errorClass":"TIME_BUDGET_EXCEEDED","excerpt":"Auszug"}]}]}
                    """
                        .formatted(ERSTER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].created").value(true));

    mvc.perform(get(path(projectId)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].mode").value("CHAIN"))
        .andExpect(jsonPath("$[0].items[0].errorClass").value("TIME_BUDGET_EXCEEDED"));
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
    // Die Anlaeufe einer Karte (Issue #967) stehen in derselben Matrix.
    mvc.perform(get(path(projectId) + "/items").param("cardNumber", "721").cookie(viewer))
        .andExpect(status().isForbidden());
    mvc.perform(get(path(projectId) + "/items").param("cardNumber", "721").cookie(stranger))
        .andExpect(status().isNotFound());
    mvc.perform(get(path(projectId) + "/items").param("cardNumber", "721").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    // Der Owner darf, und keine der abgewiesenen Anfragen hat etwas hinterlassen.
    mvc.perform(get(path(projectId)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  /**
   * Der Kostenwert des hochgeladenen Protokolls kommt mit — je Lauf und je Arbeitspaket (Issue
   * #948). Der Parser kennt ihn seit Issue #773; bis hierher ließ ihn die Einlieferung fallen.
   *
   * <p>Verglichen wird mit {@code compareTo}: Die Spalte ist {@code numeric(12,6)}, und der
   * gelesene Wert trägt darum sechs Nachkommastellen. {@code equals} auf {@code BigDecimal}
   * unterscheidet 8.03 von 8.030000 — hier wäre das ein Fehlschlag ohne Fehler.
   */
  @Test
  void submit_carriesTheReportedCost_toRunAndItem() throws Exception {
    Cookie owner = session("nr-cost-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("nr-cost-owner@example.com", "nr-cost-admin@example.com");

    submit(owner, projectId, runMitKosten(ERSTER, "25.983293", itemMitKosten(791, "11.5228115")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].created").value(true));

    NightRun lauf = einzigerLauf(projectId);
    assertThat(lauf.usage()).isNotNull();
    assertThat(lauf.usage().costUsd())
        .isNotNull()
        .usingComparator(BigDecimal::compareTo)
        .isEqualTo(new BigDecimal("25.983293"));
    // Die drei Mengen bleiben dem Upload-Weg fremd: Der Browser misst sie nicht.
    assertThat(lauf.usage().inputTokens()).isNull();
    assertThat(lauf.usage().outputTokens()).isNull();
    assertThat(lauf.usage().cachedInputTokens()).isNull();

    // Gemeldet waren 11.5228115; die Spalte fuehrt sechs Nachkommastellen und rundet kaufmaennisch.
    NightRunItem paket = einzigesPaket(lauf);
    assertThat(paket.usage()).isNotNull();
    assertThat(paket.usage().costUsd())
        .isNotNull()
        .usingComparator(BigDecimal::compareTo)
        .isEqualTo(new BigDecimal("11.522812"));
  }

  /** Ohne gemeldeten Kostenwert bleibt jede Verbrauchsspalte {@code NULL} — nicht 0. */
  @Test
  void submit_withoutUsage_leavesEveryUsageColumnNull() throws Exception {
    Cookie owner = session("nr-nocost-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("nr-nocost-owner@example.com", "nr-nocost-admin@example.com");

    submit(owner, projectId, run(ERSTER, item(721, "Persistenz", "GREEN", null)))
        .andExpect(status().isOk());

    NightRun lauf = einzigerLauf(projectId);
    assertThat(lauf.usage()).isNull();
    assertThat(einzigesPaket(lauf).usage()).isNull();
  }

  /**
   * Ein per Token gemeldeter Lauf ist reicher als jedes hochgeladene Protokoll — er trägt die
   * Token-Herkunft, die Vollständigkeit und die drei Mengen. Der Upload-Weg darf ihn nicht plätten:
   * {@code insertIfAbsent} lässt ihn stehen und meldet {@code created: false}.
   */
  @Test
  void submit_doesNotFlattenRunReportedByToken() throws Exception {
    Cookie owner = session("nr-reich-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("nr-reich-owner@example.com", "nr-reich-admin@example.com");
    NightRunUsage gemeldet =
        new NightRunUsage(new BigDecimal("8.032575"), 148L, 62_411L, 8_883_160L);
    runs.upsert(
        new NightRun(
            null,
            projectId,
            Instant.parse(ERSTER),
            NightRunMode.CHAIN,
            1000L,
            1,
            0,
            0,
            null,
            Instant.now(),
            NightRunOrigin.TOKEN,
            "nachtlauf",
            true,
            Instant.now(),
            gemeldet),
        List.of());

    submit(owner, projectId, runMitKosten(ERSTER, "25.983293", itemMitKosten(791, "11.5228115")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].created").value(false));

    NightRun lauf = einzigerLauf(projectId);
    assertThat(lauf.origin()).isEqualTo(NightRunOrigin.TOKEN);
    assertThat(lauf.complete()).isTrue();
    assertThat(lauf.usage().inputTokens()).isEqualTo(148L);
    assertThat(runs.findItemsByRunIds(List.of(lauf.id()))).isEmpty();
  }

  /**
   * Ein verdraengter Lauf, erneut hochgeladen, legt seine Pakete nicht ein zweites Mal an (Issue
   * #965). Ohne die Bereinigung stuende die Karte zweimal da: {@code ON CONFLICT} kennt nur den
   * Lauf-Kopf, und der war fort.
   */
  @Test
  void submit_nachVerdraengungLegtDerselbeLaufJedeKarteGenauEinmalAn() throws Exception {
    Cookie owner = session("nr-wdh-owner@example.com", PlatformRole.USER);
    long projectId = projectOf("nr-wdh-owner@example.com", "nr-wdh-admin@example.com");
    String lauf = run(ERSTER, item(721, "Persistenz", "GREEN", null));

    submit(owner, projectId, lauf).andExpect(status().isOk());
    jdbc.update("DELETE FROM night_run WHERE project_id = ?", projectId);
    submit(owner, projectId, lauf)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].created").value(true));

    assertThat(paketeDerKarte(projectId, 721)).isEqualTo(1);
  }

  private long paketeDerKarte(long projectId, int cardNumber) {
    Long anzahl =
        jdbc.queryForObject(
            "SELECT count(*) FROM night_run_item WHERE project_id = ? AND card_number = ?",
            Long.class,
            projectId,
            cardNumber);
    return anzahl == null ? 0L : anzahl;
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

  private static String runMitKosten(String startedAt, String kosten, String items) {
    return """
        {"startedAt":"%s","mode":"CHAIN","durationMs":1234,"processedCount":1,
         "skippedCount":0,"unparsedCount":0,"usage":{"costUsd":%s},"items":[%s]}"""
        .formatted(startedAt, kosten, items);
  }

  private static String itemMitKosten(int cardNumber, String kosten) {
    return """
        {"cardNumber":%d,"title":"Paket","state":"GREEN","excerpt":"Auszug",
         "usage":{"costUsd":%s}}"""
        .formatted(cardNumber, kosten);
  }

  private NightRun einzigerLauf(long projectId) {
    List<NightRun> gefunden = runs.findByProjectOrderByStartedAtDesc(projectId);
    assertThat(gefunden).hasSize(1);
    return gefunden.getFirst();
  }

  private NightRunItem einzigesPaket(NightRun lauf) {
    List<NightRunItem> pakete = runs.findItemsByRunIds(List.of(lauf.id()));
    assertThat(pakete).hasSize(1);
    return pakete.getFirst();
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
