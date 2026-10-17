package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Anhänge einer Karte über das board-gebundene Projekt-Token lesen, Ende zu Ende gegen Postgres und
 * den echten Objektspeicher (Issue #1700, Plan #1694, fachliche Quelle #1665).
 *
 * <p>Anhänge legt der angemeldete Mensch über {@code POST /api/cards/{id}/attachments} an; gelesen
 * wird mit {@code X-Kanban-Token}. Jede unerreichbare Antwort vergleicht der Test in Status und
 * Rumpf mit der einer nie vergebenen Anhang-ID — das Token soll nicht erfahren, ob es den Anhang
 * gibt. Papierkorb und Archiv werden per SQL gesetzt: Der Test prüft die Lesesicht, nicht die Wege
 * dorthin.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatAttachmentIT extends AbstractIntegrationTest {

  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String ENTWURF = "entwurf.png";
  private static final byte[] PNG = {
    (byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 1, 2, 3, 4
  };
  private static final long NIE_VERGEBEN = 999_999_999L;

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  // --- Szenarien der fachlichen Quelle ---------------------------------------

  @Test
  @DisplayName("Szenario: Agent holt einen Entwurf von der Karte")
  void agentHoltEinenEntwurfVonDerKarte() throws Exception {
    Fixture f = fixture("an-entwurf");
    long cardId = card(f.owner, f.boardId, "Karte mit Entwurf");
    long anhang = upload(f.owner, cardId, ENTWURF, PNG);

    JsonNode liste = json.readTree(list(f.token, cardId, 200).getContentAsString());
    JsonNode sessionListe =
        json.readTree(
            mvc.perform(get("/api/cards/" + cardId + "/attachments").cookie(f.owner))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    assertThat(liste).hasSize(1);
    JsonNode eintrag = liste.get(0);
    assertThat(eintrag.get("id").asLong()).isEqualTo(anhang);
    assertThat(eintrag.get("filename").asText()).isEqualTo(ENTWURF);
    assertThat(eintrag.get("contentType").asText()).isEqualTo("image/png");
    assertThat(eintrag.get("size").asLong()).isEqualTo(PNG.length);
    assertThat(eintrag.get("createdAt").isNull()).isFalse();
    assertThat(liste).isEqualTo(sessionListe);

    MockHttpServletResponse download = download(f.token, cardId, anhang, 200);
    assertThat(download.getContentAsByteArray()).isEqualTo(PNG);
    assertThat(download.getContentType()).isEqualTo("image/png");
    assertThat(download.getHeader("Content-Disposition"))
        .startsWith("attachment")
        .contains(ENTWURF);
  }

  @Test
  @DisplayName("Szenario: Anhang von fremdem Board")
  void anhangVonFremdemBoard() throws Exception {
    Fixture f = fixture("an-fremd");
    long karteA = card(f.owner, f.boardId, "Karte A");
    long boardB = createBoard(f.owner, f.projectId, "Board B");
    long karteB = card(f.owner, boardB, "Karte B");
    long anhangB = upload(f.owner, karteB, ENTWURF, PNG);
    MockHttpServletResponse unbekannt = download(f.token, karteA, NIE_VERGEBEN, 404);

    assertGleicheAntwort(download(f.token, karteB, anhangB, 404), unbekannt);
    assertGleicheAntwort(download(f.token, karteA, anhangB, 404), unbekannt);
    assertGleicheAntwort(list(f.token, karteB, 404), unbekannt);
  }

  @Test
  @DisplayName("Szenario: Hochladen über das Token")
  void hochladenUeberDasToken() throws Exception {
    Fixture f = fixture("an-hochladen");
    long cardId = card(f.owner, f.boardId, "Karte");
    long anhang = upload(f.owner, cardId, ENTWURF, PNG);
    String vorher = list(f.token, cardId, 200).getContentAsString();
    MockMultipartFile datei = new MockMultipartFile("file", "neu.png", "image/png", PNG);

    mvc.perform(
            multipart("/api/cards/" + cardId + "/attachments")
                .file(datei)
                .header(TOKEN_HEADER, f.token))
        .andExpect(status().isForbidden());
    mvc.perform(
            multipart("/api/kanban/items/" + cardId + "/attachments")
                .file(datei)
                .header(TOKEN_HEADER, f.token))
        .andExpect(status().isMethodNotAllowed());
    mvc.perform(delete("/api/attachments/" + anhang).header(TOKEN_HEADER, f.token))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete("/api/kanban/items/" + cardId + "/attachments/" + anhang)
                .header(TOKEN_HEADER, f.token))
        .andExpect(status().isMethodNotAllowed());

    assertThat(json.readTree(list(f.token, cardId, 200).getContentAsString()))
        .isEqualTo(json.readTree(vorher));
    assertThat(download(f.token, cardId, anhang, 200).getContentAsByteArray()).isEqualTo(PNG);
  }

  @Test
  @DisplayName("Szenario: Widerrufenes Token")
  void widerrufenesToken() throws Exception {
    Fixture f = fixture("an-widerruf");
    long cardId = card(f.owner, f.boardId, "Karte");
    long anhang = upload(f.owner, cardId, ENTWURF, PNG);
    download(f.token, cardId, anhang, 200);

    mvc.perform(delete("/api/access-tokens/" + tokenId(f.owner)).cookie(f.owner))
        .andExpect(status().isNoContent());

    download(f.token, cardId, anhang, 401);
    list(f.token, cardId, 401);
  }

  @Test
  @DisplayName("Szenario: Gelöschter Anhang")
  void geloeschterAnhang() throws Exception {
    Fixture f = fixture("an-geloescht");
    long cardId = card(f.owner, f.boardId, "Karte");
    long anhang = upload(f.owner, cardId, ENTWURF, PNG);
    long bleibt = upload(f.owner, cardId, "notiz.png", PNG);

    mvc.perform(delete("/api/attachments/" + anhang).cookie(f.owner))
        .andExpect(status().isNoContent());

    assertGleicheAntwort(
        download(f.token, cardId, anhang, 404), download(f.token, cardId, NIE_VERGEBEN, 404));
    JsonNode liste = json.readTree(list(f.token, cardId, 200).getContentAsString());
    assertThat(filenames(liste)).containsExactly("notiz.png");
    assertThat(liste.get(0).get("id").asLong()).isEqualTo(bleibt);
  }

  // --- Weitere Fälle --------------------------------------------------------

  @Test
  void karteImPapierkorb_antwortetWieEinUnbekannterAnhang() throws Exception {
    Fixture f = fixture("an-papierkorb");
    long cardId = card(f.owner, f.boardId, "Karte");
    long anhang = upload(f.owner, cardId, ENTWURF, PNG);
    long andere = card(f.owner, f.boardId, "Andere");
    jdbc.update("UPDATE card SET deleted_at = now() WHERE id = ?", cardId);
    MockHttpServletResponse unbekannt = download(f.token, andere, NIE_VERGEBEN, 404);

    assertGleicheAntwort(download(f.token, cardId, anhang, 404), unbekannt);
    assertGleicheAntwort(list(f.token, cardId, 404), unbekannt);
  }

  @Test
  void archivierteKarte_bleibtLesbar() throws Exception {
    Fixture f = fixture("an-archiv");
    long cardId = card(f.owner, f.boardId, "Karte");
    long anhang = upload(f.owner, cardId, ENTWURF, PNG);
    jdbc.update("UPDATE card SET archived = true WHERE id = ?", cardId);

    assertThat(filenames(json.readTree(list(f.token, cardId, 200).getContentAsString())))
        .containsExactly(ENTWURF);
    assertThat(download(f.token, cardId, anhang, 200).getContentAsByteArray()).isEqualTo(PNG);
  }

  @Test
  void ungebundenesToken_wirdMit409Abgewiesen() throws Exception {
    Fixture f = fixture("an-ungebunden");
    long cardId = card(f.owner, f.boardId, "Karte");
    long anhang = upload(f.owner, cardId, ENTWURF, PNG);
    String ungebunden = token(f.owner, "{\"name\":\"anhang-ungebunden\"}");

    list(ungebunden, cardId, 409);
    download(ungebunden, cardId, anhang, 409);
  }

  @Test
  void tokenBesitzerOhneMitgliedschaft_antwortetWieEinUnbekannterAnhang() throws Exception {
    Fixture f = fixture("an-mitglied");
    long cardId = card(f.owner, f.boardId, "Karte");
    long anhang = upload(f.owner, cardId, ENTWURF, PNG);
    String email = "an-mitglied-member@example.com";
    Cookie member = session(email, PlatformRole.USER);
    long memberId = users.findByEmail(email).orElseThrow().id();
    memberships.save(
        new ProjectMembership(null, f.projectId, memberId, ProjectRole.MEMBER, Instant.now()));
    String memberToken = boundToken(member, f.projectId, f.boardId);
    MockHttpServletResponse unbekannt = download(memberToken, cardId, NIE_VERGEBEN, 404);
    download(memberToken, cardId, anhang, 200);

    jdbc.update(
        "DELETE FROM project_membership WHERE project_id = ? AND user_id = ?",
        f.projectId,
        memberId);

    assertGleicheAntwort(download(memberToken, cardId, anhang, 404), unbekannt);
    assertGleicheAntwort(list(memberToken, cardId, 404), unbekannt);
  }

  @Test
  void listeUndDownload_schreibenKeinenVerlaufseintrag() throws Exception {
    Fixture f = fixture("an-verlauf");
    long cardId = card(f.owner, f.boardId, "Karte");
    long anhang = upload(f.owner, cardId, ENTWURF, PNG);
    long vorher = verlaufseintraege(cardId);

    list(f.token, cardId, 200);
    download(f.token, cardId, anhang, 200);

    assertThat(verlaufseintraege(cardId)).isEqualTo(vorher);
  }

  // --- Hilfen ---------------------------------------------------------------

  private record Fixture(long projectId, long boardId, Cookie owner, String token) {}

  private Fixture fixture(String prefix) throws Exception {
    Cookie owner = session(prefix + "-owner@example.com", PlatformRole.USER);
    Cookie admin = session(prefix + "-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "P-" + prefix, prefix + "-owner@example.com");
    long boardId = createBoard(owner, projectId, "A");
    return new Fixture(projectId, boardId, owner, boundToken(owner, projectId, boardId));
  }

  private void assertGleicheAntwort(
      MockHttpServletResponse antwort, MockHttpServletResponse unbekannt) throws Exception {
    assertThat(antwort.getStatus()).isEqualTo(unbekannt.getStatus());
    assertThat(ohneInstance(antwort)).isEqualTo(ohneInstance(unbekannt));
  }

  /** Der Rumpf ohne {@code instance}: Der Pfad der Anfrage unterscheidet sich naturgemäß. */
  private JsonNode ohneInstance(MockHttpServletResponse response) throws Exception {
    JsonNode rumpf = json.readTree(response.getContentAsString());
    if (rumpf.isObject()) {
      ((ObjectNode) rumpf).remove("instance");
    }
    return rumpf;
  }

  private MockHttpServletResponse list(String token, long cardId, int expected) throws Exception {
    return perform(
        get("/api/kanban/items/" + cardId + "/attachments").header(TOKEN_HEADER, token), expected);
  }

  private MockHttpServletResponse download(
      String token, long cardId, long attachmentId, int expected) throws Exception {
    return perform(
        get("/api/kanban/items/" + cardId + "/attachments/" + attachmentId)
            .header(TOKEN_HEADER, token),
        expected);
  }

  private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, int expected)
      throws Exception {
    return mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse();
  }

  private static List<String> filenames(JsonNode liste) {
    List<String> namen = new ArrayList<>();
    liste.forEach(a -> namen.add(a.get("filename").asText()));
    return namen;
  }

  private long verlaufseintraege(long cardId) {
    Long anzahl =
        jdbc.queryForObject(
            "SELECT count(*) FROM card_activity WHERE card_id = ?", Long.class, cardId);
    return anzahl == null ? 0 : anzahl;
  }

  private long upload(Cookie session, long cardId, String filename, byte[] content)
      throws Exception {
    return json.readTree(
            mvc.perform(
                    multipart("/api/cards/" + cardId + "/attachments")
                        .file(new MockMultipartFile("file", filename, "image/png", content))
                        .cookie(session))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private long card(Cookie session, long boardId, String title) throws Exception {
    long columnId =
        json.readTree(
                mvc.perform(get("/api/boards/" + boardId).cookie(session))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("columns")
            .get(0)
            .get("id")
            .asLong();
    return json.readTree(
            mvc.perform(
                    post("/api/boards/" + boardId + "/cards")
                        .cookie(session)
                        .contentType("application/json")
                        .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private long tokenId(Cookie session) throws Exception {
    JsonNode tokens =
        json.readTree(
            mvc.perform(get("/api/access-tokens").cookie(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return tokens.get(0).get("id").asLong();
  }

  private String boundToken(Cookie session, long projectId, long boardId) throws Exception {
    return token(
        session,
        "{\"name\":\"anhang-token\",\"projectId\":%d,\"boardId\":%d}"
            .formatted(projectId, boardId));
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

  private Cookie session(String email, PlatformRole role) {
    return anmeldung.anmelden(email, role);
  }
}
