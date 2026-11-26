package org.mwolff.manban.codeanalyse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Die Platte „Codeanalyse“ von der Meldung bis zur gelesenen Woche (Issue #2279, Plan #2272 E3, E5,
 * E6, E9, fachlich #2271): Meldungen über {@code POST /api/kanban/code-analysis-measurements} mit
 * board-gebundenem Token, Lesen über {@code GET /api/boards/{boardId}/dashboard/code-analysis} mit
 * der Sitzung eines Projektmitglieds, gegen die echte Datenbank.
 *
 * <p>„Jetzt“ ist ein Mittwoch 12:00 UTC: Vor jedem Fall rückt die stellbare Uhr auf den nächsten
 * solchen Zeitpunkt vor. Die Uhr des geteilten Testkontexts läuft nur vorwärts, darum ist es nicht
 * der feste Mittwoch 2026-11-25 aus dem Paket, sondern derselbe Wochentag mit derselben Lage zu den
 * Wochengrenzen. Die laufende Kalenderwoche ist die letzte der zwölf Wochen der Antwort.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(StellbareUhrConfig.class)
class CodeanalyseSzenarienIT extends AbstractIntegrationTest {

  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String MELDEN = "/api/kanban/code-analysis-measurements";
  private static final String LAUFEND = "$.weeks[11]";
  private static final double GENAU = 1e-9;

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock uhr;

  /** Mittwoch 12:00 UTC. */
  private Instant jetzt;

  /** Montag 00:00 UTC der laufenden Kalenderwoche. */
  private Instant wochenbeginn;

  @BeforeEach
  void aufMittwochMittagStellen() {
    ZonedDateTime vorher = uhr.instant().atZone(ZoneOffset.UTC);
    ZonedDateTime mittwoch =
        vorher
            .with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
            .truncatedTo(ChronoUnit.DAYS)
            .withHour(12);
    if (mittwoch.isBefore(vorher)) {
      mittwoch = mittwoch.plusWeeks(1);
    }
    uhr.advance(Duration.between(vorher, mittwoch));
    jetzt = mittwoch.toInstant();
    wochenbeginn = mittwoch.truncatedTo(ChronoUnit.DAYS).minusDays(2).toInstant();
  }

  @Test
  @DisplayName("Szenario: Die Prüfungen fangen Funde ab")
  void diePruefungenFangenFundeAb() throws Exception {
    Projekt projekt = projekt("ca-abfangen");
    zaehlend(
        projekt,
        "stand-1",
        jetzt.minus(Duration.ofHours(2)),
        new Stand(10, 2, 8, 2000),
        new Stand(2, 0, 2, 2200));

    lies(projekt)
        .andExpect(jsonPath(LAUFEND + ".before.findings").value(20))
        .andExpect(jsonPath(LAUFEND + ".before.changedLines").value(2000))
        .andExpect(
            jsonPath(LAUFEND + ".before.findingsDensity", closeTo(10.0, GENAU), Double.class))
        .andExpect(jsonPath(LAUFEND + ".before.bugsDensity", closeTo(5.0, GENAU), Double.class))
        .andExpect(
            jsonPath(LAUFEND + ".before.vulnerabilitiesDensity", closeTo(1.0, GENAU), Double.class))
        .andExpect(
            jsonPath(LAUFEND + ".before.codeSmellsDensity", closeTo(4.0, GENAU), Double.class))
        .andExpect(jsonPath(LAUFEND + ".final.findings").value(4))
        .andExpect(jsonPath(LAUFEND + ".final.changedLines").value(2200))
        .andExpect(jsonPath(LAUFEND + ".final.findingsDensity", closeTo(1.8, 0.05), Double.class))
        .andExpect(jsonPath(LAUFEND + ".reductionPercent", closeTo(80.0, GENAU), Double.class));
  }

  @Test
  @DisplayName("Szenario: Zwei Messungen in einer Woche")
  void zweiMessungenInEinerWoche() throws Exception {
    Projekt projekt = projekt("ca-zwei");
    zaehlend(
        projekt,
        "stand-1",
        jetzt.minus(Duration.ofHours(30)),
        new Stand(10, 0, 0, 1000),
        new Stand(0, 0, 0, 1000));
    zaehlend(
        projekt,
        "stand-2",
        jetzt.minus(Duration.ofHours(3)),
        new Stand(0, 0, 0, 9000),
        new Stand(0, 0, 0, 9000));

    lies(projekt)
        .andExpect(jsonPath(LAUFEND + ".before.findings").value(10))
        .andExpect(jsonPath(LAUFEND + ".before.changedLines").value(10_000))
        .andExpect(jsonPath(LAUFEND + ".before.findingsDensity", closeTo(1.0, GENAU), Double.class))
        .andExpect(jsonPath(LAUFEND + ".countingMeasurements").value(2));
  }

  @Test
  @DisplayName("Szenario: Mehr Funde nach den Ausbesserungen")
  void mehrFundeNachDenAusbesserungen() throws Exception {
    Projekt projekt = projekt("ca-mehr");
    zaehlend(
        projekt,
        "stand-1",
        jetzt.minus(Duration.ofHours(2)),
        new Stand(3, 0, 0, 500),
        new Stand(4, 0, 0, 500));

    lies(projekt)
        .andExpect(
            jsonPath(LAUFEND + ".reductionPercent", closeTo(-100.0 / 3, 1e-6), Double.class));
  }

  @Test
  @DisplayName("Szenario: Eine Messung zählt nicht")
  void eineMessungZaehltNicht() throws Exception {
    Projekt projekt = projekt("ca-nicht");
    nichtZaehlend(projekt, "stand-1", jetzt.minus(Duration.ofHours(2)), "cards-removed");

    lies(projekt)
        .andExpect(jsonPath(LAUFEND + ".countingMeasurements").value(0))
        .andExpect(jsonPath(LAUFEND + ".before").value(nullValue()))
        .andExpect(jsonPath(LAUFEND + ".final").value(nullValue()))
        .andExpect(jsonPath(LAUFEND + ".notCounting", hasSize(1)))
        .andExpect(jsonPath(LAUFEND + ".notCounting[0].reason").value("cards-removed"))
        .andExpect(jsonPath(LAUFEND + ".notCounting[0].count").value(1));
  }

  @Test
  @DisplayName("Szenario: Die Meldung kommt spät")
  void dieMeldungKommtSpaet() throws Exception {
    Projekt projekt = projekt("ca-spaet");
    Instant naechsterMontag = wochenbeginn.plus(Duration.ofDays(7));
    uhr.advance(Duration.between(jetzt, naechsterMontag.plus(Duration.ofHours(8))));
    Instant sonntagAbend = naechsterMontag.minus(Duration.ofHours(2));

    lies(projekt).andExpect(jsonPath("$.weeks[10].countingMeasurements").value(0));
    zaehlend(projekt, "stand-1", sonntagAbend, new Stand(5, 0, 0, 1000), new Stand(1, 0, 0, 1000));

    lies(projekt)
        .andExpect(jsonPath("$.weeks[10].weekStart").value(wochenbeginn.toString()))
        .andExpect(jsonPath("$.weeks[10].countingMeasurements").value(1))
        .andExpect(jsonPath("$.weeks[10].before.findings").value(5))
        .andExpect(jsonPath(LAUFEND + ".weekStart").value(naechsterMontag.toString()))
        .andExpect(jsonPath(LAUFEND + ".countingMeasurements").value(0))
        .andExpect(jsonPath(LAUFEND + ".before").value(nullValue()))
        .andExpect(jsonPath(LAUFEND + ".notCounting", hasSize(0)));
  }

  @Test
  @DisplayName("Szenario: Doppelte Meldung")
  void doppelteMeldung() throws Exception {
    Projekt projekt = projekt("ca-doppelt");
    Instant veroeffentlicht = jetzt.minus(Duration.ofHours(2));
    String erste =
        zaehlend(
            projekt,
            "stand-1",
            veroeffentlicht,
            new Stand(8, 0, 0, 1000),
            new Stand(2, 0, 0, 1000));
    String gelesen = inhalt(lies(projekt));

    String zweite = melde(projekt, "stand-1", veroeffentlicht, false, "incomplete", 9, null, null);

    assertThat(json.readTree(zweite)).isEqualTo(json.readTree(erste));
    assertThat(json.readTree(erste).get("counts").asBoolean()).isTrue();
    String erneut = inhalt(lies(projekt));
    assertThat(json.readTree(erneut)).isEqualTo(json.readTree(gelesen));
    lies(projekt)
        .andExpect(jsonPath(LAUFEND + ".countingMeasurements").value(1))
        .andExpect(jsonPath(LAUFEND + ".before.findings").value(8))
        .andExpect(jsonPath(LAUFEND + ".notCounting", hasSize(0)));
  }

  @Test
  @DisplayName("Szenario: Keine geänderten Zeilen")
  void keineGeaendertenZeilen() throws Exception {
    Projekt projekt = projekt("ca-nullzeilen");
    zaehlend(
        projekt,
        "stand-1",
        jetzt.minus(Duration.ofHours(2)),
        new Stand(2, 0, 0, 0),
        new Stand(0, 0, 0, 0));

    lies(projekt)
        .andExpect(jsonPath(LAUFEND + ".before.findings").value(2))
        .andExpect(jsonPath(LAUFEND + ".before.changedLines").value(0))
        .andExpect(jsonPath(LAUFEND + ".before.findingsDensity").value(nullValue()))
        .andExpect(jsonPath(LAUFEND + ".before.bugsDensity").value(nullValue()))
        .andExpect(jsonPath(LAUFEND + ".before.vulnerabilitiesDensity").value(nullValue()))
        .andExpect(jsonPath(LAUFEND + ".before.codeSmellsDensity").value(nullValue()))
        .andExpect(jsonPath(LAUFEND + ".final.findingsDensity").value(nullValue()));
  }

  @Test
  @DisplayName("Szenario: Kein Fund vor der Codeanalyse")
  void keinFundVorDerCodeanalyse() throws Exception {
    Projekt projekt = projekt("ca-keinfund");
    zaehlend(
        projekt,
        "stand-1",
        jetzt.minus(Duration.ofHours(2)),
        new Stand(0, 0, 0, 400),
        new Stand(0, 1, 0, 400));

    lies(projekt)
        .andExpect(jsonPath(LAUFEND + ".before.findings").value(0))
        .andExpect(jsonPath(LAUFEND + ".final.findings").value(1))
        .andExpect(jsonPath(LAUFEND + ".reductionPercent").value(nullValue()));
  }

  @Test
  @DisplayName("Szenario: Eine Woche ohne zählende Messung")
  void eineWocheOhneZaehlendeMessung() throws Exception {
    Projekt projekt = projekt("ca-luecke");
    zaehlend(
        projekt,
        "stand-alt",
        jetzt.minus(Duration.ofDays(42)),
        new Stand(4, 0, 0, 1000),
        new Stand(1, 0, 0, 1000));
    nichtZaehlend(projekt, "stand-mitte", jetzt.minus(Duration.ofDays(21)), "missing-final");
    zaehlend(
        projekt,
        "stand-neu",
        jetzt.minus(Duration.ofHours(2)),
        new Stand(3, 0, 0, 1000),
        new Stand(1, 0, 0, 1000));

    lies(projekt)
        .andExpect(jsonPath("$.weeks[5].countingMeasurements").value(1))
        .andExpect(jsonPath("$.weeks[8].countingMeasurements").value(0))
        .andExpect(jsonPath("$.weeks[8].before").value(nullValue()))
        .andExpect(jsonPath("$.weeks[8].final").value(nullValue()))
        .andExpect(jsonPath("$.weeks[8].cards").value(nullValue()))
        .andExpect(jsonPath("$.weeks[8].reductionPercent").value(nullValue()))
        .andExpect(jsonPath("$.weeks[8].notCounting[0].reason").value("missing-final"))
        .andExpect(jsonPath("$.weeks[8].notCounting[0].count").value(1))
        .andExpect(jsonPath(LAUFEND + ".countingMeasurements").value(1));
  }

  @Test
  @DisplayName("Szenario: Ein Projekt ohne Messung")
  void einProjektOhneMessung() throws Exception {
    Projekt projekt = projekt("ca-leer");

    lies(projekt)
        .andExpect(jsonPath("$.hasMeasurements").value(false))
        .andExpect(jsonPath("$.dataSince").value(nullValue()))
        .andExpect(jsonPath("$.weeks", hasSize(12)))
        .andExpect(jsonPath("$.weeks[*].countingMeasurements", everyItem(is(0))))
        .andExpect(jsonPath("$.weeks[*].before", everyItem(nullValue())))
        .andExpect(jsonPath("$.weeks[*].final", everyItem(nullValue())))
        .andExpect(jsonPath("$.weeks[*].cards", everyItem(nullValue())))
        .andExpect(jsonPath("$.weeks[*].reductionPercent", everyItem(nullValue())))
        .andExpect(jsonPath("$.weeks[*].notCounting[*]", hasSize(0)));
  }

  @Test
  @DisplayName("Szenario: Messungen bleiben im eigenen Projekt")
  void messungenBleibenImEigenenProjekt() throws Exception {
    Projekt eins = projekt("ca-eins");
    Projekt zwei = projekt("ca-zwei-projekt");
    Instant veroeffentlicht = jetzt.minus(Duration.ofHours(2));
    zaehlend(
        eins,
        "gleicher-stand",
        veroeffentlicht,
        new Stand(6, 0, 0, 1000),
        new Stand(3, 0, 0, 1000));
    zaehlend(
        zwei,
        "gleicher-stand",
        veroeffentlicht,
        new Stand(40, 0, 0, 2000),
        new Stand(10, 0, 0, 2000));

    lies(eins)
        .andExpect(jsonPath(LAUFEND + ".countingMeasurements").value(1))
        .andExpect(jsonPath(LAUFEND + ".before.findings").value(6))
        .andExpect(jsonPath(LAUFEND + ".final.findings").value(3));
    lies(zwei)
        .andExpect(jsonPath(LAUFEND + ".countingMeasurements").value(1))
        .andExpect(jsonPath(LAUFEND + ".before.findings").value(40))
        .andExpect(jsonPath(LAUFEND + ".final.findings").value(10));

    Cookie admin = anmeldung.anmelden("ca-frei-admin@example.com", PlatformRole.ADMIN);
    String ungebunden = tokenPlaintext(admin, "{\"name\":\"Ungebunden\"}");
    mvc.perform(
            post(MELDEN)
                .header(TOKEN_HEADER, ungebunden)
                .contentType("application/json")
                .content(
                    rumpf(
                        "frei-stand",
                        veroeffentlicht,
                        true,
                        null,
                        1,
                        new Stand(1, 0, 0, 10),
                        new Stand(0, 0, 0, 10))))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM code_analysis_measurement"
                    + " WHERE published_commit = 'frei-stand'",
                Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM code_analysis_measurement", Long.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("Szenario: Die Daten beginnen im Fenster")
  void dieDatenBeginnenImFenster() throws Exception {
    Projekt projekt = projekt("ca-seit");
    Instant ersteVeroeffentlichung =
        wochenbeginn.minus(Duration.ofDays(21)).plus(Duration.ofHours(10));
    zaehlend(
        projekt,
        "stand-1",
        ersteVeroeffentlichung,
        new Stand(2, 0, 0, 1000),
        new Stand(1, 0, 0, 1000));

    lies(projekt)
        .andExpect(jsonPath("$.hasMeasurements").value(true))
        .andExpect(jsonPath("$.dataSince").value(ersteVeroeffentlichung.toString()))
        .andExpect(jsonPath("$.weeks[8].countingMeasurements").value(1))
        .andExpect(jsonPath("$.weeks[0:8].before", hasSize(8)))
        .andExpect(jsonPath("$.weeks[0:8].before", everyItem(nullValue())))
        .andExpect(jsonPath("$.weeks[0:8].countingMeasurements", everyItem(is(0))));
  }

  @Test
  void nachgemeldeteAeltereMessungVerschiebtDenBeginn() throws Exception {
    Projekt projekt = projekt("ca-seit-nach");
    Instant erste = wochenbeginn.minus(Duration.ofDays(21)).plus(Duration.ofHours(10));
    zaehlend(projekt, "stand-1", erste, new Stand(2, 0, 0, 1000), new Stand(1, 0, 0, 1000));
    lies(projekt).andExpect(jsonPath("$.dataSince").value(erste.toString()));

    Instant frueher = wochenbeginn.minus(Duration.ofDays(49)).plus(Duration.ofHours(9));
    nichtZaehlend(projekt, "stand-0", frueher, "no-measurement");

    lies(projekt)
        .andExpect(jsonPath("$.dataSince").value(frueher.toString()))
        .andExpect(jsonPath("$.weeks[4].notCounting[0].reason").value("no-measurement"));
  }

  // --- Hilfen --------------------------------------------------------------------------------

  private record Projekt(long id, long boardId, Cookie owner, String token) {}

  /** Ein Zustand einer Messung: Funde je Art und geänderte Zeilen. */
  private record Stand(int bugs, int vulnerabilities, int codeSmells, int changedLines) {}

  private String zaehlend(
      Projekt projekt, String commit, Instant publishedAt, Stand vorher, Stand endzustand)
      throws Exception {
    return melde(projekt, commit, publishedAt, true, null, 2, vorher, endzustand);
  }

  private void nichtZaehlend(Projekt projekt, String commit, Instant publishedAt, String grund)
      throws Exception {
    melde(projekt, commit, publishedAt, false, grund, 1, null, null);
  }

  /** Meldet eine Messung mit dem Board-Token des Projekts und gibt die Antwort zurück. */
  private String melde(
      Projekt projekt,
      String commit,
      Instant publishedAt,
      boolean counts,
      @Nullable String reason,
      int cards,
      @Nullable Stand vorher,
      @Nullable Stand endzustand)
      throws Exception {
    return inhalt(
        mvc.perform(
                post(MELDEN)
                    .header(TOKEN_HEADER, projekt.token())
                    .contentType("application/json")
                    .content(rumpf(commit, publishedAt, counts, reason, cards, vorher, endzustand)))
            .andExpect(status().isOk()));
  }

  private String rumpf(
      String commit,
      Instant publishedAt,
      boolean counts,
      @Nullable String reason,
      int cards,
      @Nullable Stand vorher,
      @Nullable Stand endzustand) {
    ObjectNode rumpf = json.createObjectNode();
    rumpf.put("publishedAt", publishedAt.toString());
    rumpf.put("publishedCommit", commit);
    rumpf.put("baseCommit", "basis-" + commit);
    rumpf.put("kitVersion", "3.21.1");
    rumpf.put("counts", counts);
    if (reason != null) {
      rumpf.put("reason", reason);
    }
    rumpf.put("cards", cards);
    if (vorher != null) {
      rumpf.set("before", zustand(vorher));
    }
    if (endzustand != null) {
      rumpf.set("final", zustand(endzustand));
    }
    return rumpf.toString();
  }

  private ObjectNode zustand(Stand stand) {
    return json.createObjectNode()
        .put("bugs", stand.bugs())
        .put("vulnerabilities", stand.vulnerabilities())
        .put("codeSmells", stand.codeSmells())
        .put("changedLines", stand.changedLines());
  }

  private ResultActions lies(Projekt projekt) throws Exception {
    return mvc.perform(
            get("/api/boards/" + projekt.boardId() + "/dashboard/code-analysis")
                .cookie(projekt.owner()))
        .andExpect(status().isOk());
  }

  private static String inhalt(ResultActions antwort) throws Exception {
    return antwort.andReturn().getResponse().getContentAsString();
  }

  /**
   * Ein Projekt mit einem Board, dessen Owner ein gewöhnlicher Nutzer ist, und einem an dieses
   * Board gebundenen Token des Owners.
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
    String token =
        tokenPlaintext(
            owner,
            "{\"name\":\"Codeanalyse\",\"projectId\":%d,\"boardId\":%d}"
                .formatted(projectId, boardId));
    return new Projekt(projectId, boardId, owner, token);
  }

  private String tokenPlaintext(Cookie sitzung, String anfrage) throws Exception {
    JsonNode token =
        json.readTree(
            inhalt(
                mvc.perform(
                        post("/api/access-tokens")
                            .cookie(sitzung)
                            .contentType("application/json")
                            .content(anfrage))
                    .andExpect(status().isCreated())));
    return token.get("plaintext").asText();
  }

  private long id(ResultActions angelegt) throws Exception {
    return json.readTree(inhalt(angelegt.andExpect(status().isCreated()))).get("id").asLong();
  }
}
