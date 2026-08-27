package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * End-to-End des Schreibpfads für die Herkunft über die normale Karten-API (Issue #607).
 *
 * <p>Der Ingest konnte {@code derivedFrom} schon seit Issue #604; hier kommt der zweite Eingang
 * dazu — Anlegen über {@code POST /api/boards/{boardId}/cards} und Ändern über den eigenen schmalen
 * Endpunkt {@code PATCH /api/cards/{cardId}/derived-from}.
 *
 * <p>Der wichtigste Test dieser Klasse ist {@link
 * #patchOhneHerkunftsfeld_laesstDieHerkunftStehen()}: Er sichert die Entscheidung ab, das Feld
 * <strong>nicht</strong> in {@code UpdateCardRequest} aufzunehmen. {@code CardService.update} ist
 * ein Voll-Update, und ein fehlendes JSON-Feld ist in einem Jackson-Record nicht von {@code null}
 * zu unterscheiden — jeder bestehende Client hätte die Herkunft bei jedem Karten-Edit gelöscht.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardDerivedFromWriteIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void anlegenMitHerkunft_liefertDieNummerDesVorfahren() throws Exception {
    Fixture f = fixture("df-create");

    int vorfahrNummer = createCard(f.session, f.boardId, f.columnId, "Vorfahr", null);
    long kindId = createCardId(f.session, f.boardId, f.columnId, "Kind", vorfahrNummer);

    mvc.perform(get("/api/cards/" + kindId).cookie(f.session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.derivedFrom").value(vorfahrNummer));
  }

  @Test
  void aendernSetztUndLoeschtDieHerkunft() throws Exception {
    Fixture f = fixture("df-patch");

    int vorfahrNummer = createCard(f.session, f.boardId, f.columnId, "Vorfahr", null);
    long kindId = createCardId(f.session, f.boardId, f.columnId, "Kind", null);

    patchDerivedFrom(f.session, kindId, String.valueOf(vorfahrNummer)).andExpect(status().isOk());
    mvc.perform(get("/api/cards/" + kindId).cookie(f.session))
        .andExpect(jsonPath("$.derivedFrom").value(vorfahrNummer));

    patchDerivedFrom(f.session, kindId, "null").andExpect(status().isOk());
    mvc.perform(get("/api/cards/" + kindId).cookie(f.session))
        .andExpect(jsonPath("$.derivedFrom").doesNotExist());
  }

  /**
   * Rückwärtskompatibilität: Der bestehende Voll-Update-Pfad kennt die Herkunft nicht und darf sie
   * deshalb auch nicht anfassen. Ohne diesen Test wäre eine Implementierung "fertig", die
   * Bestandsdaten bei jedem Karten-Edit vernichtet.
   */
  @Test
  void patchOhneHerkunftsfeld_laesstDieHerkunftStehen() throws Exception {
    Fixture f = fixture("df-fullupdate");

    int vorfahrNummer = createCard(f.session, f.boardId, f.columnId, "Vorfahr", null);
    long kindId = createCardId(f.session, f.boardId, f.columnId, "Kind", vorfahrNummer);

    mvc.perform(
            patch("/api/cards/" + kindId)
                .cookie(f.session)
                .contentType("application/json")
                .content("{\"title\":\"Neuer Titel\"}"))
        .andExpect(status().isOk());

    mvc.perform(get("/api/cards/" + kindId).cookie(f.session))
        .andExpect(jsonPath("$.title").value("Neuer Titel"))
        .andExpect(jsonPath("$.derivedFrom").value(vorfahrNummer));
  }

  @Test
  void unbekannteNummer_wirdAufBeidenWegenAbgelehnt() throws Exception {
    Fixture f = fixture("df-unknown");

    mvc.perform(
            post("/api/boards/" + f.boardId + "/cards")
                .cookie(f.session)
                .contentType("application/json")
                .content(
                    "{\"columnId\":%d,\"title\":\"Kind\",\"derivedFrom\":99999}"
                        .formatted(f.columnId)))
        .andExpect(status().isBadRequest());

    long kindId = createCardId(f.session, f.boardId, f.columnId, "Kind2", null);
    patchDerivedFrom(f.session, kindId, "99999").andExpect(status().isBadRequest());
  }

  /**
   * Beleg für das mitgegebene {@code selfCardId}: Ohne es kennt {@code DerivedFrom.resolve} die
   * eigene Karte nicht und liesse den Selbstbezug durch.
   */
  @Test
  void selbstbezug_wirdAbgelehnt() throws Exception {
    Fixture f = fixture("df-self");

    JsonNode karte = createCardNode(f.session, f.boardId, f.columnId, "Selbst", null);
    long id = karte.get("id").asLong();
    int nummer = karte.get("number").asInt();

    patchDerivedFrom(f.session, id, String.valueOf(nummer)).andExpect(status().isBadRequest());
  }

  @Test
  void zyklus_wirdAbgelehnt() throws Exception {
    Fixture f = fixture("df-cycle");

    JsonNode a = createCardNode(f.session, f.boardId, f.columnId, "A", null);
    JsonNode b = createCardNode(f.session, f.boardId, f.columnId, "B", a.get("number").asInt());

    patchDerivedFrom(f.session, a.get("id").asLong(), String.valueOf(b.get("number").asInt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void vorhabenMitHerkunft_wirdAbgelehnt() throws Exception {
    Fixture f = fixture("df-epic");

    int vorfahrNummer = createCard(f.session, f.boardId, f.columnId, "Vorfahr", null);

    mvc.perform(
            post("/api/boards/" + f.boardId + "/cards")
                .cookie(f.session)
                .contentType("application/json")
                .content(
                    "{\"title\":\"Vorhaben\",\"type\":\"EPIC\",\"derivedFrom\":%d}"
                        .formatted(vorfahrNummer)))
        .andExpect(status().isBadRequest());
  }

  /**
   * Die Ablehnung muss dem Feld zuordenbar sein: Derselbe Status 400 mit fast wortgleichem Text
   * entsteht im selben Aufruf auch durch Abhängigkeits-Fehler.
   */
  @Test
  void ablehnung_traegtFieldErrorsFuerDerivedFrom() throws Exception {
    Fixture f = fixture("df-fielderrors");

    long kindId = createCardId(f.session, f.boardId, f.columnId, "Kind", null);

    patchDerivedFrom(f.session, kindId, "99999")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.derivedFrom").exists());
  }

  // --- Helfer ---------------------------------------------------------------

  private record Fixture(Cookie session, long boardId, long columnId) {}

  private Fixture fixture(String prefix) throws Exception {
    String email = prefix + "-owner@example.com";
    Cookie session = loginAs(email);
    long projectId = createProject(email, "P-" + prefix);
    JsonNode board = createBoard(session, projectId);
    return new Fixture(
        session, board.get("id").asLong(), board.get("columns").get(0).get("id").asLong());
  }

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
    String email = "df-write-admin@example.com";
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

  private JsonNode createBoard(Cookie session, long projectId) throws Exception {
    String body =
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"B\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private JsonNode createCardNode(
      Cookie session, long boardId, long columnId, String title, Integer derivedFrom)
      throws Exception {
    String herkunft = derivedFrom == null ? "" : ",\"derivedFrom\":" + derivedFrom;
    String body =
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"columnId\":%d,\"title\":\"%s\"%s}"
                            .formatted(columnId, title, herkunft)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private int createCard(
      Cookie session, long boardId, long columnId, String title, Integer derivedFrom)
      throws Exception {
    return createCardNode(session, boardId, columnId, title, derivedFrom).get("number").asInt();
  }

  private long createCardId(
      Cookie session, long boardId, long columnId, String title, Integer derivedFrom)
      throws Exception {
    return createCardNode(session, boardId, columnId, title, derivedFrom).get("id").asLong();
  }

  private org.springframework.test.web.servlet.ResultActions patchDerivedFrom(
      Cookie session, long cardId, String wert) throws Exception {
    return mvc.perform(
        patch("/api/cards/" + cardId + "/derived-from")
            .cookie(session)
            .contentType("application/json")
            .content("{\"derivedFrom\":%s}".formatted(wert)));
  }
}
