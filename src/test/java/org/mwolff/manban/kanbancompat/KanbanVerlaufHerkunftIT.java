package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Verlauf und Kommentar-Herkunft am projektgebundenen Zugang (Issue #1623, Plan #1617, fachlich
 * #1612): Spaltenschlüssel und Herkunft jedes Zugs und jedes Kommentars, unverändert über das
 * Archivieren hinweg, „unbekannt“ statt geraten bei Alt-Einträgen und kein Verlauf aus dem
 * Papierkorb.
 *
 * <p>Alt-Einträge werden per SQL eingefügt: Ein {@code MOVED} mit eigenem Spaltennamen und ohne
 * {@code status_after} und ein Kommentar ohne Herkunft entstehen über keinen heutigen Schreibweg.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanVerlaufHerkunftIT extends AbstractIntegrationTest {

  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String TOKEN_NAME = "verlauf-token";
  private static final String AGENT = "claude-opus-5-5";
  private static final String MOVED = "MOVED";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  // --- Spaltenschlüssel und Herkunft, über das Archivieren hinweg (Kriterien 1–4, 7) -------

  @Test
  void verlaufUndKommentare_tragenSchluesselUndHerkunft_vorUndNachDemArchivieren()
      throws Exception {
    Fixture f = fixture("vh-archiv", "Erika Muster");
    long id = ingest(f.token, "Karte mit Geschichte");
    sessionMove(f.owner, id, columnIdByName(f.owner, f.boardId, "Ready"));
    tokenMove(f.token, id, "IN_PROGRESS");
    sessionComment(f.owner, id, "Von Hand");
    tokenComment(f.token, id, "Vom Werkzeug");

    JsonNode verlaufVorher = activity(f.token, id, status().isOk());
    JsonNode kommentareVorher = comments(f.token, id, status().isOk());

    List<JsonNode> zuege = new ArrayList<>();
    verlaufVorher.forEach(
        a -> {
          if (MOVED.equals(a.get("type").asText())) {
            zuege.add(a);
          }
        });
    assertThat(zuege).hasSize(2);
    JsonNode vonHand = zuege.get(0);
    assertThat(vonHand.get("columnKey").asText()).isEqualTo("READY");
    assertThat(vonHand.get("origin").asText()).isEqualTo("SESSION");
    assertThat(vonHand.get("actorName").asText()).isEqualTo("Erika Muster");
    assertThat(vonHand.get("tokenName").isNull()).isTrue();
    assertThat(vonHand.get("agent").isNull()).isTrue();
    assertThat(vonHand.get("createdAt").isNull()).isFalse();
    JsonNode vomWerkzeug = zuege.get(1);
    assertThat(vomWerkzeug.get("columnKey").asText()).isEqualTo("IN_PROGRESS");
    assertThat(vomWerkzeug.get("origin").asText()).isEqualTo("TOKEN");
    assertThat(vomWerkzeug.get("actorName").asText()).isEqualTo("Erika Muster");
    assertThat(vomWerkzeug.get("tokenName").asText()).isEqualTo(TOKEN_NAME);
    assertThat(vomWerkzeug.get("agent").asText()).isEqualTo(AGENT);

    assertThat(kommentareVorher).hasSize(2);
    JsonNode handKommentar = kommentareVorher.get(0);
    assertThat(handKommentar.get("body").asText()).isEqualTo("Von Hand");
    assertThat(handKommentar.get("origin").asText()).isEqualTo("SESSION");
    assertThat(handKommentar.get("tokenName").isNull()).isTrue();
    assertThat(handKommentar.get("agent").isNull()).isTrue();
    JsonNode werkzeugKommentar = kommentareVorher.get(1);
    assertThat(werkzeugKommentar.get("body").asText()).isEqualTo("Vom Werkzeug");
    assertThat(werkzeugKommentar.get("origin").asText()).isEqualTo("TOKEN");
    assertThat(werkzeugKommentar.get("tokenName").asText()).isEqualTo(TOKEN_NAME);
    assertThat(werkzeugKommentar.get("agent").asText()).isEqualTo(AGENT);

    archive(f.owner, id);

    // Das Archivieren selbst darf einen eigenen Eintrag anhängen; die Einträge davor bleiben
    // unverändert, die Kommentare vollständig gleich (Kriterium 4).
    JsonNode verlaufNachher = activity(f.token, id, status().isOk());
    List<JsonNode> vorher = new ArrayList<>();
    verlaufVorher.forEach(vorher::add);
    List<JsonNode> nachher = new ArrayList<>();
    verlaufNachher.forEach(nachher::add);
    assertThat(nachher.subList(0, vorher.size())).isEqualTo(vorher);
    assertThat(comments(f.token, id, status().isOk())).isEqualTo(kommentareVorher);
    JsonNode einzelabruf = byNumber(f.token, numberOf(id));
    assertThat(einzelabruf.get("archived").asBoolean()).isTrue();
    assertThat(einzelabruf.get("comments")).isEqualTo(kommentareVorher);
  }

  // --- Kein geratener Spaltenschlüssel (Kriterium 8) -------------------------------------

  @Test
  void altEintragMitEigenemSpaltennamen_ohneStatusAfter_traegtKeinenSpaltenschluessel()
      throws Exception {
    Fixture f = fixture("vh-alt-zug", "Alt Zug");
    long id = ingest(f.token, "Alte Karte");
    jdbc.update(
        "INSERT INTO card_activity (card_id, actor_user_id, type, detail, created_at)"
            + " VALUES (?, NULL, 'MOVED', 'Verschoben nach Warteschlange', now())",
        id);

    JsonNode verlauf = activity(f.token, id, status().isOk());

    JsonNode alt = null;
    for (JsonNode a : verlauf) {
      if (MOVED.equals(a.get("type").asText())) {
        alt = a;
      }
    }
    assertThat(alt).isNotNull();
    assertThat(alt.get("detail").asText()).isEqualTo("Verschoben nach Warteschlange");
    assertThat(alt.get("columnKey").isNull()).isTrue();
    assertThat(alt.get("origin").isNull()).isTrue();
    assertThat(alt.get("actorName").isNull()).isTrue();
  }

  // --- Herkunft unbekannt statt geraten (Kriterium 8) ------------------------------------

  @Test
  void altKommentarOhneHerkunft_meldetHerkunftUnbekannt() throws Exception {
    Fixture f = fixture("vh-alt-kommentar", "Alt Kommentar");
    long id = ingest(f.token, "Karte mit Alt-Kommentar");
    jdbc.update(
        "INSERT INTO comment (card_id, author_user_id, author_name, body)"
            + " VALUES (?, NULL, 'Früher', 'Vor der Einführung')",
        id);

    JsonNode kommentare = comments(f.token, id, status().isOk());

    assertThat(kommentare).hasSize(1);
    JsonNode alt = kommentare.get(0);
    assertThat(alt.get("author").asText()).isEqualTo("Früher");
    assertThat(alt.get("origin").isNull()).isTrue();
    assertThat(alt.get("tokenName").isNull()).isTrue();
    assertThat(alt.get("agent").isNull()).isTrue();
  }

  // --- Papierkorb (Kriterium 6) ---------------------------------------------------------

  @Test
  void karteImPapierkorb_liefertWederVerlaufNochKommentare() throws Exception {
    Fixture f = fixture("vh-papierkorb", "Papier Korb");
    long id = ingest(f.token, "Gelöscht");
    tokenComment(f.token, id, "Noch da");
    activity(f.token, id, status().isOk());
    jdbc.update("UPDATE card SET deleted_at = now() WHERE id = ?", id);

    activity(f.token, id, status().isNotFound());
    comments(f.token, id, status().isNotFound());
  }

  // --- Hilfen -----------------------------------------------------------------------------

  private record Fixture(long boardId, Cookie owner, String token) {}

  private Fixture fixture(String prefix, String displayName) throws Exception {
    Cookie owner = session(prefix + "-owner@example.com", displayName, PlatformRole.USER);
    Cookie admin = session(prefix + "-admin@example.com", "Admin", PlatformRole.ADMIN);
    long projectId = createProject(admin, "P-" + prefix, prefix + "-owner@example.com");
    long boardId = createBoard(owner, projectId, "B");
    String token =
        token(
            owner,
            "{\"name\":\"%s\",\"projectId\":%d,\"boardId\":%d}"
                .formatted(TOKEN_NAME, projectId, boardId));
    return new Fixture(boardId, owner, token);
  }

  private JsonNode activity(String token, long id, ResultMatcher expected) throws Exception {
    return read(
        mvc.perform(get("/api/kanban/items/" + id + "/activity").header(TOKEN_HEADER, token))
            .andExpect(expected)
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode comments(String token, long id, ResultMatcher expected) throws Exception {
    return read(
        mvc.perform(get("/api/kanban/items/" + id + "/comments").header(TOKEN_HEADER, token))
            .andExpect(expected)
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode byNumber(String token, int number) throws Exception {
    return read(
        mvc.perform(get("/api/kanban/items/by-number/" + number).header(TOKEN_HEADER, token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode read(String body) throws Exception {
    return json.readTree(body);
  }

  private long ingest(String token, String title) throws Exception {
    return read(mvc.perform(
                post("/api/kanban/items")
                    .header(TOKEN_HEADER, token)
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("title", title))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .get("id")
        .asLong();
  }

  private void tokenMove(String token, long id, String column) throws Exception {
    mvc.perform(
            put("/api/kanban/items/" + id + "/move")
                .header(TOKEN_HEADER, token)
                .header("X-Agent-Model", AGENT)
                .contentType("application/json")
                .content("{\"column\":\"%s\",\"position\":0}".formatted(column)))
        .andExpect(status().isOk());
  }

  private void sessionMove(Cookie session, long id, long columnId) throws Exception {
    mvc.perform(
            post("/api/cards/" + id + "/move")
                .cookie(session)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"position\":0}".formatted(columnId)))
        .andExpect(status().is2xxSuccessful());
  }

  private void tokenComment(String token, long id, String text) throws Exception {
    mvc.perform(
            post("/api/kanban/items/" + id + "/comments")
                .header(TOKEN_HEADER, token)
                .header("X-Agent-Model", AGENT)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("body", text))))
        .andExpect(status().is2xxSuccessful());
  }

  private void sessionComment(Cookie session, long id, String text) throws Exception {
    mvc.perform(
            post("/api/cards/" + id + "/comments")
                .cookie(session)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("body", text))))
        .andExpect(status().is2xxSuccessful());
  }

  private void archive(Cookie session, long id) throws Exception {
    mvc.perform(post("/api/cards/" + id + "/archive").cookie(session)).andExpect(status().isOk());
  }

  private int numberOf(long cardId) {
    Integer n = jdbc.queryForObject("SELECT number FROM card WHERE id = ?", Integer.class, cardId);
    if (n == null) {
      throw new IllegalStateException("Karte ohne Nummer: " + cardId);
    }
    return n;
  }

  private long columnIdByName(Cookie session, long boardId, String name) throws Exception {
    String body =
        mvc.perform(get("/api/boards/" + boardId).cookie(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return read(body)
        .get("columns")
        .valueStream()
        .filter(c -> name.equals(c.get("name").asText()))
        .findFirst()
        .orElseThrow()
        .get("id")
        .asLong();
  }

  private String token(Cookie session, String request) throws Exception {
    return read(mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content(request))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .get("plaintext")
        .asText();
  }

  private long createProject(Cookie admin, String name, String ownerEmail) throws Exception {
    return read(mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .get("id")
        .asLong();
  }

  private long createBoard(Cookie session, long projectId, String name) throws Exception {
    return read(mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString())
        .get("id")
        .asLong();
  }

  private Cookie session(String email, String displayName, PlatformRole role) {
    users.save(anmeldung.konto(email, role).withDisplayName(displayName));
    return anmeldung.anmelden(email, role);
  }
}
