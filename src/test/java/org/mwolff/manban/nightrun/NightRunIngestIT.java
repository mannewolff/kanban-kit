package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.nightrun.application.NightRunRepository;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Der Weg ohne Menschen: {@code POST /api/kanban/night-runs} mit projektgebundenem Token (Issue
 * #947, fachlich #927).
 *
 * <p>Jeder Fall läuft <b>ohne Sitzungs-Cookie</b> — genau darum geht es. Die Sitzung dient hier nur
 * dazu, Projekt und Token überhaupt anzulegen; der Aufruf selbst trägt allein {@code
 * X-Kanban-Token}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NightRunIngestIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String PFAD = "/api/kanban/night-runs";
  private static final String START = "2026-09-16T22:31:00Z";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private NightRunRepository runs;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private ProjectRepository projects;
  @Autowired private JdbcTemplate jdbc;

  private static String meldung(boolean complete, String... pakete) {
    return """
        {"startedAt":"%s","mode":"CHAIN","durationMs":4320000,"processedCount":%d,
         "skippedCount":0,"unparsedCount":0,"complete":%s,
         "usage":{"costUsd":8.032575,"inputTokens":148,"outputTokens":62411,
                  "cachedInputTokens":8883160},
         "items":[%s]}"""
        .formatted(START, pakete.length, complete, String.join(",", pakete));
  }

  /**
   * Eine gemeldete interaktive Sitzung: Laufart <b>und</b> Gattung {@code INTERACTIVE} (E23), sonst
   * derselbe Rumpf wie beim Nachtlauf — die Erweiterung ist additiv (Issue #1012).
   */
  private static String sitzung(String startedAt, String... pakete) {
    return """
        {"startedAt":"%s","mode":"INTERACTIVE","kind":"INTERACTIVE","durationMs":180000,
         "processedCount":%d,"skippedCount":0,"unparsedCount":0,"complete":true,
         "items":[%s]}"""
        .formatted(startedAt, pakete.length, String.join(",", pakete));
  }

  private static String paket(int cardNumber) {
    return """
        {"cardNumber":%d,"title":"Paket %d","state":"GREEN","durationMs":1122000,
         "usage":{"costUsd":0.94,"inputTokens":412000}}"""
        .formatted(cardNumber, cardNumber);
  }

  private static String rotesPaket(int cardNumber) {
    return """
        {"cardNumber":%d,"title":"Paket %d","state":"RED","errorClass":"CHECKS_RED"}"""
        .formatted(cardNumber, cardNumber);
  }

  @Test
  void einlieferungOhneMenschLegtDenLaufAnUndKennzeichnetIhnMaschinell() throws Exception {
    Aufbau aufbau = aufbau("ingest-a");

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(meldung(false, paket(917))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("CREATED"))
        .andExpect(jsonPath("$.startedAt").value(START));

    NightRun gespeichert = einzigerLauf(aufbau.projectId());
    assertThat(gespeichert.origin()).isEqualTo(NightRunOrigin.TOKEN);
    assertThat(gespeichert.tokenName()).isEqualTo(aufbau.tokenName());
    assertThat(gespeichert.updatedAt()).isNotNull();
    assertThat(gespeichert.complete()).isFalse();
    assertThat(gespeichert.usage()).isNotNull();
    assertThat(gespeichert.usage().cachedInputTokens()).isEqualTo(8_883_160L);
  }

  @Test
  void einWiderrufenesTokenWirdAbgewiesenUndSchreibtNichts() throws Exception {
    Aufbau aufbau = aufbau("ingest-widerruf");
    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(meldung(true, paket(917))))
        .andExpect(status().isOk());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/access-tokens/" + aufbau.tokenId())
                .cookie(aufbau.session()))
        .andExpect(status().isNoContent());

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(meldung(true, paket(917)).replace(START, "2026-09-17T22:31:00Z")))
        .andExpect(status().isUnauthorized());

    assertThat(nachtlaeufe(aufbau.projectId())).hasSize(1);
  }

  @Test
  void einUngebundenesTokenWirdAbgewiesen() throws Exception {
    Cookie session = session("ingest-frei@example.com", PlatformRole.ADMIN);
    String frei = plaintext(session, "{\"name\":\"Ungebunden\"}");

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, frei)
                .contentType("application/json")
                .content(meldung(true)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void einFremdGebundenesTokenSchreibtNurInSeinEigenesProjekt() throws Exception {
    Aufbau eigen = aufbau("ingest-eigen");
    Aufbau fremd = aufbau("ingest-fremd");

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, fremd.token())
                .contentType("application/json")
                .content(meldung(true, paket(917))))
        .andExpect(status().isOk());

    assertThat(nachtlaeufe(fremd.projectId())).hasSize(1);
    assertThat(nachtlaeufe(eigen.projectId())).isEmpty();
  }

  /**
   * Ein Token darf nie mehr als sein Besitzer. Das Mitglied ist im Projekt, aber nur MEMBER — der
   * Endpunkt verlangt OWNER, also wird die Meldung abgewiesen, obwohl das Token gueltig und an
   * genau dieses Projekt gebunden ist.
   */
  @Test
  void einTokenOhneOwnerRolleWirdAbgewiesen() throws Exception {
    Cookie admin = session("ingest-owner@example.com", PlatformRole.ADMIN);
    Cookie mitglied = session("ingest-mitglied@example.com", PlatformRole.USER);
    long projectId = createProject(admin, "Rollen-Projekt", "ingest-owner@example.com");
    long boardId = createBoard(admin, projectId);
    long mitgliedId = users.findByEmail("ingest-mitglied@example.com").orElseThrow().requireId();
    // Direkt angelegt statt ueber eine Einladung: Gebraucht wird allein die Rolle, nicht der Weg
    // dorthin — und der Einladungsablauf ist Gegenstand eigener Tests.
    memberships.save(
        new ProjectMembership(null, projectId, mitgliedId, ProjectRole.MEMBER, Instant.now()));
    String mitgliedToken =
        plaintext(
            mitglied,
            "{\"name\":\"Mitglied\",\"projectId\":%d,\"boardId\":%d}"
                .formatted(projectId, boardId));

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, mitgliedToken)
                .contentType("application/json")
                .content(meldung(true)))
        .andExpect(status().isForbidden());

    assertThat(nachtlaeufe(projectId)).isEmpty();
  }

  /** Der Kern des fortschreibenden Meldens: erst unvollstaendig, dann vollstaendig. */
  @Test
  void einUnvollstaendigerLaufStehtAmBoardUndWirdSpaeterVollstaendig() throws Exception {
    Aufbau aufbau = aufbau("ingest-teil");

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(meldung(false, paket(917))))
        .andExpect(status().isOk());
    assertThat(einzigerLauf(aufbau.projectId()).complete()).isFalse();

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(meldung(true, paket(917), paket(918))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("REPLACED"));

    NightRun nachher = einzigerLauf(aufbau.projectId());
    assertThat(nachher.complete()).isTrue();
    assertThat(runs.findItemsByRunIds(List.of(nachher.requireId()))).hasSize(2);
  }

  @Test
  void eineUnveraenderteWiederholungVerdoppeltNichts() throws Exception {
    Aufbau aufbau = aufbau("ingest-wdh");
    String gleich = meldung(true, paket(917));

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(gleich))
        .andExpect(jsonPath("$.outcome").value("CREATED"));
    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(gleich))
        .andExpect(jsonPath("$.outcome").value("REPLACED"));

    NightRun nachher = einzigerLauf(aufbau.projectId());
    assertThat(runs.findItemsByRunIds(List.of(nachher.requireId()))).hasSize(1);
    assertThat(nachher.usage().inputTokens()).isEqualTo(148L);
  }

  /**
   * Der gemeldete Stand ist vollstaendig: Ein Paket, das die zweite Meldung nicht fuehrt, ist fort.
   */
  @Test
  void eineAbweichendeZweitmeldungErsetztDenStandVollstaendig() throws Exception {
    Aufbau aufbau = aufbau("ingest-ersetzt");

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(meldung(false, paket(917), paket(918))))
        .andExpect(status().isOk());

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(meldung(true, paket(919))))
        .andExpect(status().isOk());

    NightRun nachher = einzigerLauf(aufbau.projectId());
    assertThat(runs.findItemsByRunIds(List.of(nachher.requireId())))
        .extracting(item -> item.cardNumber())
        .containsExactly(919);
  }

  /** Derselbe Fall ueber den Token-Weg (Issue #965): {@code upsert} sieht keinen Lauf mehr. */
  @Test
  void nachVerdraengungLegtDieselbeMeldungJedeKarteGenauEinmalAn() throws Exception {
    Aufbau aufbau = aufbau("ingest-verdraengt");
    String gleich = meldung(true, paket(917));

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(gleich))
        .andExpect(status().isOk());
    jdbc.update("DELETE FROM night_run WHERE project_id = ?", aufbau.projectId());
    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(gleich))
        .andExpect(jsonPath("$.outcome").value("CREATED"));

    Long anzahl =
        jdbc.queryForObject(
            "SELECT count(*) FROM night_run_item WHERE project_id = ? AND card_number = ?",
            Long.class,
            aufbau.projectId(),
            917);
    assertThat(anzahl).isEqualTo(1L);
  }

  @Test
  void zuVielePaketeWerdenAbgewiesen() throws Exception {
    Aufbau aufbau = aufbau("ingest-grenze");
    String[] zuViele = new String[201];
    for (int i = 0; i < zuViele.length; i++) {
      zuViele[i] = paket(1000 + i);
    }

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(meldung(true, zuViele)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors").exists());
  }

  // --- Die Gattung an der Einlieferung (Issue #1012) -----------------------------------------

  /**
   * Der Kern des Pakets: Eine Sitzung kommt ueber dieselbe Strecke herein wie ein Nachtlauf und
   * liegt danach mit Laufart <b>und</b> Gattung {@code INTERACTIVE} in {@code night_run}.
   */
  @Test
  void eineSitzungWirdMitGattungUndLaufartInteractiveAngenommen() throws Exception {
    Aufbau aufbau = aufbau("ingest-sitzung");

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(sitzung(START, paket(1012))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("CREATED"));

    List<NightRun> sitzungen = sitzungen(aufbau.projectId());
    assertThat(sitzungen).hasSize(1);
    assertThat(sitzungen.getFirst().mode()).isEqualTo(NightRunMode.INTERACTIVE);
    assertThat(sitzungen.getFirst().kind()).isEqualTo(NightRunKind.INTERACTIVE);
    assertThat(runs.findItemsByRunIds(List.of(sitzungen.getFirst().requireId())))
        .extracting(NightRunItem::kind)
        .containsExactly(NightRunKind.INTERACTIVE);
  }

  /**
   * Die Gegenprobe zur Additivitaet: Eine aeltere Kit-Kopie kennt das Feld {@code kind} nicht und
   * meldet unveraendert weiter — ihre Meldung ist ein Nachtlauf.
   */
  @Test
  void eineMeldungOhneGattungLiegtAlsNachtlaufVor() throws Exception {
    Aufbau aufbau = aufbau("ingest-ohne-gattung");

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(meldung(true, paket(917))))
        .andExpect(status().isOk());

    assertThat(einzigerLauf(aufbau.projectId()).kind()).isEqualTo(NightRunKind.NIGHT);
    assertThat(sitzungen(aufbau.projectId())).isEmpty();
  }

  /**
   * Der Erfassungsbeginn (E18) steht nach der ersten Sitzung und bewegt sich danach nie wieder —
   * weder nach vorn noch nach hinten. Aus der aeltesten vorhandenen Sitzung liesse er sich nicht
   * ableiten: Die wandert mit dem Ringpuffer nach vorn.
   */
  @Test
  void dieErsteSitzungSetztDenErfassungsbeginn_spaetereLassenIhnStehen() throws Exception {
    Aufbau aufbau = aufbau("ingest-beginn");
    Instant erste = Instant.parse("2026-09-16T10:00:00Z");
    Instant frueher = Instant.parse("2026-09-15T10:00:00Z");
    Instant spaeter = Instant.parse("2026-09-17T10:00:00Z");

    melde(aufbau, sitzung(erste.toString()));
    assertThat(erfassungsbeginn(aufbau.projectId())).isEqualTo(erste);

    melde(aufbau, sitzung(frueher.toString()));
    assertThat(erfassungsbeginn(aufbau.projectId())).isEqualTo(erste);

    melde(aufbau, sitzung(spaeter.toString()));
    assertThat(erfassungsbeginn(aufbau.projectId())).isEqualTo(erste);
  }

  /** Ein Nachtlauf sagt ueber die interaktive Nutzung nichts aus und setzt den Wert nicht. */
  @Test
  void einNachtlaufSetztDenErfassungsbeginnNicht() throws Exception {
    Aufbau aufbau = aufbau("ingest-beginn-nacht");

    melde(aufbau, meldung(true, paket(917)));

    assertThat(erfassungsbeginn(aufbau.projectId())).isNull();
  }

  /**
   * Das Nicht-Ziel: Die Nachtlauf-Seite und die Platte „Abbruchgruende" liefern dieselben
   * Ergebnisse wie vor der Aenderung, auch wenn im selben Projekt Sitzungen liegen.
   */
  @Test
  void dieNachtlaufSeiteSiehtKeineSitzungen() throws Exception {
    Aufbau aufbau = aufbau("ingest-nichtziel");

    melde(aufbau, meldung(true, rotesPaket(917)));
    melde(aufbau, sitzung("2026-09-17T10:00:00Z", rotesPaket(1012)));

    assertThat(nachtlaeufe(aufbau.projectId()))
        .extracting(NightRun::startedAt)
        .containsExactly(Instant.parse(START));
    assertThat(runs.countRunsByErrorClass(aufbau.projectId(), NightRunKind.NIGHT))
        .containsExactly(java.util.Map.entry(NightRunErrorClass.CHECKS_RED, 1L));
  }

  /** Die Idempotenz gilt fuer die Sitzung wie fuer den Lauf: derselbe Start, ein Eintrag. */
  @Test
  void eineWiederholteSitzungErsetztDieErsteUndVerdoppeltNichts() throws Exception {
    Aufbau aufbau = aufbau("ingest-sitzung-wdh");
    String gleich = sitzung(START, paket(1012));

    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(gleich))
        .andExpect(jsonPath("$.outcome").value("CREATED"));
    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(gleich))
        .andExpect(jsonPath("$.outcome").value("REPLACED"));

    List<NightRun> sitzungen = sitzungen(aufbau.projectId());
    assertThat(sitzungen).hasSize(1);
    assertThat(runs.findItemsByRunIds(List.of(sitzungen.getFirst().requireId()))).hasSize(1);
  }

  // --- Aufbau -------------------------------------------------------------------------------

  private void melde(Aufbau aufbau, String rumpf) throws Exception {
    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(rumpf))
        .andExpect(status().isOk());
  }

  private List<NightRun> sitzungen(long projectId) {
    return runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.INTERACTIVE);
  }

  private List<NightRun> nachtlaeufe(long projectId) {
    return runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT);
  }

  private Instant erfassungsbeginn(long projectId) {
    return projects.findById(projectId).orElseThrow().interactiveUsageSince();
  }

  private record Aufbau(
      Cookie session, long projectId, String token, long tokenId, String tokenName) {}

  private Aufbau aufbau(String kennung) throws Exception {
    Cookie session = session(kennung + "@example.com", PlatformRole.ADMIN);
    long projectId = createProject(session, kennung + "-Projekt", kennung + "@example.com");
    long boardId = createBoard(session, projectId);
    String name = kennung + "-Token";
    JsonNode angelegt =
        json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(session)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"%s\",\"projectId\":%d,\"boardId\":%d}"
                                .formatted(name, projectId, boardId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return new Aufbau(
        session, projectId, angelegt.get("plaintext").asText(), angelegt.get("id").asLong(), name);
  }

  private NightRun einzigerLauf(long projectId) {
    List<NightRun> gefunden = nachtlaeufe(projectId);
    assertThat(gefunden).hasSize(1);
    return gefunden.getFirst();
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
    return json.readTree(
            mvc.perform(
                    post("/api/projects")
                        .cookie(admin)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private long createBoard(Cookie session, long projectId) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/boards")
                        .cookie(session)
                        .contentType("application/json")
                        .content("{\"name\":\"Board\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
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
