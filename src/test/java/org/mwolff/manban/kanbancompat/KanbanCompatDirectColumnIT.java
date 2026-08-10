package org.mwolff.manban.kanbancompat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-End des direct-Ingests mit Spaltenwahl (#569): Ein Werkzeug, das einen freigegebenen Plan
 * in Arbeitspakete zerlegt, legt sie dort ab, wo der Nacht-Runner sie findet — in Ready.
 *
 * <p>Der Schwerpunkt liegt auf den Ablehnungen. Jede von ihnen verhindert einen Zustand, den man
 * hinterher schwer bemerkt: eine Karte in der falschen Spalte, eine Karte in DONE ohne
 * Aufbewahrungs-Zeitstempel, oder eine Fehlermeldung, die davon abhängt, ob zufällig schon eine
 * Karte mit demselben Schlüssel existiert.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatDirectColumnIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void directIngestLandsInTheRequestedColumn() throws Exception {
    Fixture f = fixture("dc-ready");

    ingest(f.token, "{\"title\":\"Paket\",\"direct\":true,\"column\":\"READY\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.number").isNumber());

    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", f.token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.READY[0].title").value("Paket"))
        .andExpect(jsonPath("$.BACKLOG").isEmpty());
  }

  @Test
  void withoutColumnItStaysInTheFirstColumn() throws Exception {
    // Heutiges Verhalten seit #535 — der Sonar-Sync sendet kein column und darf sich nicht aendern.
    Fixture f = fixture("dc-nocol");

    ingest(f.token, "{\"title\":\"Finding\",\"direct\":true}").andExpect(status().isCreated());

    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", f.token))
        .andExpect(jsonPath("$.BACKLOG[0].title").value("Finding"));
  }

  @Test
  void explicitNullColumnStaysInTheFirstColumn() throws Exception {
    Fixture f = fixture("dc-nullcol");

    ingest(f.token, "{\"title\":\"Finding\",\"direct\":true,\"column\":null}")
        .andExpect(status().isCreated());

    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", f.token))
        .andExpect(jsonPath("$.BACKLOG[0].title").value("Finding"));
  }

  @Test
  void blankColumnIsRejected() throws Exception {
    // Ein leerer String ist ein angegebener, ungueltiger Key — nicht dasselbe wie „fehlt".
    Fixture f = fixture("dc-blank");

    ingest(f.token, "{\"title\":\"X\",\"direct\":true,\"column\":\"   \"}")
        .andExpect(status().isBadRequest());

    assertNoCards(f.token);
  }

  @Test
  void unknownColumnKeyIsRejected() throws Exception {
    Fixture f = fixture("dc-unknown");

    ingest(f.token, "{\"title\":\"X\",\"direct\":true,\"column\":\"FOO\"}")
        .andExpect(status().isBadRequest());

    assertNoCards(f.token);
  }

  @Test
  void doneColumnIsRejected() throws Exception {
    // doCreate setzt movedToDoneAt nicht; die Done-Aufbewahrung archiviert allein darueber. Eine
    // direkt in DONE angelegte Karte laege dauerhaft fest, ohne dass der Grund sichtbar waere.
    Fixture f = fixture("dc-done");

    ingest(f.token, "{\"title\":\"X\",\"direct\":true,\"column\":\"DONE\"}")
        .andExpect(status().isBadRequest());

    assertNoCards(f.token);
  }

  @Test
  void idempotentHitKeepsTheExistingColumn() throws Exception {
    // Der Schluessel trifft: Die bestehende Karte wird zurueckgegeben, aber nicht verschoben.
    Fixture f = fixture("dc-idem");
    ingest(f.token, "{\"title\":\"P\",\"direct\":true,\"column\":\"READY\",\"externalKey\":\"k1\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.created").value(true));

    ingest(
            f.token,
            "{\"title\":\"P erneut\",\"direct\":true,\"column\":\"IN_PROGRESS\","
                + "\"externalKey\":\"k1\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.created").value(false));

    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", f.token))
        .andExpect(jsonPath("$.READY[0].title").value("P"))
        .andExpect(jsonPath("$.IN_PROGRESS").isEmpty());
  }

  @Test
  void invalidColumnIsRejectedEvenWhenTheKeyWouldHit() throws Exception {
    // Die Spalte wird vor dem Duplikat-Check aufgeloest: Dieselbe Fehlermeldung, egal ob der
    // Schluessel schon eine Karte trifft. Sonst haengt sie davon ab, ob zufaellig eine existiert.
    Fixture f = fixture("dc-idem-invalid");
    ingest(f.token, "{\"title\":\"P\",\"direct\":true,\"column\":\"READY\",\"externalKey\":\"k1\"}")
        .andExpect(status().isCreated());

    ingest(f.token, "{\"title\":\"P\",\"direct\":true,\"column\":\"FOO\",\"externalKey\":\"k1\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void ideaStoredStaysWithoutEffectAlongsideDirect() throws Exception {
    // Die konflikttraechtige Kombination: direct gewinnt, ideaStored bleibt wirkungslos.
    Fixture f = fixture("dc-ideastored");

    ingest(f.token, "{\"title\":\"P\",\"direct\":true,\"column\":\"READY\",\"ideaStored\":true}")
        .andExpect(status().isCreated());

    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", f.token))
        .andExpect(jsonPath("$.READY[0].title").value("P"));
  }

  @Test
  void withoutDirectTheColumnStaysIrrelevant() throws Exception {
    // Ohne direct geht die Karte in den Pool — auch ein DONE fuehrt nicht zur Ablehnung.
    Fixture f = fixture("dc-pool");

    ingest(f.token, "{\"title\":\"Idee\",\"column\":\"DONE\"}").andExpect(status().isCreated());

    assertNoCards(f.token);
  }

  private void assertNoCards(String token) throws Exception {
    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.BACKLOG").isEmpty())
        .andExpect(jsonPath("$.READY").isEmpty())
        .andExpect(jsonPath("$.IN_PROGRESS").isEmpty())
        .andExpect(jsonPath("$.IN_REVIEW").isEmpty())
        .andExpect(jsonPath("$.DONE").isEmpty());
  }

  private ResultActions ingest(String token, String body) throws Exception {
    return mvc.perform(
        post("/api/kanban/items")
            .header("X-Kanban-Token", token)
            .contentType("application/json")
            .content(body));
  }

  private record Fixture(Cookie owner, String token) {}

  private Fixture fixture(String prefix) throws Exception {
    Cookie owner = session(prefix + "-owner@example.com", PlatformRole.USER);
    Cookie admin = session(prefix + "-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "Projekt " + prefix, prefix + "-owner@example.com");
    JsonNode board = createBoard(owner, projectId, "Board");
    return new Fixture(owner, boundToken(owner, projectId, board.get("id").asLong()));
  }

  private String boundToken(Cookie session, long projectId, long boardId) throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"ingest-token\",\"projectId\":%d,\"boardId\":%d}"
                            .formatted(projectId, boardId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("plaintext").asText();
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

  private JsonNode createBoard(Cookie session, long projectId, String name) throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
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
