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
import org.mwolff.manban.common.TextLimits;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

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

  /** Gültiges Token ohne Board-Bindung — für die 409-Gegenprobe der Compat-Endpunkte. */
  private String unboundToken(Cookie session, String name) throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\"}".formatted(name)))
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
  void fullClientFlow_ingest_thenListMoveComment() throws Exception {
    Cookie session = loginAs("kanban-owner@example.com");
    long projectId = createProject("kanban-owner@example.com", "Dogfood");
    long boardId = createBoard(session, projectId, "Board");
    String token = boundToken(session, projectId, boardId);

    // Ingest über den board-gebundenen Token legt seit Issue #1203 direkt auf dem Board an — in
    // der genannten Spalte. Zurück kommen id und projektweite Nummer der Karte.
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
            .andExpect(jsonPath("$.number").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long cardId = json.readTree(created).get("id").asLong();

    // Die Karte ist sofort für die kanbancompat-Operationen (list/move/comment) da — sie liegt
    // nicht mehr an einem zweiten Ablageort, den ein Mensch erst einplanen müsste.
    JsonNode items = kanbanItems(token);
    assertThat(items.has("BACKLOG")).isTrue();
    assertThat(items.has("READY")).isTrue();
    assertThat(items.has("IN_PROGRESS")).isTrue();
    assertThat(items.has("IN_REVIEW")).isTrue();
    assertThat(items.has("DONE")).isTrue();
    JsonNode item = items.get("BACKLOG").get(0);
    assertThat(item.get("title").asText()).isEqualTo("Erste Aufgabe");
    assertThat(item.get("body").asText()).isEqualTo("Beschreibung");
    assertThat(item.get("id").asLong()).isEqualTo(cardId);
    assertThat(item.get("column").asText()).isEqualTo("BACKLOG");
    assertThat(item.get("type").asText()).isEqualTo("card");

    // move -> IN_PROGRESS
    mvc.perform(
            put("/api/kanban/items/" + cardId + "/move")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content("{\"column\":\"IN_PROGRESS\",\"position\":0}"))
        .andExpect(status().isOk());
    JsonNode afterMove = kanbanItems(token);
    assertThat(afterMove.get("BACKLOG")).isEmpty();
    assertThat(afterMove.get("IN_PROGRESS").get(0).get("id").asLong()).isEqualTo(cardId);

    // comment
    mvc.perform(
            post("/api/kanban/items/" + cardId + "/comments")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content("{\"body\":\"Ein Kommentar\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void comments_areReadable_inChronologicalOrder() throws Exception {
    Cookie session = loginAs("kanban-comments@example.com");
    long projectId = createProject("kanban-comments@example.com", "Comment-Dogfood");
    long boardId = createBoard(session, projectId, "Comment-Board");
    String token = boundToken(session, projectId, boardId);
    long cardId = createCard(session, boardId, firstColumnId(session, boardId), "Karte");

    // Ohne Kommentare: leeres Array, kein 404 und kein null.
    mvc.perform(get("/api/kanban/items/" + cardId + "/comments").header("X-Kanban-Token", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    kanbanComment(token, cardId, "Erster");
    kanbanComment(token, cardId, "Zweiter");

    // Beide Kommentare sind lesbar, in Schreibreihenfolge, mit Autor und Zeitstempel.
    mvc.perform(get("/api/kanban/items/" + cardId + "/comments").header("X-Kanban-Token", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].body").value("Erster"))
        .andExpect(jsonPath("$[0].author").value("Person"))
        .andExpect(jsonPath("$[0].createdAt").exists())
        .andExpect(jsonPath("$[1].body").value("Zweiter"));

    // Karte eines fremden Projekts (Token-Nutzer ist dort kein Mitglied): policy-konform 404,
    // damit die Existenz fremder Karten nicht über den Statuscode durchsickert. Bewacht wird hier
    // die Mitgliedschaftsprüfung der Kommentar-Fassade, nicht der Board-Guard — dieser Fall ist
    // doppelt geschützt. Der Board-Guard hat mit listComments_isScopedToTheBoundBoard einen eigenen
    // Test (#469).
    Cookie stranger = loginAs("kanban-comments-stranger@example.com");
    long foreignProject = createProject("kanban-comments-stranger@example.com", "Fremdprojekt");
    long foreignBoard = createBoard(stranger, foreignProject, "Fremdboard");
    long foreignCard =
        createCard(stranger, foreignBoard, firstColumnId(stranger, foreignBoard), "FremdeKarte");
    mvc.perform(
            get("/api/kanban/items/" + foreignCard + "/comments").header("X-Kanban-Token", token))
        .andExpect(status().isNotFound());
  }

  /**
   * Der Scope-Fall, der den Board-Guard des Lesepfads wirklich bewacht: gleiches Projekt, anderes
   * Board. Bei einer Karte aus einem <em>fremden</em> Projekt (siehe {@link
   * #comments_areReadable_inChronologicalOrder}) greift zusätzlich die Mitgliedschaftsprüfung der
   * Kommentar-Fassade — ein entfernter {@code requireOnBoard} bliebe dort unbemerkt (#469). Hier
   * ist der Token-Nutzer Mitglied desselben Projekts, sodass allein die Board-Bindung des Tokens
   * den Zugriff verhindert.
   *
   * <p>Der Schreibpfad ist für denselben Fall bereits durch die Gegenprobe am Ende von {@link
   * #archivedCardIsInvisibleForTheAutomation} gepinnt (reguläre Karte auf einem zweiten Board
   * desselben Projekts, POST → 404) und wird hier nicht dupliziert.
   */
  @Test
  void listComments_isScopedToTheBoundBoard() throws Exception {
    Cookie session = loginAs("kanban-comment-scope@example.com");
    long projectId = createProject("kanban-comment-scope@example.com", "Comment-Scope");
    long board1 = createBoard(session, projectId, "Scope-Board 1");
    long board2 = createBoard(session, projectId, "Scope-Board 2");
    String token1 = boundToken(session, projectId, board1);

    // Karte auf board2 mit einem Kommentar über die Cookie-API: ohne Board-Guard würde der Lesepfad
    // diesen fremden Inhalt herausgeben, nicht bloß eine leere Liste.
    long foreignCard = createCard(session, board2, firstColumnId(session, board2), "Karte Board 2");
    mvc.perform(
            post("/api/cards/" + foreignCard + "/comments")
                .cookie(session)
                .contentType("application/json")
                .content("{\"body\":\"Nur für Board 2\"}"))
        .andExpect(status().isCreated());

    // Gegenprobe, dass das Token grundsätzlich lesen darf: auf dem eigenen Board liefert es 200.
    long ownCard = createCard(session, board1, firstColumnId(session, board1), "Karte Board 1");
    mvc.perform(get("/api/kanban/items/" + ownCard + "/comments").header("X-Kanban-Token", token1))
        .andExpect(status().isOk());

    // board1-Token darf die Kommentare der board2-Karte nicht lesen (Scope): 404.
    mvc.perform(
            get("/api/kanban/items/" + foreignCard + "/comments").header("X-Kanban-Token", token1))
        .andExpect(status().isNotFound());
  }

  /**
   * Der Aktivitätsverlauf innerhalb der Board-Grenze (#876). Dieselbe Auskunft wie {@code GET
   * /api/cards/{id}/activity}, nur über das gebundene Token — der Ersatzweg für das nächtliche
   * Abdeckungs-Gate, sobald board-gebundene Token die übrige API nicht mehr erreichen.
   *
   * <p>Reichweite wie beim Kommentar-Lesepfad: eigenes Board 200, Karte eines anderen Boards
   * desselben Projekts 404 (Board-Guard, nicht Mitgliedschaft), ungebundenes Token 409. Eine
   * board-lose Karte gibt es über diesen Weg seit Issue #1203 nicht mehr — jeder Ingest legt
   * board-gebunden an, und die neue Karte ist damit selbst über ihr Token lesbar.
   */
  @Test
  void activity_isReadableForOwnBoard_andScopedToTheBoundBoard() throws Exception {
    Cookie session = loginAs("kanban-activity@example.com");
    long projectId = createProject("kanban-activity@example.com", "Activity-Dogfood");
    long boardId = createBoard(session, projectId, "Activity-Board");
    String token = boundToken(session, projectId, boardId);
    long cardId = createCard(session, boardId, firstColumnId(session, boardId), "Karte");

    // Eigenes Board: 200 mit dem Anlege-Eintrag — die Auskunft, aus der das Abdeckungs-Gate das
    // Anlagedatum liest (Typ und Zeitstempel).
    mvc.perform(get("/api/kanban/items/" + cardId + "/activity").header("X-Kanban-Token", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].type").value("CREATED"))
        .andExpect(jsonPath("$[0].createdAt").exists())
        .andExpect(jsonPath("$[0].origin").value("SESSION"));

    // Zweites Board desselben Projekts: hier ist der Token-Nutzer Mitglied, allein die
    // Board-Bindung hält ihn ab — 404.
    long otherBoard = createBoard(session, projectId, "Activity-Board 2");
    long foreignCard =
        createCard(session, otherBoard, firstColumnId(session, otherBoard), "Fremde Karte");
    mvc.perform(
            get("/api/kanban/items/" + foreignCard + "/activity").header("X-Kanban-Token", token))
        .andExpect(status().isNotFound());

    // Eine über den Ingest angelegte Karte liegt auf dem gebundenen Board und ist damit über
    // dasselbe Token lesbar (Issue #1203) — vorher war sie board-los und antwortete mit 404.
    String created =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .contentType("application/json")
                    .content("{\"title\":\"Per Ingest\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long ingestedId = json.readTree(created).get("id").asLong();
    mvc.perform(
            get("/api/kanban/items/" + ingestedId + "/activity").header("X-Kanban-Token", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].origin").value("TOKEN"));

    // Ungebundenes Token: 409 wie bei allen übrigen Compat-Endpunkten.
    mvc.perform(
            get("/api/kanban/items/" + cardId + "/activity")
                .header("X-Kanban-Token", unboundToken(session, "activity-unbound")))
        .andExpect(status().isConflict());
  }

  @Test
  void comment_rejectsBodyOverTheLengthLimit() throws Exception {
    Cookie session = loginAs("kanban-comment-size@example.com");
    long projectId = createProject("kanban-comment-size@example.com", "Comment-Size");
    long boardId = createBoard(session, projectId, "Size-Board");
    String token = boundToken(session, projectId, boardId);
    long cardId = createCard(session, boardId, firstColumnId(session, boardId), "Karte");

    // Ein Token ist für Automatik gedacht: ohne Grenze könnte es beliebig große Kommentare in die
    // text-Spalte schreiben. Grenze und Wert sind identisch zum UI-Pfad (CommentController).
    kanbanCommentRequest(token, cardId, "a".repeat(TextLimits.MAX_TEXT + 1))
        .andExpect(status().isBadRequest());

    // Gegenprobe auf dem Grenzwert selbst.
    kanbanCommentRequest(token, cardId, "a".repeat(TextLimits.MAX_TEXT))
        .andExpect(status().isCreated());
  }

  private ResultActions kanbanCommentRequest(String token, long cardId, String body)
      throws Exception {
    return mvc.perform(
        post("/api/kanban/items/" + cardId + "/comments")
            .header("X-Kanban-Token", token)
            .contentType("application/json")
            .content("{\"body\":\"%s\"}".formatted(body)));
  }

  private void kanbanComment(String token, long cardId, String body) throws Exception {
    mvc.perform(
            post("/api/kanban/items/" + cardId + "/comments")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content("{\"body\":\"%s\"}".formatted(body)))
        .andExpect(status().isCreated());
  }

  @Test
  void ingest_landsOnTheBoundBoard_neverBoardless() throws Exception {
    Cookie session = loginAs("kanban-idea@example.com");
    long projectId = createProject("kanban-idea@example.com", "Idea-Dogfood");
    long boardId = createBoard(session, projectId, "Idea-Board");
    String token = boundToken(session, projectId, boardId);

    // Issue #1203: jeder board-token-Ingest legt board-gebunden an. Das ideaStored-Feld ist
    // gegenstandslos (hier bewusst weggelassen — das Ergebnis ist mit/ohne Feld identisch).
    String created =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .contentType("application/json")
                    .content("{\"title\":\"Als Idee\"}"))
            .andExpect(status().isCreated())
            // #402: die Ingest-Antwort enthält die vergebene projektweite Nummer.
            .andExpect(jsonPath("$.number").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long cardId = json.readTree(created).get("id").asLong();
    int cardNumber = json.readTree(created).get("number").asInt();

    // Akzeptanzkriterium: keine angelegte Karte ist board-los — jede traegt Board, Spalte, Nummer.
    long firstColumn = firstColumnId(session, boardId);
    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(cardId))
        .andExpect(jsonPath("$[0].number").value(cardNumber))
        .andExpect(jsonPath("$[0].title").value("Als Idee"))
        .andExpect(jsonPath("$[0].boardId").value(boardId))
        .andExpect(jsonPath("$[0].columnId").value(firstColumn));
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
  void itemsExposeAssignedLabelNamesInBoardOrder() throws Exception {
    Cookie session = loginAs("kanban-labels@example.com");
    long projectId = createProject("kanban-labels@example.com", "Label-Projekt");
    long boardId = createBoard(session, projectId, "Label-Board");
    long columnId = firstColumnId(session, boardId);
    String token = boundToken(session, projectId, boardId);

    long cardId = createCard(session, boardId, columnId, "Mit Labels");

    // findByBoardId ordnet Labels aufsteigend nach Name -> Board-Reihenfolge (Alpha, Beta).
    long alpha = createLabel(session, boardId, "Alpha");
    long beta = createLabel(session, boardId, "Beta");
    // Zuordnung bewusst in umgekehrter Reihenfolge (Beta, Alpha): das Ergebnis muss der
    // Board-Definitionsreihenfolge folgen, nicht der Zuordnungsreihenfolge.
    setCardLabels(session, cardId, beta, alpha);

    // Eine zweite Karte ohne Labels: labels-Feld muss eine leere Liste sein.
    createCard(session, boardId, columnId, "Ohne Labels");

    JsonNode backlog = kanbanItems(token).get("BACKLOG");
    JsonNode withLabels = itemById(backlog, cardId);
    assertThat(withLabels.get("labels").isArray()).isTrue();
    assertThat(withLabels.get("labels")).hasSize(2);
    assertThat(withLabels.get("labels").get(0).asText()).isEqualTo("Alpha");
    assertThat(withLabels.get("labels").get(1).asText()).isEqualTo("Beta");

    JsonNode withoutLabels =
        backlog.valueStream().filter(n -> n.get("id").asLong() != cardId).findFirst().orElseThrow();
    assertThat(withoutLabels.get("labels").isArray()).isTrue();
    assertThat(withoutLabels.get("labels")).isEmpty();
  }

  private long createLabel(Cookie session, long boardId, String name) throws Exception {
    String body =
        mvc.perform(
                post("/api/boards/" + boardId + "/labels")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"color\":\"#123456\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private void setCardLabels(Cookie session, long cardId, long... labelIds) throws Exception {
    StringBuilder ids = new StringBuilder();
    for (int i = 0; i < labelIds.length; i++) {
      ids.append(i == 0 ? "" : ",").append(labelIds[i]);
    }
    mvc.perform(
            put("/api/cards/" + cardId + "/labels")
                .cookie(session)
                .contentType("application/json")
                .content("{\"labels\":[%s]}".formatted(ids)))
        .andExpect(status().isOk());
  }

  private static JsonNode itemById(JsonNode items, long id) {
    return items.valueStream().filter(n -> n.get("id").asLong() == id).findFirst().orElseThrow();
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

    // Eigene Karte auf board1 über die Cookie-API anlegen: sonst wären alle Spalten leer und die
    // Non-Leak-Prüfung unten würde vacuously durchlaufen, ohne etwas zu beweisen (Sonar S5841).
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
   * Eine archivierte Karte ist in der Oberfläche ausgeblendet und darf darum auch über die
   * Compat-Schnittstelle nicht als Aufgabe ihrer Spalte erscheinen. Vor Issue #428 filterte die
   * Schicht zu wenig — Kit und Nacht-Runner sahen eine Aufgabe, die für Menschen nicht existiert.
   *
   * <p>Der Lesepfad ist die Zusage; <b>Bewegen und Kommentieren einer archivierten Karte bleiben
   * möglich</b> (der Board-Guard {@code requireOnBoard} prüft die Board-Bindung, nicht den
   * Archivzustand) — dasselbe Bestandsverhalten wie an der Karten-API, wo eine archivierte Karte
   * bearbeitet und wiederhergestellt werden darf. Die Gegenprobe am Ende pinnt den Schreibpfad für
   * den Fall, den der Guard wirklich abweist: eine Karte auf einem anderen Board.
   */
  @Test
  void archivedCardIsInvisibleForTheAutomation() throws Exception {
    long projectId = createProject("ghost-owner@example.com", "GhostProjekt");
    Cookie owner = loginAs("ghost-owner@example.com");
    long boardId = createBoard(owner, projectId, "GhostBoard");
    long columnId = firstColumnId(owner, boardId);
    String token = boundToken(owner, projectId, boardId);

    long visible = createCard(owner, boardId, columnId, "SichtbareKarte");
    long ghost = createCard(owner, boardId, columnId, "GeisterKarte");

    // Vor dem Archivieren sind beide Karten für die Automatik da.
    assertThat(kanbanItems(token).get("BACKLOG")).hasSize(2);

    mvc.perform(post("/api/cards/" + ghost + "/archive").cookie(owner)).andExpect(status().isOk());

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
        json.readTree(uiCards).valueStream().filter(c -> !c.get("archived").asBoolean()).count();
    assertThat((long) items.get("BACKLOG").size()).isEqualTo(uiVisible);

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
