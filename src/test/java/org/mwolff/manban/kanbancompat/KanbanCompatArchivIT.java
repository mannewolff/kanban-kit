package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Lesender Archivzugang der Kanban-Strecke über ein board-gebundenes Token (Plan #1574):
 * Einzelabruf nach Nummer und Suche nach Art, Vorhaben und Zeitraum; Textsuche und Herkunftskette
 * ergänzen ihre Fälle hier.
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

  // --- Suche nach Art, Vorhaben und Zeitraum (Kriterien 2 und 4) ----------------

  @Test
  void suche_nachArt_liefertArchivierteTreffer_mitAngaben() throws Exception {
    Fixture f = fixture("su-art");
    long fachlich = ingest(f.token, "[Fachlich] Alter Fachplan", null);
    long plan = ingest(f.token, "[Plan] Plan", null);
    ingest(f.token, "Arbeitspaket", null);
    jdbc.update(
        "UPDATE card SET archived = true, moved_to_done_at = now(), description = ? WHERE id = ?",
        "## Ziel\nFrüher entschieden",
        fachlich);

    JsonNode page = search(f.token, "?kind=FACHLICH&kind=PLAN", status().isOk());

    List<Long> ids = new ArrayList<>();
    page.get("hits").forEach(h -> ids.add(h.get("id").asLong()));
    assertThat(ids).containsExactly(plan, fachlich);
    assertThat(page.get("nextCursor").isNull()).isTrue();
    JsonNode hit = page.get("hits").get(1);
    assertThat(hit.get("number").asInt()).isEqualTo(numberOf(fachlich));
    assertThat(hit.get("title").asText()).isEqualTo("[Fachlich] Alter Fachplan");
    assertThat(hit.get("kind").asText()).isEqualTo("FACHLICH");
    assertThat(hit.get("column").asText()).isEqualTo("BACKLOG");
    assertThat(hit.get("archived").asBoolean()).isTrue();
    assertThat(hit.get("createdAt").isNull()).isFalse();
    assertThat(hit.get("movedToDoneAt").isNull()).isFalse();
    assertThat(hit.get("excerpt").get("text").asText()).isEqualTo("## Ziel Früher entschieden");
    assertThat(hit.get("excerpt").get("source").asText()).isEqualTo("BODY");
    assertThat(hit.get("excerpt").get("commentId").isNull()).isTrue();
  }

  @Test
  void suche_unbekannteArt_ist400() throws Exception {
    Fixture f = fixture("su-unbekannt");

    search(f.token, "?kind=FACHPLAN", status().isBadRequest());
  }

  @Test
  void suche_ungueltigeGrenzen_sind400() throws Exception {
    Fixture f = fixture("su-grenzen");

    search(f.token, "?limit=0", status().isBadRequest());
    search(f.token, "?limit=201", status().isBadRequest());
    search(f.token, "?cursor=0", status().isBadRequest());
    search(
        f.token,
        "?createdFrom=2026-02-01T00:00:00Z&createdTo=2026-01-01T00:00:00Z",
        status().isBadRequest());
  }

  @Test
  void suche_ungebundenesToken_wirdMit409Abgewiesen() throws Exception {
    Fixture f = fixture("su-ungebunden");

    search(unboundToken(f.owner), "", status().isConflict());
  }

  @Test
  void suche_nachVorhaben_schliesstArchiviertesMitgliedEin() throws Exception {
    Fixture f = fixture("su-vorhaben");
    long vorhaben = ingest(f.token, "Vorhaben", null);
    jdbc.update("UPDATE card SET type = 'EPIC', status = NULL WHERE id = ?", vorhaben);
    long wurzel = ingest(f.token, "[Plan] Wurzel", null);
    long kind = ingest(f.token, "Paket", numberOf(wurzel));
    ingest(f.token, "Fremd", null);
    jdbc.update("UPDATE card SET parent_id = ? WHERE id = ?", vorhaben, wurzel);
    jdbc.update("UPDATE card SET archived = true WHERE id = ?", kind);

    JsonNode page = search(f.token, "?epic=" + numberOf(vorhaben), status().isOk());
    JsonNode keinVorhaben = search(f.token, "?epic=" + numberOf(wurzel), status().isOk());

    List<Long> ids = new ArrayList<>();
    page.get("hits").forEach(h -> ids.add(h.get("id").asLong()));
    assertThat(ids).containsExactly(kind, wurzel);
    assertThat(keinVorhaben.get("hits")).isEmpty();
  }

  @Test
  void suche_nachZeitraum_istHalboffen() throws Exception {
    Fixture f = fixture("su-zeitraum");
    long frueh = ingest(f.token, "Früh", null);
    long mitte = ingest(f.token, "Mitte", null);
    long spaet = ingest(f.token, "Spät", null);
    setCreated(frueh, "2026-01-01T00:00:00Z");
    setCreated(mitte, "2026-02-01T00:00:00Z");
    setCreated(spaet, "2026-03-01T00:00:00Z");

    JsonNode page =
        search(
            f.token,
            "?createdFrom=2026-02-01T00:00:00Z&createdTo=2026-03-01T00:00:00Z",
            status().isOk());

    List<Long> ids = new ArrayList<>();
    page.get("hits").forEach(h -> ids.add(h.get("id").asLong()));
    assertThat(ids).containsExactly(mitte);
  }

  @Test
  void suche_zweihundertfuenfzigKarten_inTeilen_vollstaendigUndOhneDoppel() throws Exception {
    Fixture f = fixture("su-teile");
    Set<Long> angelegt = new HashSet<>();
    for (int i = 0; i < 250; i++) {
      angelegt.add(ingest(f.token, "Karte " + i, null));
    }
    jdbc.update("UPDATE card SET archived = true WHERE id IN (?, ?)", min(angelegt), max(angelegt));

    List<Long> gesammelt = new ArrayList<>();
    String cursor = null;
    int teile = 0;
    do {
      JsonNode page =
          search(
              f.token, "?limit=50" + (cursor == null ? "" : "&cursor=" + cursor), status().isOk());
      assertThat(page.get("hits").size()).isLessThanOrEqualTo(50);
      page.get("hits").forEach(h -> gesammelt.add(h.get("id").asLong()));
      cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
      teile++;
    } while (cursor != null);

    assertThat(teile).isEqualTo(5);
    assertThat(gesammelt).hasSize(250).doesNotHaveDuplicates();
    assertThat(new HashSet<>(gesammelt)).isEqualTo(angelegt);
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

  private JsonNode search(String token, String query, ResultMatcher expected) throws Exception {
    return json.readTree(
        mvc.perform(get("/api/kanban/search" + query).header(TOKEN_HEADER, token))
            .andExpect(expected)
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void setCreated(long cardId, String instant) {
    jdbc.update(
        "UPDATE card SET created_at = CAST(? AS timestamptz) WHERE id = ?", instant, cardId);
  }

  private static long min(Set<Long> ids) {
    return ids.stream().mapToLong(Long::longValue).min().orElseThrow();
  }

  private static long max(Set<Long> ids) {
    return ids.stream().mapToLong(Long::longValue).max().orElseThrow();
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
