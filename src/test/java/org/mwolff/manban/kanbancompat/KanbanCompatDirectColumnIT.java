package org.mwolff.manban.kanbancompat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * End-to-End der Spaltenwahl beim Ingest (#569, #1203): Ein Werkzeug, das einen freigegebenen Plan
 * in Arbeitspakete zerlegt, legt sie dort ab, wo der Nacht-Runner sie findet — in Ready.
 *
 * <p>Seit Issue #1203 legt <em>jeder</em> Ingest board-gebunden an; {@code direct} entscheidet nur
 * noch, wie streng ein angegebenes {@code column} aufgelöst wird. Mit {@code direct=true} bleibt
 * ein nicht auflösbarer Schlüssel ein 400, ohne {@code direct} greift die erste Spalte (E8a) —
 * sonst bekäme eine ältere Kit-Version, die {@code column: "BACKLOG"} bedingungslos sendet, auf
 * einem Board ohne Backlog-Spalte einen Fehler statt der zugesagten Karte.
 *
 * <p>Der Schwerpunkt liegt auf den Ablehnungen. Jede von ihnen verhindert einen Zustand, den man
 * hinterher schwer bemerkt: eine Karte in der falschen Spalte, eine Karte in DONE, die niemand
 * dorthin verschoben hat, oder eine Fehlermeldung, die davon abhängt, ob zufällig schon eine Karte
 * mit demselben Schlüssel existiert.
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
    // E8b: Ein Werkzeug von aussen legt nichts an, was bereits erledigt ist — dass eine Karte
    // fertig ist, stellt ein Mensch auf dem Board fest.
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
  void withoutDirectItLandsInTheFirstColumn() throws Exception {
    // Seit #1203 legt auch ein Ingest ohne direct board-gebunden an — ohne column in der ersten
    // Spalte. Vorher lag die Karte board-los im Ideen-Pool und war fuer die Automatik unsichtbar.
    Fixture f = fixture("dc-nodirect");

    ingest(f.token, "{\"title\":\"Ohne direct\"}").andExpect(status().isCreated());

    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", f.token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.BACKLOG[0].title").value("Ohne direct"))
        .andExpect(jsonPath("$.BACKLOG[0].number").isNumber());
  }

  @Test
  void withoutDirectTheNamedColumnStillApplies() throws Exception {
    // Ein bekannter Schluessel gilt auf beiden Wegen: der Aufrufer bekommt die Spalte, die er
    // nennt.
    Fixture f = fixture("dc-nodirect-col");

    ingest(f.token, "{\"title\":\"Paket\",\"column\":\"READY\"}").andExpect(status().isCreated());

    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", f.token))
        .andExpect(jsonPath("$.READY[0].title").value("Paket"))
        .andExpect(jsonPath("$.BACKLOG").isEmpty());
  }

  @Test
  void withoutDirectAnUnresolvableColumnFallsBackToTheFirstOne() throws Exception {
    // E8a: Der Anlass dieser Regel. `.claude/kit/board.mjs` sendet column: "BACKLOG"
    // bedingungslos und laesst bei einer Idee nur `direct` weg. Auf einem Board ohne
    // Backlog-Spalte waere ein 400 der Bruch der Zusage, dass aeltere Kit-Versionen weiter
    // anlegen koennen — also faellt die Anlage auf die erste Spalte zurueck.
    Fixture f = fixture("dc-nodirect-fallback");
    renameFirstColumn(f.owner, f.boardId, "Eingang");

    ingest(f.token, "{\"title\":\"Aeltere Kit-Version\",\"column\":\"BACKLOG\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.number").isNumber());

    // Die Karte liegt in der ersten (nun nicht mehr kanonisch benannten) Spalte des Boards.
    assertInFirstColumn(f, "Aeltere Kit-Version");
  }

  @Test
  void withoutDirectAnUnknownColumnKeyFallsBackToTheFirstOne() throws Exception {
    // Derselbe Rueckfall fuer einen Schluessel, den das Protokoll gar nicht kennt: Ohne direct
    // steuert der Aufrufer den Schluessel nicht, und eine Karte ist besser als ein Fehler.
    Fixture f = fixture("dc-nodirect-unknown");

    ingest(f.token, "{\"title\":\"Fremder Schluessel\",\"column\":\"FOO\"}")
        .andExpect(status().isCreated());

    assertInFirstColumn(f, "Fremder Schluessel");
  }

  @Test
  void withoutDirectTheBlankColumnFallsBackToTheFirstOne() throws Exception {
    // Auch der leere String ist ohne direct nur ein nicht auflösbarer Schluessel.
    Fixture f = fixture("dc-nodirect-blank");

    ingest(f.token, "{\"title\":\"Leerer Schluessel\",\"column\":\"   \"}")
        .andExpect(status().isCreated());

    assertInFirstColumn(f, "Leerer Schluessel");
  }

  @Test
  void withoutDirectTheDoneColumnIsStillRejected() throws Exception {
    // Der Done-Ausschluss ist fachlich, nicht technisch (E8b): Ein Werkzeug von aussen legt nichts
    // an, was bereits erledigt ist. Er haengt deshalb nicht an direct.
    Fixture f = fixture("dc-nodirect-done");

    ingest(f.token, "{\"title\":\"Schon fertig\",\"column\":\"DONE\"}")
        .andExpect(status().isBadRequest());

    assertNoCards(f.token);
  }

  @Test
  void ideaStoredAloneIsWithoutEffect() throws Exception {
    // AK 12: `ideaStored` bleibt im Vertrag zulaessig — ein 400 auf ein bekanntes Feld waere der
    // Bruch der Zusage. Wirkung hat es keine mehr: die Karte landet auf dem Board.
    Fixture f = fixture("dc-ideastored-alone");

    ingest(f.token, "{\"title\":\"Als Idee gemeint\",\"ideaStored\":true}")
        .andExpect(status().isCreated());

    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", f.token))
        .andExpect(jsonPath("$.BACKLOG[0].title").value("Als Idee gemeint"));
  }

  @Test
  void ideaStoredWithColumnAndWithoutDirectStillCreates() throws Exception {
    // Genau die Kombination, die `.claude/kit/board.mjs` fuer eine Idee sendet: column gesetzt,
    // direct weggelassen, ideaStored=true.
    Fixture f = fixture("dc-kit-idea");

    ingest(f.token, "{\"title\":\"Kit-Idee\",\"column\":\"BACKLOG\",\"ideaStored\":true}")
        .andExpect(status().isCreated());

    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", f.token))
        .andExpect(jsonPath("$.BACKLOG[0].title").value("Kit-Idee"));
  }

  /** Prüft, dass genau eine Karte mit diesem Titel in der ersten Spalte des Boards liegt. */
  private void assertInFirstColumn(Fixture f, String title) throws Exception {
    long firstColumn = firstColumnId(f.owner, f.boardId);
    mvc.perform(get("/api/boards/" + f.boardId + "/cards").cookie(f.owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].title").value(title))
        .andExpect(jsonPath("$[0].boardId").value(f.boardId))
        .andExpect(jsonPath("$[0].columnId").value(firstColumn))
        .andExpect(jsonPath("$[0].number").isNumber());
  }

  private long firstColumnId(Cookie session, long boardId) throws Exception {
    return columns(session, boardId).get(0).get("id").asLong();
  }

  private JsonNode columns(Cookie session, long boardId) throws Exception {
    return json.readTree(
            mvc.perform(get("/api/boards/" + boardId).cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("columns");
  }

  /** Nimmt dem Board seine Backlog-Spalte, ohne ihm die erste Spalte zu nehmen. */
  private void renameFirstColumn(Cookie session, long boardId, String name) throws Exception {
    mvc.perform(
            patch("/api/columns/" + firstColumnId(session, boardId))
                .cookie(session)
                .contentType("application/json")
                .content("{\"name\":\"%s\"}".formatted(name)))
        .andExpect(status().isOk());
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

  private record Fixture(Cookie owner, String token, long boardId) {}

  private Fixture fixture(String prefix) throws Exception {
    Cookie owner = session(prefix + "-owner@example.com", PlatformRole.USER);
    Cookie admin = session(prefix + "-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "Projekt " + prefix, prefix + "-owner@example.com");
    JsonNode board = createBoard(owner, projectId, "Board");
    long boardId = board.get("id").asLong();
    return new Fixture(owner, boundToken(owner, projectId, boardId), boardId);
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
