package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.Laufkennung;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Die Platte „Nacharbeit“ Ende zu Ende (Issue #1757, Plan #1752): {@code GET
 * /api/boards/{boardId}/night-run-rework}.
 *
 * <p>Die Spuren entstehen auf dem Weg des Runners: Das Token meldet zuerst den Lauf über {@code
 * POST /api/kanban/night-runs}, danach weist es sich bei jedem Aufruf mit {@code X-Night-Run} und
 * dem gemeldeten Start aus. Nur so belegt der Test, dass Laufkennung und aufbewahrter Lauf
 * zueinanderfinden (E4).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NightRunReworkIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String AGENT_HEADER = "X-Agent-Model";
  private static final String MODELL = "claude-opus-5-5";
  private static final String JSON = "application/json";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  /**
   * Die Erfassungszeile, die V55 anlegt: {@code resetDatabase} leert auch sie, darum steht sie hier
   * wie in {@code CardLabelEventRepositoryIT} — eine Stunde vor jedem Lauf der Tests.
   */
  @BeforeEach
  void labelErfassungBeginnt() {
    jdbc.update(
        "INSERT INTO card_label_event_capture (since) VALUES (?)",
        Timestamp.from(Instant.now().minus(Duration.ofHours(1))));
  }

  @Test
  @DisplayName("Szenario: Der Runner gibt ein Paket zurück")
  void derRunnerGibtEinPaketZurueck() throws Exception {
    Aufbau a = aufbau("rework-paket");
    Instant lauf = laufMelden(a);
    List<Item> pakete = new ArrayList<>();
    for (int i = 1; i <= 14; i++) {
      Item paket = anlegen(a, "Paket " + i, lauf);
      verschieben(a, paket, "IN_PROGRESS", lauf);
      pakete.add(paket);
    }
    Item zurueck = pakete.get(3);
    verschieben(a, zurueck, "BACKLOG", lauf);
    // Ohne Ausweis ist es keine Spur des Runs — wie Tagesarbeit (AK 3).
    verschieben(a, pakete.get(5), "READY", null);

    JsonNode antwort = nacharbeit(a.session(), a.boardId(), 200);

    assertThat(antwort.get("runsInPeriod").asBoolean()).isTrue();
    JsonNode zeile = antwort.get("packages");
    assertThat(zeile.get("affected").asInt()).isEqualTo(1);
    assertThat(zeile.get("processed").asInt()).isEqualTo(14);
    assertThat(zeile.get("cards")).hasSize(1);
    assertThat(zeile.get("cards").get(0).get("number").asInt()).isEqualTo(zurueck.number());
    assertThat(zeile.get("cards").get(0).get("count").asInt()).isEqualTo(1);
    JsonNode woche = antwort.get("weeks").get(11).get("packages");
    assertThat(woche.get("state").asText()).isEqualTo("QUOTE");
    assertThat(woche.get("affected").asInt()).isEqualTo(1);
    assertThat(woche.get("processed").asInt()).isEqualTo(14);
  }

  @Test
  @DisplayName("Szenario: Der Nachtlauf hält einen Plan an")
  void derNachtlaufHaeltEinenPlanAn() throws Exception {
    Aufbau a = aufbau("rework-plan");
    Instant lauf = laufMelden(a);
    List<Item> dokumente = new ArrayList<>();
    for (int i = 1; i <= 6; i++) {
      Item dokument =
          anlegen(a, (i % 2 == 0 ? "[Plan] Dokument " : "[Fachlich] Dokument ") + i, null);
      kommentieren(a, dokument, lauf);
      dokumente.add(dokument);
    }
    lies(
        post("/api/boards/" + a.boardId() + "/labels")
            .cookie(a.session())
            .contentType(JSON)
            .content("{\"name\":\"kit:clarify\",\"color\":\"#b87333\"}"),
        201);
    Item angehalten = dokumente.get(1);
    mvc.perform(
            mitAgent(post("/api/kanban/items/" + angehalten.id() + "/labels"), a, lauf)
                .contentType(JSON)
                .content("{\"name\":\"kit:clarify\"}"))
        .andExpect(status().isNoContent());

    JsonNode antwort = nacharbeit(a.session(), a.boardId(), 200);

    JsonNode zeile = antwort.get("documents");
    assertThat(zeile.get("affected").asInt()).isEqualTo(1);
    assertThat(zeile.get("processed").asInt()).isEqualTo(6);
    assertThat(zeile.get("cards")).hasSize(1);
    assertThat(zeile.get("cards").get(0).get("number").asInt()).isEqualTo(angehalten.number());
    assertThat(antwort.get("packages").get("processed").asInt()).isZero();
  }

  /**
   * Plan #1752 E16: Der Weg nach In Progress liegt vor dem ältesten Wochenfenster, der Rückzug im
   * jüngsten — der Vorgängerstatus kommt trotzdem an, und der Rückzug zählt.
   */
  @Test
  void einRueckzugZaehltAuchWennDerWegNachInProgressVorDemAeltestenFensterLag() throws Exception {
    Aufbau a = aufbau("rework-alt");
    Instant lauf = laufMelden(a);
    Item paket = anlegen(a, "Altes Paket", null);
    verschieben(a, paket, "IN_PROGRESS", null);
    jdbc.update(
        "UPDATE card_activity SET created_at = ?"
            + " WHERE card_id = ? AND status_after = 'IN_PROGRESS'",
        Timestamp.from(Instant.now().minus(Duration.ofDays(100))),
        paket.id());
    verschieben(a, paket, "BACKLOG", lauf);

    JsonNode zeile = nacharbeit(a.session(), a.boardId(), 200).get("packages");

    assertThat(zeile.get("affected").asInt()).isEqualTo(1);
    assertThat(zeile.get("processed").asInt()).isEqualTo(1);
    assertThat(zeile.get("cards").get(0).get("number").asInt()).isEqualTo(paket.number());
  }

  /** E12 im vollen Kontext: Mitglied ohne Run-Recht 403, Fremder und unbekanntes Board 404. */
  @Test
  void dieRechteDerRunsGeltenAuchAmBoardPfad() throws Exception {
    Aufbau a = aufbau("rework-recht");
    Cookie mitglied = session("rework-recht-m@example.com", PlatformRole.USER);
    long mitgliedId = users.findByEmail("rework-recht-m@example.com").orElseThrow().requireId();
    memberships.save(
        new ProjectMembership(null, a.projectId(), mitgliedId, ProjectRole.MEMBER, Instant.now()));
    Cookie fremd = session("rework-recht-x@example.com", PlatformRole.USER);

    nacharbeit(mitglied, a.boardId(), 403);
    nacharbeit(fremd, a.boardId(), 404);
    nacharbeit(a.session(), Long.MAX_VALUE, 404);
  }

  // --- Aufbau ---------------------------------------------------------------------------------

  private record Aufbau(Cookie session, long projectId, long boardId, String token) {}

  private record Item(long id, int number) {}

  private record Text(String body) {}

  private Aufbau aufbau(String kennung) throws Exception {
    String email = kennung + "@example.com";
    Cookie session = session(email, PlatformRole.ADMIN);
    long projectId =
        lies(
                post("/api/projects")
                    .cookie(session)
                    .contentType(JSON)
                    .content(
                        "{\"name\":\"%s\",\"ownerEmail\":\"%s\"}"
                            .formatted(kennung + "-Projekt", email)),
                201)
            .get("id")
            .asLong();
    long boardId =
        lies(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType(JSON)
                    .content("{\"name\":\"Board\"}"),
                201)
            .get("id")
            .asLong();
    String token =
        lies(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType(JSON)
                    .content(
                        "{\"name\":\"%s\",\"projectId\":%d,\"boardId\":%d}"
                            .formatted(kennung + "-Token", projectId, boardId)),
                201)
            .get("plaintext")
            .asText();
    return new Aufbau(session, projectId, boardId, token);
  }

  /** Meldet einen laufenden Nachtlauf, der vor fünf Minuten begann; liefert seine Laufkennung. */
  private Instant laufMelden(Aufbau a) throws Exception {
    Instant start = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
    mvc.perform(
            post("/api/kanban/night-runs")
                .header(TOKEN_HEADER, a.token())
                .contentType(JSON)
                .content(
                    """
                    {"startedAt":"%s","mode":"IMPLEMENTATION","durationMs":0,"processedCount":0,
                     "skippedCount":0,"unparsedCount":0,"complete":false,"items":[]}"""
                        .formatted(start)))
        .andExpect(status().isOk());
    return start;
  }

  /** Mit Token und Modell, dazu der Ausweis des Laufs, wenn {@code lauf} gesetzt ist. */
  private MockHttpServletRequestBuilder mitAgent(
      MockHttpServletRequestBuilder r, Aufbau a, @Nullable Instant lauf) {
    MockHttpServletRequestBuilder mit =
        r.header(TOKEN_HEADER, a.token()).header(AGENT_HEADER, MODELL);
    return lauf == null ? mit : mit.header(Laufkennung.HEADER, lauf.toString());
  }

  private Item anlegen(Aufbau a, String titel, @Nullable Instant lauf) throws Exception {
    JsonNode angelegt =
        lies(
            mitAgent(post("/api/kanban/items"), a, lauf)
                .contentType(JSON)
                .content("{\"title\":\"%s\"}".formatted(titel)),
            201);
    return new Item(angelegt.get("id").asLong(), angelegt.get("number").asInt());
  }

  private void verschieben(Aufbau a, Item item, String spalte, @Nullable Instant lauf)
      throws Exception {
    mvc.perform(
            mitAgent(put("/api/kanban/items/" + item.id() + "/move"), a, lauf)
                .contentType(JSON)
                .content("{\"column\":\"%s\",\"position\":0}".formatted(spalte)))
        .andExpect(status().isOk());
  }

  private void kommentieren(Aufbau a, Item item, Instant lauf) throws Exception {
    mvc.perform(
            mitAgent(post("/api/kanban/items/" + item.id() + "/comments"), a, lauf)
                .contentType(JSON)
                .content(json.writeValueAsString(new Text("Review-Befund"))))
        .andExpect(status().isCreated());
  }

  private JsonNode nacharbeit(Cookie wer, long boardId, int erwartet) throws Exception {
    return lies(get("/api/boards/" + boardId + "/night-run-rework").cookie(wer), erwartet);
  }

  private JsonNode lies(MockHttpServletRequestBuilder r, int erwartet) throws Exception {
    String text =
        mvc.perform(r)
            .andExpect(status().is(erwartet))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return text.isEmpty() ? json.nullNode() : json.readTree(text);
  }

  private Cookie session(String email, PlatformRole role) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, role));
    }
    return mvc.perform(
            post("/api/auth/login")
                .contentType(JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }
}
