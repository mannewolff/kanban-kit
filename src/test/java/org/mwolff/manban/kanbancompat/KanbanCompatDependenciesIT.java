package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * End-to-End der Abhängigkeiten über den Ingest (#566): Ein Migrations-Script überträgt die
 * strukturierten {@code Issue #N}-Verweise eines fremden Trackers.
 *
 * <p>Zwei Eigenschaften sind der Kern und werden hier belegt: Der Endpunkt erreicht auch board-lose
 * Pool-Ideen (der board-bezogene Guard von {@code move}/{@code comments} täte das nicht), und
 * Verweise auf noch nicht importierte Nummern werden gespeichert statt abgelehnt — sonst müsste ein
 * Import seine Karten nach Abhängigkeitsgraph sortieren.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatDependenciesIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void forwardReferenceIsStoredAndResolvesAfterTargetArrives() throws Exception {
    Fixture f = fixture("dep-forward");

    // Karte 10 verweist auf 20 — die es noch nicht gibt. Genau der Fall, den der UI-Pfad ablehnt.
    long ten = ingest(f.token, "Issue 10", "github#10", 10);
    setDependencies(f.token, ten, "[20]").andExpect(status().isNoContent());

    // Der Verweis ist gespeichert, obwohl das Ziel fehlt.
    assertThat(dependenciesOf(f.owner, ten)).containsExactly(20);

    // Nach dem Import der Zielkarte ist er vollstaendig aufgeloest — ohne zweiten Schreibzugriff.
    ingest(f.token, "Issue 20", "github#20", 20);
    assertThat(dependenciesOf(f.owner, ten)).containsExactly(20);
  }

  @Test
  void poolIdeaIsReachable() throws Exception {
    // Ohne direct entsteht eine board-lose Pool-Idee (Entscheidung B). Der board-bezogene Guard
    // von move/comments antwortet fuer sie mit 404 (#472) — dieser Endpunkt muss sie erreichen,
    // sonst scheitert der Hauptanwendungsfall des Issues.
    Fixture f = fixture("dep-pool");
    long poolIdea = ingestPooled(f.token, "Rohe Idee");

    setDependencies(f.token, poolIdea, "[77]").andExpect(status().isNoContent());

    assertThat(dependenciesOf(f.owner, poolIdea)).containsExactly(77);
  }

  @Test
  void replaceSemanticsMakeRepeatedCallsIdempotent() throws Exception {
    Fixture f = fixture("dep-idem");
    long id = ingest(f.token, "Issue 1", "github#1", 1);

    setDependencies(f.token, id, "[5,6]").andExpect(status().isNoContent());
    setDependencies(f.token, id, "[5,6]").andExpect(status().isNoContent());

    assertThat(dependenciesOf(f.owner, id)).containsExactly(5, 6);
  }

  @Test
  void listReplacesPreviousReferences() throws Exception {
    Fixture f = fixture("dep-replace");
    long id = ingest(f.token, "Issue 1", "github#1", 1);
    setDependencies(f.token, id, "[5,6]").andExpect(status().isNoContent());

    setDependencies(f.token, id, "[7]").andExpect(status().isNoContent());

    assertThat(dependenciesOf(f.owner, id)).containsExactly(7);
  }

  @Test
  void emptyListClearsReferences() throws Exception {
    Fixture f = fixture("dep-clear");
    long id = ingest(f.token, "Issue 1", "github#1", 1);
    setDependencies(f.token, id, "[5]").andExpect(status().isNoContent());

    setDependencies(f.token, id, "[]").andExpect(status().isNoContent());

    assertThat(dependenciesOf(f.owner, id)).isEmpty();
  }

  @Test
  void selfReferenceIsRejected() throws Exception {
    Fixture f = fixture("dep-self");
    long id = ingest(f.token, "Issue 42", "github#42", 42);

    setDependencies(f.token, id, "[42]").andExpect(status().isBadRequest());
  }

  @Test
  void cardOfForeignProjectIsNotFound() throws Exception {
    // Der Token bindet an ein Projekt; eine Karte aus einem anderen bleibt unerreichbar, auch
    // wenn derselbe Nutzer dort Rechte haette.
    Fixture own = fixture("dep-own");
    Fixture foreign = fixture("dep-foreign");
    long foreignCard = ingest(foreign.token, "Fremd", "github#1", 1);

    setDependencies(own.token, foreignCard, "[5]").andExpect(status().isNotFound());
  }

  @Test
  void unknownCardIsNotFound() throws Exception {
    Fixture f = fixture("dep-unknown");

    setDependencies(f.token, 999_999L, "[5]").andExpect(status().isNotFound());
  }

  @Test
  void tokenLosesAccessWhenTheRoleIsDowngraded() throws Exception {
    // Die Rechte werden bei jedem Aufruf geprueft, nicht nur beim Anlegen des Tokens. Ein VIEWER
    // bekommt gar keinen Token (die Erstellung verlangt selbst Schreibrecht) — der realistische
    // Fall ist deshalb der Entzug danach: Der Token bleibt gueltig, das Recht ist weg.
    Fixture f = fixture("dep-downgrade");
    long card = ingest(f.token, "Ziel", "github#1", 1);
    String email = "dep-downgrade-guest@example.com";
    Cookie guest = session(email, PlatformRole.USER);
    addMember(f.owner, f.projectId, email, "MEMBER");
    long guestId = users.findByEmail(email).orElseThrow().requireId();
    JsonNode board = boardOf(f.owner, f.projectId);
    String guestToken = boundToken(guest, f.projectId, board.get("id").asLong());

    // Mit MEMBER-Rechten traegt der Token noch.
    setDependencies(guestToken, card, "[5]").andExpect(status().isNoContent());

    mvc.perform(
            patch("/api/projects/" + f.projectId + "/members/" + guestId)
                .cookie(f.owner)
                .contentType("application/json")
                .content("{\"role\":\"VIEWER\"}"))
        .andExpect(status().isOk());

    setDependencies(guestToken, card, "[6]").andExpect(status().isForbidden());
  }

  @Test
  void nullElementInTheListIsBadRequest() throws Exception {
    // @Positive allein laesst null durch; der Selbstverweis-Vergleich entpackt den Wert und
    // endete sonst als NullPointerException in einem 500.
    Fixture f = fixture("dep-null");
    long card = ingest(f.token, "Ziel", "github#1", 1);

    setDependencies(f.token, card, "[null]").andExpect(status().isBadRequest());
  }

  @Test
  void numberAboveMaximumIsBadRequest() throws Exception {
    Fixture f = fixture("dep-max");
    long card = ingest(f.token, "Ziel", "github#1", 1);

    setDependencies(f.token, card, "[%d]".formatted(Integer.MAX_VALUE))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listReadPathIsUnchanged() throws Exception {
    // Bewusst keine Vertragserweiterung: GET /items liefert weiterhin keine Abhaengigkeiten.
    Fixture f = fixture("dep-contract");
    long id = ingest(f.token, "Issue 1", "github#1", 1);
    setDependencies(f.token, id, "[5]").andExpect(status().isNoContent());

    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", f.token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.BACKLOG[0].dependencies").doesNotExist());
  }

  private record Fixture(Cookie owner, String token, long projectId) {}

  private ResultActions setDependencies(String token, long cardId, String jsonArray)
      throws Exception {
    return mvc.perform(
        put("/api/kanban/items/" + cardId + "/dependencies")
            .header("X-Kanban-Token", token)
            .contentType("application/json")
            .content("{\"dependsOn\":%s}".formatted(jsonArray)));
  }

  /** Liest die Abhaengigkeiten ueber den UI-Pfad — der Ingest gibt sie bewusst nicht zurueck. */
  private java.util.List<Integer> dependenciesOf(Cookie session, long cardId) throws Exception {
    String body =
        mvc.perform(get("/api/cards/" + cardId).cookie(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    java.util.List<Integer> result = new java.util.ArrayList<>();
    json.readTree(body).get("dependencies").forEach(n -> result.add(n.asInt()));
    return result;
  }

  private long ingest(String token, String title, String key, int number) throws Exception {
    String body =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .contentType("application/json")
                    .content(
                        "{\"title\":\"%s\",\"externalKey\":\"%s\",\"number\":%d,\"direct\":true}"
                            .formatted(title, key, number)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private long ingestPooled(String token, String title) throws Exception {
    String body =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .contentType("application/json")
                    .content("{\"title\":\"%s\"}".formatted(title)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  /** Nimmt einen bereits registrierten Nutzer mit der angegebenen Rolle ins Projekt auf. */
  private void addMember(Cookie owner, long projectId, String email, String role) throws Exception {
    mvc.perform(
            post("/api/projects/" + projectId + "/invitations")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role)))
        .andExpect(status().isAccepted());
  }

  /** Erstes Board des Projekts — Ziel fuer das Token des eingeladenen Nutzers. */
  private JsonNode boardOf(Cookie session, long projectId) throws Exception {
    String body =
        mvc.perform(get("/api/projects/" + projectId + "/boards").cookie(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get(0);
  }

  private Fixture fixture(String prefix) throws Exception {
    Cookie owner = session(prefix + "-owner@example.com", PlatformRole.USER);
    Cookie admin = session(prefix + "-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "Projekt " + prefix, prefix + "-owner@example.com");
    JsonNode board = createBoard(owner, projectId, "Board");
    return new Fixture(owner, boundToken(owner, projectId, board.get("id").asLong()), projectId);
  }

  private String boundToken(Cookie session, long projectId, long boardId) throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"import-token\",\"projectId\":%d,\"boardId\":%d}"
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
