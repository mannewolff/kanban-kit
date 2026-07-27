package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * End-to-End-Test der Kanban-Compat-API (tbx.mjs/board.mjs-Kontrakt) über ein board-gebundenes PAT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;

  @Autowired private AppUserRepository users;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ObjectMapper json;

  // --- Setup-Helfer ---------------------------------------------------------

  private Cookie loginAs(String email) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.USER));
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

  private long createProject(String ownerEmail, String name) throws Exception {
    if (users.findByEmail(ownerEmail).isEmpty()) {
      users.save(
          new AppUser(
              null,
              ownerEmail,
              passwordEncoder.encode(PASSWORD),
              "Person",
              true,
              PlatformRole.USER));
    }
    Cookie admin = platformAdminSession();
    String body =
        mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private Cookie platformAdminSession() throws Exception {
    String email = "project-admin@example.com";
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.ADMIN));
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

  private long createBoard(Cookie session, long projectId, String name) throws Exception {
    String body =
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private long firstColumnId(Cookie session, long boardId) throws Exception {
    String body =
        mvc.perform(get("/api/boards/" + boardId).cookie(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("columns").get(0).get("id").asLong();
  }

  private String boundToken(Cookie session, long projectId, long boardId) throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"board-token\",\"projectId\":%d,\"boardId\":%d}"
                            .formatted(projectId, boardId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("plaintext").asText();
  }

  private JsonNode kanbanItems(String token) throws Exception {
    String body =
        mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  // --- Tests ----------------------------------------------------------------

  @Test
  void fullClientFlow_ingestToPool_thenPlan_thenListMoveComment() throws Exception {
    Cookie session = loginAs("kanban-owner@example.com");
    long projectId = createProject("kanban-owner@example.com", "Dogfood");
    long boardId = createBoard(session, projectId, "Board");
    String token = boundToken(session, projectId, boardId);

    // Ingest über den board-gebundenen Token landet seit Entscheidung B als board-lose Pool-Idee,
    // NICHT direkt im Board. Zurück kommt die id der Idee (keine board-scoped Nummer).
    String created =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .contentType("application/json")
                    .content(
                        "{\"title\":\"Erste Aufgabe\",\"body\":\"Beschreibung\","
                            + "\"column\":\"BACKLOG\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ideaId = json.readTree(created).get("id").asLong();

    // Die Idee liegt im Projekt-Pool und ist (noch) nicht in den kanbancompat-Board-Items sichtbar.
    mvc.perform(get("/api/projects/" + projectId + "/ideas").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(ideaId));
    assertThat(kanbanItems(token).get("BACKLOG")).isEmpty();

    // Einplanen (Cookie-API) macht daraus eine board-gebundene Karte; erst dann sehen die
    // kanbancompat-Operationen (list/move/comment) das Item.
    mvc.perform(
            put("/api/cards/" + ideaId + "/plan")
                .cookie(session)
                .contentType("application/json")
                .content("{\"targetBoardId\":" + boardId + "}"))
        .andExpect(status().isOk());

    // list -> gruppiert, Item im BACKLOG mit type "card"
    JsonNode items = kanbanItems(token);
    assertThat(items.has("BACKLOG")).isTrue();
    assertThat(items.has("READY")).isTrue();
    assertThat(items.has("IN_PROGRESS")).isTrue();
    assertThat(items.has("IN_REVIEW")).isTrue();
    assertThat(items.has("DONE")).isTrue();
    JsonNode item = items.get("BACKLOG").get(0);
    assertThat(item.get("title").asText()).isEqualTo("Erste Aufgabe");
    assertThat(item.get("body").asText()).isEqualTo("Beschreibung");
    assertThat(item.get("id").asLong()).isEqualTo(ideaId);
    assertThat(item.get("column").asText()).isEqualTo("BACKLOG");
    assertThat(item.get("type").asText()).isEqualTo("card");

    // move -> IN_PROGRESS
    mvc.perform(
            put("/api/kanban/items/" + ideaId + "/move")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content("{\"column\":\"IN_PROGRESS\",\"position\":0}"))
        .andExpect(status().isOk());
    JsonNode afterMove = kanbanItems(token);
    assertThat(afterMove.get("BACKLOG")).isEmpty();
    assertThat(afterMove.get("IN_PROGRESS").get(0).get("id").asLong()).isEqualTo(ideaId);

    // comment
    mvc.perform(
            post("/api/kanban/items/" + ideaId + "/comments")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content("{\"body\":\"Ein Kommentar\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void ingest_landsInProjectIdeaPool_notOnBoard() throws Exception {
    Cookie session = loginAs("kanban-idea@example.com");
    long projectId = createProject("kanban-idea@example.com", "Idea-Dogfood");
    long boardId = createBoard(session, projectId, "Idea-Board");
    String token = boundToken(session, projectId, boardId);

    // Entscheidung B: jeder board-token-Ingest landet als board-lose Pool-Idee. Das ideaStored-Feld
    // ist gegenstandslos (hier bewusst weggelassen — das Ergebnis ist mit/ohne Feld identisch).
    String created =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .contentType("application/json")
                    .content("{\"title\":\"Als Idee\"}"))
            .andExpect(status().isCreated())
            // #402: die Ingest-Antwort enthält die sofort vergebene projektweite Nummer.
            .andExpect(jsonPath("$.number").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ideaId = json.readTree(created).get("id").asLong();
    int ideaNumber = json.readTree(created).get("number").asInt();

    // Akzeptanzkriterium: erscheint in GET /api/projects/{id}/ideas mit derselben Nummer ...
    mvc.perform(get("/api/projects/" + projectId + "/ideas").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(ideaId))
        .andExpect(jsonPath("$[0].number").value(ideaNumber))
        .andExpect(jsonPath("$[0].title").value("Als Idee"))
        .andExpect(jsonPath("$[0].ideaStored").value(true));
    // ... aber NICHT in GET /api/boards/{id}/cards.
    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void epicsEndpointReportsProgress() throws Exception {
    Cookie session = loginAs("kanban-epics@example.com");
    long projectId = createProject("kanban-epics@example.com", "Epic-Projekt");
    long boardId = createBoard(session, projectId, "Epic-Board");
    String token = boundToken(session, projectId, boardId);

    // Epic über die normale (Cookie-)API anlegen.
    mvc.perform(
            post("/api/boards/" + boardId + "/cards")
                .cookie(session)
                .contentType("application/json")
                .content("{\"title\":\"Großes Epic\",\"type\":\"EPIC\",\"shortcode\":\"EPX\"}"))
        .andExpect(status().isCreated());

    mvc.perform(get("/api/kanban/epics").header("X-Kanban-Token", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].title").value("Großes Epic"))
        .andExpect(jsonPath("$[0].shortcode").value("EPX"))
        .andExpect(jsonPath("$[0].progress.total").value(0))
        .andExpect(jsonPath("$[0].progress.done").value(0));
  }

  @Test
  void invalidTokenIsUnauthorized() throws Exception {
    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", "tk_bogus"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unboundTokenIsConflict() throws Exception {
    Cookie session = loginAs("kanban-unbound@example.com");
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"unbound\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = json.readTree(body).get("plaintext").asText();

    mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", token))
        .andExpect(status().isConflict());
  }

  @Test
  void tokenIsScopedToItsBoard() throws Exception {
    Cookie session = loginAs("kanban-scope@example.com");
    long projectId = createProject("kanban-scope@example.com", "Scope-Projekt");
    long board1 = createBoard(session, projectId, "Board 1");
    long board2 = createBoard(session, projectId, "Board 2");
    String token1 = boundToken(session, projectId, board1);

    // Karte auf board2 über die Cookie-API anlegen.
    long col2 = firstColumnId(session, board2);
    String createdOnBoard2 =
        mvc.perform(
                post("/api/boards/" + board2 + "/cards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"title\":\"Fremd\",\"columnId\":%d}".formatted(col2)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long foreignCardId = json.readTree(createdOnBoard2).get("id").asLong();

    // board1-Token darf board2-Karte nicht verschieben (Scope): 404.
    mvc.perform(
            put("/api/kanban/items/" + foreignCardId + "/move")
                .header("X-Kanban-Token", token1)
                .contentType("application/json")
                .content("{\"column\":\"DONE\",\"position\":0}"))
        .andExpect(status().isNotFound());

    // Eigene Karte auf board1 über die Cookie-API anlegen (ein Ingest ginge seit Entscheidung B in
    // den Pool, nicht aufs Board): sonst wären alle Spalten leer und die Non-Leak-Prüfung unten
    // würde vacuously durchlaufen, ohne wirklich etwas zu beweisen (Sonar S5841).
    long col1 = firstColumnId(session, board1);
    mvc.perform(
            post("/api/boards/" + board1 + "/cards")
                .cookie(session)
                .contentType("application/json")
                .content("{\"title\":\"Eigene Karte\",\"columnId\":%d}".formatted(col1)))
        .andExpect(status().isCreated());

    // board1-Items enthalten die eigene Karte, aber nicht die Fremdkarte (Scope-Beweis).
    JsonNode items = kanbanItems(token1);
    assertThat(items.get("BACKLOG")).isNotEmpty();
    for (String col : new String[] {"BACKLOG", "READY", "IN_PROGRESS", "IN_REVIEW", "DONE"}) {
      assertThat(items.get(col))
          .allSatisfy(n -> assertThat(n.get("title").asText()).isNotEqualTo("Fremd"));
    }
  }

  /**
   * Eine Karte im Ideen-Speicher trägt weiterhin Board und Spalte, ist in der Oberfläche aber
   * ausgeblendet. Die Compat-Schicht filterte nur archivierte Karten und meldete sie deshalb als
   * reguläre Karte ihrer Spalte — Kit und Nacht-Runner sahen eine Aufgabe, die für Menschen nicht
   * existiert (real passiert, Issue #428). Sie darf über diese Schnittstelle weder auftauchen noch
   * bewegt noch kommentiert werden.
   */
  @Test
  void ideaStoredCardIsInvisibleAndUntouchableForTheAutomation() throws Exception {
    long projectId = createProject("ghost-owner@example.com", "GhostProjekt");
    Cookie owner = loginAs("ghost-owner@example.com");
    long boardId = createBoard(owner, projectId, "GhostBoard");
    long columnId = firstColumnId(owner, boardId);
    String token = boundToken(owner, projectId, boardId);

    long visible = createCard(owner, boardId, columnId, "SichtbareKarte");
    long ghost = createCard(owner, boardId, columnId, "GeisterKarte");
    long archivedCard = createCard(owner, boardId, columnId, "ArchivierteKarte");

    // Vor dem Verschieben in den Ideen-Speicher sind alle drei Karten für die Automatik da.
    assertThat(kanbanItems(token).get("BACKLOG")).hasSize(3);

    // Archiviert und im Ideen-Speicher sind zwei unabhängige Gründe, eine Karte auszublenden —
    // jeder muss für sich greifen.
    mvc.perform(post("/api/cards/" + archivedCard + "/archive").cookie(owner))
        .andExpect(status().isOk());

    mvc.perform(post("/api/cards/" + ghost + "/idea-storage").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ideaStored").value(true))
        .andExpect(jsonPath("$.boardId").value(boardId));

    // Die Automatik sieht nur noch die sichtbare Karte — und damit genauso viele wie die UI-API.
    JsonNode items = kanbanItems(token);
    assertThat(items.get("BACKLOG")).hasSize(1);
    assertThat(items.get("BACKLOG").get(0).get("id").asLong()).isEqualTo(visible);

    String uiCards =
        mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long uiVisible =
        json.readTree(uiCards)
            .valueStream()
            .filter(c -> !c.get("ideaStored").asBoolean() && !c.get("archived").asBoolean())
            .count();
    assertThat((long) items.get("BACKLOG").size()).isEqualTo(uiVisible);

    // Bewegen und Kommentieren der unsichtbaren Karte sind über die Schnittstelle nicht möglich.
    mvc.perform(
            put("/api/kanban/items/" + ghost + "/move")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content("{\"column\":\"READY\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/kanban/items/" + ghost + "/comments")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content("{\"body\":\"Kommentar\"}"))
        .andExpect(status().isNotFound());

    // Gegenprobe zum zweiten Grund derselben Prüfung: eine reguläre Karte auf einem anderen Board
    // ist über dieses Token ebenfalls nicht kommentierbar.
    long otherBoard = createBoard(owner, projectId, "AnderesBoard");
    long foreign = createCard(owner, otherBoard, firstColumnId(owner, otherBoard), "FremdeKarte");
    mvc.perform(
            post("/api/kanban/items/" + foreign + "/comments")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content("{\"body\":\"Kommentar\"}"))
        .andExpect(status().isNotFound());
  }

  private long createCard(Cookie session, long boardId, long columnId, String title)
      throws Exception {
    String body =
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }
}
