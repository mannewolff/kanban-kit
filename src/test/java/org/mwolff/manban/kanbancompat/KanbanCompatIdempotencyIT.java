package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TransactionRace;
import org.mwolff.manban.accesstoken.application.AccessTokenService;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.kanbancompat.application.IdempotencyRecordStore;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService;
import org.mwolff.manban.kanbancompat.application.KanbanCompatService.Created;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Idempotenz der anlegenden Compat-Befehle gegen echtes PostgreSQL (Issue #1001, Plan #995
 * E7/E8/E9): Derselbe {@code Idempotency-Key} erzeugt eine Wirkung und beliebig viele gleiche
 * Antworten — auch bei gleichzeitiger Wiederholung.
 *
 * <p>Die beiden Rennen laufen über {@link TransactionRace} auf Service-Ebene und nicht über zwei
 * HTTP-Threads: Nur so ist <em>nachgewiesen</em>, dass der zweite Aufruf am Schlüssel des ersten
 * wartet, statt zufällig erst nach dessen Commit zu kommen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatIdempotencyIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String HEADER = "Idempotency-Key";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccessTokenService accessTokens;
  @Autowired private KanbanCompatService service;
  @Autowired private IdempotencyRecordStore store;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  /** Ein Projekt mit Board und daran gebundenem Token. */
  private record Setup(long projectId, long boardId, String token) {}

  @Test
  void concurrentCreates_withTheSameKey_makeOneCard_andTwoEqualAnswers() throws Exception {
    Setup s = setup("race-create");
    KanbanPrincipal principal = principal(s.token());
    AtomicReference<Created> first = new AtomicReference<>();
    AtomicReference<Created> second = new AtomicReference<>();

    TransactionRace.Result race =
        new TransactionRace(transactionManager, dataSource)
            .run(
                () -> first.set(createVia(principal, "k-race")),
                () -> second.set(createVia(principal, "k-race")));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(second.get()).isEqualTo(first.get());
    assertThat(cardsTitled(s.projectId(), "Rennen")).isEqualTo(1);
  }

  @Test
  void concurrentComments_withTheSameKey_makeOneComment() throws Exception {
    Setup s = setup("race-comment");
    long cardId = directCard(s.token(), "Ziel");
    KanbanPrincipal principal = principal(s.token());

    TransactionRace.Result race =
        new TransactionRace(transactionManager, dataSource)
            .run(
                () -> service.comment(principal, cardId, "Einmal", "k-comment"),
                () -> service.comment(principal, cardId, "Einmal", "k-comment"));

    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure()).isNull();
    assertThat(comments(cardId)).isEqualTo(1);
  }

  @Test
  void repeatedCreate_answersByteForByte_withTheSameStatus() throws Exception {
    Setup s = setup("replay");

    MockHttpServletResponse first = create(s.token(), "{\"title\":\"Einmal\"}", "k-1");
    MockHttpServletResponse again = create(s.token(), "{\"title\":\"Einmal\"}", "k-1");

    assertThat(again.getStatus()).isEqualTo(201).isEqualTo(first.getStatus());
    assertThat(again.getContentAsByteArray()).isEqualTo(first.getContentAsByteArray());
    assertThat(cardsTitled(s.projectId(), "Einmal")).isEqualTo(1);
  }

  @Test
  void differentKeys_withEqualContent_makeTwoComments() throws Exception {
    Setup s = setup("two-comments");
    long cardId = directCard(s.token(), "Ziel");

    comment(s.token(), cardId, "Gleicher Text", "k-a");
    comment(s.token(), cardId, "Gleicher Text", "k-b");

    assertThat(comments(cardId)).isEqualTo(2);
  }

  @Test
  void withoutKey_everythingStaysAsBefore() throws Exception {
    Setup s = setup("no-key");
    long cardId = directCard(s.token(), "Ziel");

    create(s.token(), "{\"title\":\"Frei\"}", null);
    create(s.token(), "{\"title\":\"Frei\"}", null);
    comment(s.token(), cardId, "Frei", null);
    comment(s.token(), cardId, "Frei", null);

    assertThat(cardsTitled(s.projectId(), "Frei")).isEqualTo(2);
    assertThat(comments(cardId)).isEqualTo(2);
    assertThat(records()).isZero();
  }

  @Test
  void externalKey_takesPrecedence_overTheIdempotencyKey() throws Exception {
    Setup s = setup("extkey");

    // Gleicher Idempotenz-Schlüssel, verschiedene externalKeys: zwei Karten — der technische
    // Schlüssel greift gar nicht erst (E8).
    create(s.token(), "{\"title\":\"A\",\"externalKey\":\"ext:1\"}", "k-same");
    create(s.token(), "{\"title\":\"B\",\"externalKey\":\"ext:2\"}", "k-same");
    // Gleicher externalKey, verschiedene Idempotenz-Schlüssel: eine Karte, der fachliche
    // Schlüssel entscheidet.
    JsonNode repeated =
        json.readTree(
            create(s.token(), "{\"title\":\"A2\",\"externalKey\":\"ext:1\"}", "k-other")
                .getContentAsString());

    assertThat(cardsTitled(s.projectId(), "A")).isEqualTo(1);
    assertThat(cardsTitled(s.projectId(), "B")).isEqualTo(1);
    assertThat(repeated.get("created").asBoolean()).isFalse();
    assertThat(records()).isZero();
  }

  @Test
  void sameKey_inTwoProjects_isValidTwice() throws Exception {
    Setup a = setup("project-a");
    Setup b = setup("project-b");

    create(a.token(), "{\"title\":\"Doppelt\"}", "k-shared");
    create(b.token(), "{\"title\":\"Doppelt\"}", "k-shared");

    assertThat(cardsTitled(a.projectId(), "Doppelt")).isEqualTo(1);
    assertThat(cardsTitled(b.projectId(), "Doppelt")).isEqualTo(1);
  }

  @Test
  void keyOfAnotherCommand_isConflict_andCreatesNothing() throws Exception {
    Setup s = setup("reuse");
    long cardId = directCard(s.token(), "Ziel");
    create(s.token(), "{\"title\":\"Erst\"}", "k-reused");

    mvc.perform(
            post("/api/kanban/items/" + cardId + "/comments")
                .header("X-Kanban-Token", s.token())
                .header(HEADER, "k-reused")
                .contentType("application/json")
                .content("{\"body\":\"Zweitverwendung\"}"))
        .andExpect(status().isConflict());

    assertThat(comments(cardId)).isZero();
  }

  @Test
  void failedCommand_leavesNoKey_soTheRetryCanSucceed() throws Exception {
    // Given: Der erste Versuch scheitert fachlich (DONE ist beim direct-Ingest ausgeschlossen).
    // Liefe die Ablage des Schlüssels nicht in derselben Transaktion, bliebe er stehen und die
    // Wiederholung bekäme eine Antwort, die es nie gab.
    Setup s = setup("rollback");
    mvc.perform(
            post("/api/kanban/items")
                .header("X-Kanban-Token", s.token())
                .header(HEADER, "k-retry")
                .contentType("application/json")
                .content("{\"title\":\"Später\",\"direct\":true,\"column\":\"DONE\"}"))
        .andExpect(status().is4xxClientError());

    create(s.token(), "{\"title\":\"Später\"}", "k-retry");

    assertThat(cardsTitled(s.projectId(), "Später")).isEqualTo(1);
  }

  @Test
  void overlongKey_isRejected() throws Exception {
    Setup s = setup("overlong");

    mvc.perform(
            post("/api/kanban/items")
                .header("X-Kanban-Token", s.token())
                .header(HEADER, "k".repeat(201))
                .contentType("application/json")
                .content("{\"title\":\"Zu lang\"}"))
        .andExpect(status().isBadRequest());

    assertThat(cardsTitled(s.projectId(), "Zu lang")).isZero();
  }

  @Test
  void deleteOlderThan_removesOnlyRecordsBeforeTheCutoff() throws Exception {
    Setup s = setup("retention");
    Instant now = Instant.now();
    record(s.projectId(), "old", now.minus(Duration.ofHours(25)));
    record(s.projectId(), "young", now.minus(Duration.ofHours(23)));

    int deleted = store.deleteOlderThan(now.minus(Duration.ofHours(24)));

    assertThat(deleted).isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "SELECT key FROM idempotency_record WHERE project_id = ?",
                String.class,
                s.projectId()))
        .containsExactly("young");
  }

  // --- Fixtures ------------------------------------------------------------

  private Created createVia(KanbanPrincipal principal, String key) {
    return service.create(principal, "Rennen", null, null, null, false, null, null, key);
  }

  private KanbanPrincipal principal(String token) {
    return accessTokens.resolveBinding(token).orElseThrow();
  }

  private MockHttpServletResponse create(String token, String body, @Nullable String key)
      throws Exception {
    var request =
        post("/api/kanban/items")
            .header("X-Kanban-Token", token)
            .contentType("application/json")
            .content(body);
    if (key != null) {
      request.header(HEADER, key);
    }
    return mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse();
  }

  private void comment(String token, long cardId, String text, @Nullable String key)
      throws Exception {
    var request =
        post("/api/kanban/items/" + cardId + "/comments")
            .header("X-Kanban-Token", token)
            .contentType("application/json")
            .content("{\"body\":\"%s\"}".formatted(text));
    if (key != null) {
      request.header(HEADER, key);
    }
    mvc.perform(request).andExpect(status().isCreated());
  }

  private long directCard(String token, String title) throws Exception {
    return json.readTree(
            create(token, "{\"title\":\"%s\",\"direct\":true}".formatted(title), null)
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private void record(long projectId, String key, Instant createdAt) {
    jdbc.update(
        "INSERT INTO idempotency_record (project_id, key, endpoint, response_status, created_at)"
            + " VALUES (?, ?, 'POST /items', 201, ?)",
        projectId,
        key,
        Timestamp.from(createdAt));
  }

  private int cardsTitled(long projectId, String title) {
    return count("SELECT count(*) FROM card WHERE project_id = ? AND title = ?", projectId, title);
  }

  private int comments(long cardId) {
    return count("SELECT count(*) FROM comment WHERE card_id = ?", cardId);
  }

  private int records() {
    return count("SELECT count(*) FROM idempotency_record");
  }

  private int count(String sql, Object... args) {
    Integer n = jdbc.queryForObject(sql, Integer.class, args);
    return n == null ? 0 : n;
  }

  private Setup setup(String name) throws Exception {
    String ownerEmail = name + "-owner@example.com";
    Cookie owner = session(ownerEmail, PlatformRole.USER);
    Cookie admin = session(name + "-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, name, ownerEmail);
    long boardId = createBoard(owner, projectId, "Board").get("id").asLong();
    return new Setup(projectId, boardId, boundToken(owner, projectId, boardId));
  }

  private String boundToken(Cookie session, long projectId, long boardId) throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"sync-token\",\"projectId\":%d,\"boardId\":%d}"
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
