package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Lesender Archivzugang der Kanban-Strecke über ein board-gebundenes Token (Plan #1574). Dieses
 * Paket deckt den Einzelabruf nach Nummer ab; Suche und Herkunftskette ergänzen ihre Fälle hier.
 *
 * <p>Archiv und Papierkorb werden per SQL gesetzt ({@code archived}, {@code deleted_at}): Der Test
 * prüft die Lesesicht, nicht die Wege dorthin.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatArchivIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String TOKEN_HEADER = "X-Kanban-Token";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  // --- Einzelabruf nach Nummer (Kriterium 1) ---------------------------------

  @Test
  void archivierteKarte_istNachNummerAbrufbar_mitAllenKommentaren() throws Exception {
    Fixture f = fixture("ar-einzel");
    long vorfahr = ingest(f.token, "[Fachlich] Herkunft", null);
    long id = ingest(f.token, "[Plan] Archivierter Plan", numberOf(vorfahr));
    comment(f.token, id, "## Review\nerster");
    comment(f.token, id, "## Abschluss\nzweiter");
    jdbc.update("UPDATE card SET archived = true, moved_to_done_at = now() WHERE id = ?", id);

    JsonNode detail = byNumber(f.token, numberOf(id), status().isOk());

    assertThat(detail.get("archived").asBoolean()).isTrue();
    assertThat(detail.get("createdAt").isNull()).isFalse();
    assertThat(detail.get("movedToDoneAt").isNull()).isFalse();
    JsonNode item = detail.get("item");
    assertThat(item.get("id").asLong()).isEqualTo(id);
    assertThat(item.get("number").asInt()).isEqualTo(numberOf(id));
    assertThat(item.get("title").asText()).isEqualTo("[Plan] Archivierter Plan");
    assertThat(item.get("type").asText()).isEqualTo("card");
    assertThat(item.get("derivedFrom").asInt()).isEqualTo(numberOf(vorfahr));
    List<String> bodies = new ArrayList<>();
    detail.get("comments").forEach(c -> bodies.add(c.get("body").asText()));
    assertThat(bodies).containsExactly("## Review\nerster", "## Abschluss\nzweiter");
  }

  @Test
  void uebersicht_zeigtDieArchivierteKarteWeiterhinNicht() throws Exception {
    Fixture f = fixture("ar-uebersicht");
    long aktiv = ingest(f.token, "Aktiv", null);
    long archiviert = ingest(f.token, "Archiviert", null);
    jdbc.update("UPDATE card SET archived = true WHERE id = ?", archiviert);

    List<Long> ids = new ArrayList<>();
    json.readTree(
            mvc.perform(get("/api/kanban/items").header(TOKEN_HEADER, f.token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .forEach(spalte -> spalte.forEach(item -> ids.add(item.get("id").asLong())));

    assertThat(ids).contains(aktiv).doesNotContain(archiviert);
  }

  @Test
  void aktiveKarte_istEbenfallsNachNummerAbrufbar() throws Exception {
    Fixture f = fixture("ar-aktiv");
    long id = ingest(f.token, "Aktiv", null);

    JsonNode detail = byNumber(f.token, numberOf(id), status().isOk());

    assertThat(detail.get("archived").asBoolean()).isFalse();
    assertThat(detail.get("item").get("column").asText()).isEqualTo("BACKLOG");
    assertThat(detail.get("comments")).isEmpty();
  }

  @Test
  void papierkorbNummer_istNichtGefunden() throws Exception {
    Fixture f = fixture("ar-papierkorb");
    long id = ingest(f.token, "Gelöscht", null);
    jdbc.update("UPDATE card SET deleted_at = now() WHERE id = ?", id);

    String papierkorb = byNumberRaw(f.token, numberOf(id), status().isNotFound());
    String unbekannt = byNumberRaw(f.token, 999_999, status().isNotFound());

    assertThat(json.readTree(papierkorb).get("detail"))
        .isEqualTo(json.readTree(unbekannt).get("detail"));
  }

  @Test
  void nummerEinesAnderenBoards_istNichtGefunden() throws Exception {
    Fixture f = fixture("ar-fremd");
    long anderesBoard = createBoard(f.owner, f.projectId, "Anderes Board");
    String andererToken = boundToken(f.owner, f.projectId, anderesBoard);
    long fremd = ingest(andererToken, "Auf dem anderen Board", null);
    jdbc.update("UPDATE card SET archived = true WHERE id = ?", fremd);

    String antwort = byNumberRaw(f.token, numberOf(fremd), status().isNotFound());
    String unbekannt = byNumberRaw(f.token, 999_999, status().isNotFound());

    assertThat(json.readTree(antwort).get("detail"))
        .isEqualTo(json.readTree(unbekannt).get("detail"));
  }

  @Test
  void ungebundenesToken_wirdMit409Abgewiesen() throws Exception {
    Fixture f = fixture("ar-ungebunden");
    long id = ingest(f.token, "Karte", null);

    byNumberRaw(unboundToken(f.owner), numberOf(id), status().isConflict());
  }

  // --- Hilfen ---------------------------------------------------------------

  private record Fixture(long projectId, Cookie owner, String token) {}

  private Fixture fixture(String prefix) throws Exception {
    Cookie owner = session(prefix + "-owner@example.com", PlatformRole.USER);
    Cookie admin = session(prefix + "-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "P-" + prefix, prefix + "-owner@example.com");
    long boardId = createBoard(owner, projectId, "B");
    return new Fixture(projectId, owner, boundToken(owner, projectId, boardId));
  }

  private JsonNode byNumber(String token, int number, ResultMatcher expected) throws Exception {
    return json.readTree(byNumberRaw(token, number, expected));
  }

  private String byNumberRaw(String token, int number, ResultMatcher expected) throws Exception {
    return mvc.perform(get("/api/kanban/items/by-number/" + number).header(TOKEN_HEADER, token))
        .andExpect(expected)
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private long ingest(String token, String title, Integer derivedFrom) throws Exception {
    String body =
        derivedFrom == null
            ? "{\"title\":\"%s\"}".formatted(title)
            : "{\"title\":\"%s\",\"derivedFrom\":%d}".formatted(title, derivedFrom);
    return json.readTree(
            mvc.perform(
                    post("/api/kanban/items")
                        .header(TOKEN_HEADER, token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private void comment(String token, long cardId, String text) throws Exception {
    mvc.perform(
            post("/api/kanban/items/" + cardId + "/comments")
                .header(TOKEN_HEADER, token)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("body", text))))
        .andExpect(status().is2xxSuccessful());
  }

  private int numberOf(long cardId) {
    Integer n = jdbc.queryForObject("SELECT number FROM card WHERE id = ?", Integer.class, cardId);
    if (n == null) {
      throw new IllegalStateException("Karte ohne Nummer: " + cardId);
    }
    return n;
  }

  private String boundToken(Cookie session, long projectId, long boardId) throws Exception {
    return token(
        session,
        "{\"name\":\"archiv-token\",\"projectId\":%d,\"boardId\":%d}"
            .formatted(projectId, boardId));
  }

  private String unboundToken(Cookie session) throws Exception {
    return token(session, "{\"name\":\"archiv-ungebunden\"}");
  }

  private String token(Cookie session, String request) throws Exception {
    return json.readTree(
            mvc.perform(
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

  private long createBoard(Cookie session, long projectId, String name) throws Exception {
    return json.readTree(
            mvc.perform(
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
