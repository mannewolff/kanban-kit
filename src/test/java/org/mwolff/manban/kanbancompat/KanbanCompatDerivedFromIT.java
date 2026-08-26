package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End der Herkunft am Ingest: beide Anlegewege tragen sie, eine unbekannte Nummer wird auf
 * beiden abgelehnt, und der Idempotenz-Treffer lässt sie unverändert.
 *
 * <p>Der Assert läuft über die Datenbank, nicht über die kanbancompat-Antwort: {@code Created}
 * trägt nur {@code id}, {@code number} und {@code created}, und {@code Item} bekommt die Herkunft
 * erst in Issue #605. Board-lose Pool-Ideen sind über {@code /api/kanban/items} ohnehin nicht
 * lesbar (#434).
 *
 * <p>Beide Wege werden einzeln geprüft, auch bei Ablehnung und Idempotenz: Der Duplikat-Check ist
 * in {@code doCreateProjectIdea} und {@code createDirect} getrennt implementiert — ein Test auf
 * einem Weg belegt den anderen nicht.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatDerivedFromIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void poolWeg_traegtDieHerkunft() throws Exception {
    Fixture f = fixture("df-pool");

    int vorfahrNummer = numberOf(ingest(f.token, "Vorfahr", null, null, false));
    long kindId = ingest(f.token, "Kind", vorfahrNummer, null, false);

    assertThat(derivedFromNumber(kindId)).isEqualTo(vorfahrNummer);
  }

  @Test
  void directWeg_traegtDieHerkunft() throws Exception {
    Fixture f = fixture("df-direct");

    int vorfahrNummer = numberOf(ingest(f.token, "Vorfahr", null, null, true));
    long kindId = ingest(f.token, "Kind", vorfahrNummer, null, true);

    assertThat(derivedFromNumber(kindId)).isEqualTo(vorfahrNummer);
  }

  @Test
  void unbekannteNummer_wirdAbgelehnt_aufBeidenWegen() throws Exception {
    Fixture f = fixture("df-unknown");

    ingestExpecting(f.token, "Pool", 999_999, null, false, status().isBadRequest());
    ingestExpecting(f.token, "Direct", 999_999, null, true, status().isBadRequest());

    assertThat(countCards(f.projectId)).isZero();
  }

  @Test
  void idempotenzTreffer_laesstDieHerkunftUnveraendert_aufBeidenWegen() throws Exception {
    Fixture f = fixture("df-idem");
    int vorfahrNummer = numberOf(ingest(f.token, "Vorfahr", null, null, false));
    int andererNummer = numberOf(ingest(f.token, "Anderer", null, null, false));

    long poolId = ingest(f.token, "Pool", vorfahrNummer, "k:pool", false);
    long poolWieder = ingestExisting(f.token, "Pool erneut", andererNummer, "k:pool", false);
    assertThat(poolWieder).isEqualTo(poolId);
    assertThat(derivedFromNumber(poolId)).isEqualTo(vorfahrNummer);

    long directId = ingest(f.token, "Direct", vorfahrNummer, "k:direct", true);
    long directWieder = ingestExisting(f.token, "Direct erneut", andererNummer, "k:direct", true);
    assertThat(directWieder).isEqualTo(directId);
    assertThat(derivedFromNumber(directId)).isEqualTo(vorfahrNummer);
  }

  @Test
  void nummerUnterEinsOderUeberDerObergrenze_wirdAbgelehnt() throws Exception {
    Fixture f = fixture("df-bounds");

    ingestExpecting(f.token, "Zu klein", 0, null, false, status().isBadRequest());
    ingestExpecting(f.token, "Zu gross", 1_000_001, null, false, status().isBadRequest());
  }

  // --- Hilfen ---------------------------------------------------------------

  private record Fixture(long projectId, String token) {}

  private Fixture fixture(String prefix) throws Exception {
    Cookie owner = session(prefix + "-owner@example.com", PlatformRole.USER);
    Cookie admin = session(prefix + "-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "P-" + prefix, prefix + "-owner@example.com");
    JsonNode board = createBoard(owner, projectId, "B");
    return new Fixture(projectId, boundToken(owner, projectId, board.get("id").asLong()));
  }

  private long ingest(String token, String title, Integer derivedFrom, String key, boolean direct)
      throws Exception {
    return json.readTree(
            ingestExpecting(token, title, derivedFrom, key, direct, status().isCreated()))
        .get("id")
        .asLong();
  }

  private long ingestExisting(
      String token, String title, Integer derivedFrom, String key, boolean direct)
      throws Exception {
    return json.readTree(
            ingestExpecting(token, title, derivedFrom, key, direct, status().isCreated()))
        .get("id")
        .asLong();
  }

  private String ingestExpecting(
      String token,
      String title,
      Integer derivedFrom,
      String key,
      boolean direct,
      org.springframework.test.web.servlet.ResultMatcher expected)
      throws Exception {
    StringBuilder body = new StringBuilder("{\"title\":\"").append(title).append('"');
    if (derivedFrom != null) {
      body.append(",\"derivedFrom\":").append(derivedFrom);
    }
    if (key != null) {
      body.append(",\"externalKey\":\"").append(key).append('"');
    }
    if (direct) {
      body.append(",\"direct\":true");
    }
    body.append('}');
    return mvc.perform(
            post("/api/kanban/items")
                .header("X-Kanban-Token", token)
                .contentType("application/json")
                .content(body.toString()))
        .andExpect(expected)
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private int numberOf(long cardId) {
    Integer n = jdbc.queryForObject("SELECT number FROM card WHERE id = ?", Integer.class, cardId);
    if (n == null) {
      throw new IllegalStateException("Karte ohne Nummer: " + cardId);
    }
    return n;
  }

  private int derivedFromNumber(long cardId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT v.number FROM card k JOIN card v ON v.id = k.derived_from_card_id"
                + " WHERE k.id = ?",
            Integer.class,
            cardId);
    if (n == null) {
      throw new IllegalStateException("Karte ohne Herkunft: " + cardId);
    }
    return n;
  }

  private int countCards(long projectId) {
    Integer c =
        jdbc.queryForObject(
            "SELECT count(*) FROM card WHERE project_id = ?", Integer.class, projectId);
    return c == null ? 0 : c;
  }

  private String boundToken(Cookie session, long projectId, long boardId) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(session)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"df-token\",\"projectId\":%d,\"boardId\":%d}"
                                .formatted(projectId, boardId)))
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
