package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End der vorgegebenen Kartennummer beim Ingest (#565): Ein Import aus einem anderen Tracker
 * behält die Identität seiner Issues, statt neue Nummern zu bekommen.
 *
 * <p>Deckt die vier Ablehnungspfade mit ab, weil bei einer Migration jeder von ihnen
 * stillschweigend die falsche Identität erzeugen würde: fehlendes {@code direct}, fehlender {@code
 * externalKey}, belegte Nummer (auch im Papierkorb) und ein Projekt, das schon vor dem Import
 * gewachsen war.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatGivenNumberIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void ingestWithGivenNumberKeepsIdentityAndCounterMovesAbove() throws Exception {
    Fixture f = fixture("gn-keep");

    // Zwei migrierte Karten mit ihren Original-Nummern, absichtlich mit Luecke dazwischen.
    assertThat(ingestNumbered(f.token, "Issue 278", "github#278", 278)).isEqualTo(278);
    assertThat(ingestNumbered(f.token, "Issue 41", "github#41", 41)).isEqualTo(41);

    // Eine Nummer unterhalb des Maximums fuellt die Luecke.
    assertThat(ingestNumbered(f.token, "Issue 100", "github#100", 100)).isEqualTo(100);

    // Danach zaehlt die automatische Vergabe oberhalb der hoechsten importierten Nummer weiter.
    int next = ingestAuto(f.token, "Ohne Vorgabe", "github#next");
    assertThat(next).isGreaterThan(278);
  }

  @Test
  void repeatedIngestWithSameKeyAndNumberIsIdempotent() throws Exception {
    Fixture f = fixture("gn-idem");
    long first = ingestNumberedId(f.token, "Issue 7", "github#7", 7, true);

    long second = ingestNumberedId(f.token, "Issue 7 erneut", "github#7", 7, false);

    assertThat(second).isEqualTo(first);
  }

  @Test
  void ingestWithSameKeyButDifferentNumberIsConflict() throws Exception {
    Fixture f = fixture("gn-keymismatch");
    ingestNumbered(f.token, "Issue 7", "github#7", 7);

    // Der Schluessel trifft, die angeforderte Identitaet weicht ab: kein stilles Zurueckgeben.
    postNumbered(f.token, "Issue 7 falsch", "github#7", 8).andExpect(status().isConflict());
  }

  @Test
  void takenNumberIsConflict() throws Exception {
    Fixture f = fixture("gn-taken");
    ingestNumbered(f.token, "Issue 12", "github#12", 12);

    postNumbered(f.token, "Kollision", "github#other", 12).andExpect(status().isConflict());
  }

  @Test
  void numberOfTrashedCardStaysTaken() throws Exception {
    // Der Unique-Constraint kennt keinen Papierkorb: eine verworfene Karte belegt ihre Nummer
    // weiterhin. Wer hier die deleted_at-filternde Suche verwendet, laesst die Kollision durch.
    Fixture f = fixture("gn-trash");
    long id = ingestNumberedId(f.token, "Issue 55", "github#55", 55, true);
    mvc.perform(delete("/api/cards/" + id).cookie(f.owner)).andExpect(status().isNoContent());

    postNumbered(f.token, "Wiederverwendung", "github#55b", 55).andExpect(status().isConflict());
  }

  @Test
  void numberOfArchivedCardStaysTaken() throws Exception {
    Fixture f = fixture("gn-archived");
    long id = ingestNumberedId(f.token, "Issue 66", "github#66", 66, true);
    mvc.perform(post("/api/cards/" + id + "/archive").cookie(f.owner)).andExpect(status().isOk());

    postNumbered(f.token, "Wiederverwendung", "github#66b", 66).andExpect(status().isConflict());
  }

  @Test
  void projectWithImportForeignCardRejectsGivenNumber() throws Exception {
    // Vorbedingung: in ein gewachsenes Projekt wird nicht hineinimportiert. Die Karte ohne
    // externalKey entsteht hier ueber denselben Ingest ohne Schluessel.
    Fixture f = fixture("gn-grown");
    ingestAuto(f.token, "Gewachsene Karte", null);

    postNumbered(f.token, "Migriert", "github#1", 1).andExpect(status().isConflict());
  }

  @Test
  void archivedImportForeignCardStillBlocksTheImport() throws Exception {
    // Die Vorbedingung muss archivierte Karten mitzaehlen. Ohne diesen Fall bliebe ein
    // versehentliches `deleted_at IS NULL` in hasCardWithoutExternalKey unentdeckt — das Projekt
    // saehe leer aus, obwohl es gewachsen ist.
    Fixture f = fixture("gn-grown-archived");
    long id = ingestAuto2(f.token, "Gewachsene Karte");
    mvc.perform(post("/api/cards/" + id + "/archive").cookie(f.owner)).andExpect(status().isOk());

    postNumbered(f.token, "Migriert", "github#1", 1).andExpect(status().isConflict());
  }

  @Test
  void trashedImportForeignCardStillBlocksTheImport() throws Exception {
    Fixture f = fixture("gn-grown-trashed");
    long id = ingestAuto2(f.token, "Gewachsene Karte");
    mvc.perform(delete("/api/cards/" + id).cookie(f.owner)).andExpect(status().isNoContent());

    postNumbered(f.token, "Migriert", "github#1", 1).andExpect(status().isConflict());
  }

  @Test
  void numberAboveMaximumIsBadRequest() throws Exception {
    // Ohne Obergrenze liesse ein einziger Import mit Integer.MAX_VALUE jede spaetere automatische
    // Anlage im Projekt an MAX(number)+1 scheitern.
    Fixture f = fixture("gn-max");

    mvc.perform(
            post("/api/kanban/items")
                .header("X-Kanban-Token", f.token)
                .contentType("application/json")
                .content(
                    "{\"title\":\"X\",\"externalKey\":\"github#max\",\"number\":%d,\"direct\":true}"
                        .formatted(Integer.MAX_VALUE)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void givenNumberWithoutDirectIsBadRequest() throws Exception {
    Fixture f = fixture("gn-nodirect");

    mvc.perform(
            post("/api/kanban/items")
                .header("X-Kanban-Token", f.token)
                .contentType("application/json")
                .content(
                    "{\"title\":\"X\",\"externalKey\":\"github#1\",\"number\":1,\"direct\":false}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void givenNumberWithoutExternalKeyIsBadRequest() throws Exception {
    Fixture f = fixture("gn-nokey");

    mvc.perform(
            post("/api/kanban/items")
                .header("X-Kanban-Token", f.token)
                .contentType("application/json")
                .content("{\"title\":\"X\",\"number\":1,\"direct\":true}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void concurrentIngestOfSameNumberYieldsExactlyOneCard() throws Exception {
    // Die Sperre bleibt, obwohl die Berechnung entfaellt: sonst legen zwei gleichzeitige Importe
    // dieselbe Nummer an und der Unique-Constraint entscheidet, wer 500 bekommt.
    Fixture f = fixture("gn-race");
    CountDownLatch start = new CountDownLatch(1);
    int first;
    int second;
    // ExecutorService ist seit Java 19 AutoCloseable — try-with-resources wartet beim Schliessen
    // auf die Beendigung und spart das finally.
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Integer> a = pool.submit(() -> raceStatus(f.token, "A", "github#a", start));
      Future<Integer> b = pool.submit(() -> raceStatus(f.token, "B", "github#b", start));
      start.countDown();
      first = a.get(30, TimeUnit.SECONDS);
      second = b.get(30, TimeUnit.SECONDS);
    }

    // Genau einer legt an, genau einer bekommt den Konflikt — und in der Datenbank steht eine
    // einzige Karte mit dieser Nummer.
    assertThat(List.of(first, second))
        .containsExactlyInAnyOrder(HttpStatus.CREATED.value(), HttpStatus.CONFLICT.value());
    assertThat(countByNumber(f.projectId, 900)).isOne();
  }

  /**
   * Setzt denselben nummerierten Ingest ab, sobald das Startsignal fällt, und liefert den Status.
   */
  private int raceStatus(String token, String title, String key, CountDownLatch start)
      throws Exception {
    start.await();
    return mvc.perform(
            post("/api/kanban/items")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content(
                    "{\"title\":\"%s\",\"externalKey\":\"%s\",\"number\":900,\"direct\":true}"
                        .formatted(title, key)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private record Fixture(Cookie owner, String token, long projectId) {}

  private Fixture fixture(String prefix) throws Exception {
    Cookie owner = session(prefix + "-owner@example.com", PlatformRole.USER);
    Cookie admin = session(prefix + "-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "Projekt " + prefix, prefix + "-owner@example.com");
    JsonNode board = createBoard(owner, projectId, "Board");
    return new Fixture(owner, boundToken(owner, projectId, board.get("id").asLong()), projectId);
  }

  private org.springframework.test.web.servlet.ResultActions postNumbered(
      String token, String title, String key, int number) throws Exception {
    return mvc.perform(
        post("/api/kanban/items")
            .header("X-Kanban-Token", token)
            .contentType("application/json")
            .content(
                "{\"title\":\"%s\",\"externalKey\":\"%s\",\"number\":%d,\"direct\":true}"
                    .formatted(title, key, number)));
  }

  private int ingestNumbered(String token, String title, String key, int number) throws Exception {
    String body =
        postNumbered(token, title, key, number)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("number").asInt();
  }

  private long ingestNumberedId(
      String token, String title, String key, int number, boolean expectCreated) throws Exception {
    String body =
        postNumbered(token, title, key, number)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.created").value(expectCreated))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private int ingestAuto(String token, String title, String key) throws Exception {
    String keyPart = key == null ? "" : ",\"externalKey\":\"%s\"".formatted(key);
    String body =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .contentType("application/json")
                    .content("{\"title\":\"%s\"%s,\"direct\":true}".formatted(title, keyPart)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("number").asInt();
  }

  /** Ingest ohne Schluessel und ohne Nummer — erzeugt eine importfremde Karte, liefert deren id. */
  private long ingestAuto2(String token, String title) throws Exception {
    String body =
        mvc.perform(
                post("/api/kanban/items")
                    .header("X-Kanban-Token", token)
                    .contentType("application/json")
                    .content("{\"title\":\"%s\",\"direct\":true}".formatted(title)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private int countByNumber(long projectId, int number) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM card WHERE project_id = ? AND number = ?",
            Integer.class,
            projectId,
            number);
    return count == null ? 0 : count;
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
