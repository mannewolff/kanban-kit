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
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

  private static String meldung(boolean complete, String... pakete) {
    return """
        {"startedAt":"%s","mode":"CHAIN","durationMs":4320000,"processedCount":%d,
         "skippedCount":0,"unparsedCount":0,"complete":%s,
         "usage":{"costUsd":8.032575,"inputTokens":148,"outputTokens":62411,
                  "cachedInputTokens":8883160},
         "items":[%s]}"""
        .formatted(START, pakete.length, complete, String.join(",", pakete));
  }

  private static String paket(int cardNumber) {
    return """
        {"cardNumber":%d,"title":"Paket %d","state":"GREEN","durationMs":1122000,
         "usage":{"costUsd":0.94,"inputTokens":412000}}"""
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

    assertThat(runs.findByProjectOrderByStartedAtDesc(aufbau.projectId())).hasSize(1);
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

    assertThat(runs.findByProjectOrderByStartedAtDesc(fremd.projectId())).hasSize(1);
    assertThat(runs.findByProjectOrderByStartedAtDesc(eigen.projectId())).isEmpty();
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

    assertThat(runs.findByProjectOrderByStartedAtDesc(projectId)).isEmpty();
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

  // --- Aufbau -------------------------------------------------------------------------------

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
    List<NightRun> gefunden = runs.findByProjectOrderByStartedAtDesc(projectId);
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
