package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardChangedEvent;
import org.mwolff.manban.board.application.BoardChangedEvent.ChangeType;
import org.mwolff.manban.board.web.BoardEventRegistry;
import org.mwolff.manban.common.TextLimits;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End des board-gebundenen Stapel-Anlegens (Issue #1200): Ort und Reihenfolge der Karten,
 * Alles-oder-nichts, Mengengrenzen, Rechte, Done-Spalte als Ziel und ein SSE-Ereignis je Karte.
 *
 * <p>Der Einzelweg {@code POST /api/boards/{boardId}/cards} kommt mit, soweit ihn die
 * Done-Entscheidung (E4) im gemeinsamen Anlegepfad berührt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class BoardCardBatchIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @MockitoSpyBean private BoardEventRegistry registry;

  @Test
  void batch_appendsCardsToColumnEnd_inInputOrder() throws Exception {
    Cookie owner = session("batch-card-order@example.com", PlatformRole.USER);
    long projectId = createProject("batch-card-order@example.com");
    JsonNode board = createBoard(owner, projectId);
    long boardId = board.get("id").asLong();
    long backlog = columnId(board, 0);

    // Bestand: eine einzeln angelegte Karte, an der die Stapel-Karten vorbeiziehen muessen.
    JsonNode bestand = createCard(owner, boardId, backlog, "Bestand");

    JsonNode created =
        json.readTree(
            mvc.perform(
                    post("/api/boards/" + boardId + "/cards/batch")
                        .cookie(owner)
                        .contentType("application/json")
                        .content(
                            """
                            {"columnId":%d,
                             "cards":[{"title":"Kapitel 1","description":"Text 1"},
                                      {"title":"Kapitel 2","description":"Text 2"},
                                      {"title":"Kapitel 3"}]}
                            """
                                .formatted(backlog)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].title").value("Kapitel 1"))
                .andExpect(jsonPath("$[0].description").value("Text 1"))
                .andExpect(jsonPath("$[2].description").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString());

    int bestandsnummer = bestand.get("number").asInt();
    int bestandsposition = bestand.get("positionInColumn").asInt();
    for (int i = 0; i < 3; i++) {
      JsonNode karte = created.get(i);
      assertThat(karte.get("boardId").asLong()).isEqualTo(boardId);
      assertThat(karte.get("columnId").asLong()).isEqualTo(backlog);
      // Fortlaufende Kartennummern in Eingabereihenfolge.
      assertThat(karte.get("number").asInt()).isEqualTo(bestandsnummer + 1 + i);
      // Am Ende der Spalte: hinter dem Bestand, untereinander aufsteigend.
      assertThat(karte.get("positionInColumn").asInt()).isGreaterThan(bestandsposition);
      if (i > 0) {
        assertThat(karte.get("positionInColumn").asInt())
            .isGreaterThan(created.get(i - 1).get("positionInColumn").asInt());
      }
      // Nicht erledigt: die Zielspalte ist keine Done-Spalte.
      assertThat(karte.get("movedToDoneAt").isNull()).isTrue();
    }

    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(jsonPath("$.length()").value(4));
  }

  @Test
  void batch_withOneInvalidItem_createsNothing() throws Exception {
    Cookie owner = session("batch-card-invalid@example.com", PlatformRole.USER);
    long projectId = createProject("batch-card-invalid@example.com");
    JsonNode board = createBoard(owner, projectId);
    long boardId = board.get("id").asLong();
    long backlog = columnId(board, 0);

    // Alles-oder-nichts: ein ueberlanger Titel im zweiten Element verwirft den ganzen Stapel.
    mvc.perform(
            post("/api/boards/" + boardId + "/cards/batch")
                .cookie(owner)
                .contentType("application/json")
                .content(
                    "{\"columnId\":%d,\"cards\":[{\"title\":\"Gut\"},{\"title\":\"%s\"}]}"
                        .formatted(backlog, "t".repeat(301))))
        .andExpect(status().isBadRequest());

    // Ein leerer Titel und eine zu lange Beschreibung ebenso.
    mvc.perform(
            post("/api/boards/" + boardId + "/cards/batch")
                .cookie(owner)
                .contentType("application/json")
                .content(
                    "{\"columnId\":%d,\"cards\":[{\"title\":\"Gut\"},{\"title\":\"   \"}]}"
                        .formatted(backlog)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/boards/" + boardId + "/cards/batch")
                .cookie(owner)
                .contentType("application/json")
                .content(
                    "{\"columnId\":%d,\"cards\":[{\"title\":\"Gut\",\"description\":\"%s\"}]}"
                        .formatted(backlog, "d".repeat(TextLimits.MAX_TEXT + 1))))
        .andExpect(status().isBadRequest());

    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void batch_rejectsEmptyAndOversizedList() throws Exception {
    Cookie owner = session("batch-card-limit@example.com", PlatformRole.USER);
    long projectId = createProject("batch-card-limit@example.com");
    JsonNode board = createBoard(owner, projectId);
    long boardId = board.get("id").asLong();
    long backlog = columnId(board, 0);

    mvc.perform(
            post("/api/boards/" + boardId + "/cards/batch")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"cards\":[]}".formatted(backlog)))
        .andExpect(status().isBadRequest());

    mvc.perform(
            post("/api/boards/" + boardId + "/cards/batch")
                .cookie(owner)
                .contentType("application/json")
                .content(body(backlog, CardController.MAX_CARDS_PER_BATCH + 1)))
        .andExpect(status().isBadRequest());

    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(jsonPath("$.length()").value(0));

    // Die Grenze selbst geht noch durch.
    mvc.perform(
            post("/api/boards/" + boardId + "/cards/batch")
                .cookie(owner)
                .contentType("application/json")
                .content(body(backlog, CardController.MAX_CARDS_PER_BATCH)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.length()").value(CardController.MAX_CARDS_PER_BATCH));

    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(jsonPath("$.length()").value(CardController.MAX_CARDS_PER_BATCH));
  }

  @Test
  void batch_forbiddenForMemberWithoutTicketCreate() throws Exception {
    Cookie owner = session("batch-card-rights@example.com", PlatformRole.USER);
    Cookie viewer = session("batch-card-viewer@example.com", PlatformRole.USER);
    long projectId = createProject("batch-card-rights@example.com");
    JsonNode board = createBoard(owner, projectId);
    long boardId = board.get("id").asLong();
    long backlog = columnId(board, 0);
    memberships.save(
        new ProjectMembership(
            null,
            projectId,
            userId("batch-card-viewer@example.com"),
            ProjectRole.VIEWER,
            Instant.now()));

    mvc.perform(
            post("/api/boards/" + boardId + "/cards/batch")
                .cookie(viewer)
                .contentType("application/json")
                .content(
                    "{\"columnId\":%d,\"cards\":[{\"title\":\"Kapitel\"}]}".formatted(backlog)))
        .andExpect(status().isForbidden());

    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void batch_intoDoneColumn_setsMovedToDoneAt() throws Exception {
    Cookie owner = session("batch-card-done@example.com", PlatformRole.USER);
    long projectId = createProject("batch-card-done@example.com");
    JsonNode board = createBoard(owner, projectId);
    long boardId = board.get("id").asLong();
    long done = columnId(board, 4);

    mvc.perform(
            post("/api/boards/" + boardId + "/cards/batch")
                .cookie(owner)
                .contentType("application/json")
                .content(
                    """
                    {"columnId":%d,"cards":[{"title":"Fertig 1"},{"title":"Fertig 2"}]}
                    """
                        .formatted(done)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$[0].movedToDoneAt").isNotEmpty())
        .andExpect(jsonPath("$[1].movedToDoneAt").isNotEmpty());
  }

  @Test
  void singleCreate_intoDoneColumn_setsMovedToDoneAt() throws Exception {
    Cookie owner = session("single-card-done@example.com", PlatformRole.USER);
    long projectId = createProject("single-card-done@example.com");
    JsonNode board = createBoard(owner, projectId);
    long boardId = board.get("id").asLong();

    JsonNode inDone = createCard(owner, boardId, columnId(board, 4), "Direkt fertig");
    assertThat(inDone.get("movedToDoneAt").isNull()).isFalse();

    // Gegenprobe: ausserhalb einer Done-Spalte bleibt der Zeitpunkt leer.
    JsonNode imBacklog = createCard(owner, boardId, columnId(board, 0), "Noch offen");
    assertThat(imBacklog.get("movedToDoneAt").isNull()).isTrue();
  }

  @Test
  void batch_deliversOneEventPerCreatedCard() throws Exception {
    Cookie owner = session("batch-card-events@example.com", PlatformRole.USER);
    long projectId = createProject("batch-card-events@example.com");
    JsonNode board = createBoard(owner, projectId);
    long boardId = board.get("id").asLong();
    long backlog = columnId(board, 0);
    clearInvocations(registry);

    JsonNode created =
        json.readTree(
            mvc.perform(
                    post("/api/boards/" + boardId + "/cards/batch")
                        .cookie(owner)
                        .contentType("application/json")
                        .content(
                            """
                            {"columnId":%d,"cards":[{"title":"A"},{"title":"B"},{"title":"C"}]}
                            """
                                .formatted(backlog)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

    ArgumentCaptor<BoardChangedEvent> captor = ArgumentCaptor.forClass(BoardChangedEvent.class);
    verify(registry, times(3)).publish(anyLong(), captor.capture());
    List<BoardChangedEvent> erwartet =
        IntStream.range(0, 3)
            .mapToObj(
                i ->
                    new BoardChangedEvent(
                        boardId, ChangeType.CREATED, created.get(i).get("id").asLong()))
            .toList();
    assertThat(captor.getAllValues()).containsExactlyElementsOf(erwartet);
  }

  @Test
  void batch_rolledBackTransaction_deliversNoEvent() throws Exception {
    Cookie owner = session("batch-card-rollback@example.com", PlatformRole.USER);
    long projectId = createProject("batch-card-rollback@example.com");
    JsonNode board = createBoard(owner, projectId);
    long boardId = board.get("id").asLong();
    long fremdeSpalte = columnId(createBoard(owner, projectId), 0);
    clearInvocations(registry);

    // Spalte eines anderen Boards: der Stapel scheitert, nichts entsteht, nichts wird gemeldet.
    mvc.perform(
            post("/api/boards/" + boardId + "/cards/batch")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"cards\":[{\"title\":\"A\"}]}".formatted(fremdeSpalte)))
        .andExpect(status().isNotFound());

    verify(registry, times(0)).publish(anyLong(), any());
    mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(owner))
        .andExpect(jsonPath("$.length()").value(0));
  }

  private static String body(long columnId, int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> "{\"title\":\"Kapitel %d\"}".formatted(i))
        .collect(Collectors.joining(",", "{\"columnId\":%d,\"cards\":[".formatted(columnId), "]}"));
  }

  /** Default-Spalten: [0]=Backlog, [1]=Ready, [2]=In Progress, [3]=In Review, [4]=Done. */
  private static long columnId(JsonNode board, int index) {
    return board.get("columns").get(index).get("id").asLong();
  }

  private JsonNode createCard(Cookie owner, long boardId, long columnId, String title)
      throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(owner)
                    .contentType("application/json")
                    .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private long createProject(String ownerEmail) throws Exception {
    Cookie admin = session("batch-card-admin@example.com", PlatformRole.ADMIN);
    return json.readTree(
            mvc.perform(
                    post("/api/projects")
                        .cookie(admin)
                        .contentType("application/json")
                        .content("{\"name\":\"P\",\"ownerEmail\":\"%s\"}".formatted(ownerEmail)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private JsonNode createBoard(Cookie owner, long projectId) throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(owner)
                    .contentType("application/json")
                    .content("{\"name\":\"B\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
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
