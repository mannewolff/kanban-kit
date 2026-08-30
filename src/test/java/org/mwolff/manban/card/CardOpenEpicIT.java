package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.mwolff.manban.card.application.CardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-End des Vorgangs-Eröffnens (Issue #640): {@code POST /api/cards/{cardId}/open-epic}.
 *
 * <p>Der wichtigste Test ist {@link #fehlerNachDemAnlegen_laesstKeinVorhabenZurueck()}: Er sichert
 * die Entscheidung ab, den ganzen Ablauf in <b>eine</b> Transaktion zu legen. Getrennte Aufrufe
 * hinterliessen bei einem Abbruch ein Vorhaben ohne Anforderung — also genau den Zustand, den die
 * Anforderung #636 abschaffen will. Ein Test, der nur den Fehlercode prüft, würde das nicht zeigen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
// Der @MockitoSpyBean macht diesen Kontext einzigartig: Spring kann ihn nicht mit den uebrigen
// IT-Kontexten teilen, und er haelt bis Suite-Ende einen eigenen Connection-Pool. Zusammen mit den
// anderen Sonder-Kontexten riss das Postgres-Verbindungslimit ("too many clients already") in
// spaeter laufenden ITs. Der Kontext wird deshalb nach dieser Klasse geschlossen.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CardOpenEpicIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @MockitoSpyBean private CardRepository cards;

  @Test
  void einAufruf_legtVorhabenAn_setztAnforderungUndZuordnung() throws Exception {
    Fixture f = fixture("oe-happy");
    JsonNode quelle = createCard(f, "Anforderung");

    String body =
        openEpic(
                f.session, quelle.get("id").asLong(), "{\"kuerzel\":\"EP-1\",\"name\":\"Vorgang\"}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode vorhaben = json.readTree(body);

    // 1. Das Vorhaben existiert und liegt auf dem Board der Quellkarte.
    assertThat(vorhaben.get("type").asText()).isEqualTo("EPIC");
    assertThat(vorhaben.get("boardId").asLong()).isEqualTo(f.boardId);

    // 2. Es trägt die Quellkarte als Anforderung — sichtbar in der Vorhaben-Liste.
    mvc.perform(get("/api/boards/" + f.boardId + "/epics").cookie(f.session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].requirementCardNumber").value(quelle.get("number").asInt()));

    // 3. Und die Quellkarte ist ihm zugeordnet.
    mvc.perform(get("/api/cards/" + quelle.get("id").asLong()).cookie(f.session))
        .andExpect(jsonPath("$.parentId").value(vorhaben.get("id").asLong()));
  }

  /**
   * Die Kette aus #632/#633 greift ohne weiteres Zutun: Was aus der Anforderungskarte entstanden
   * ist, zählt im Fortschritt des neuen Vorhabens mit — auch über mehrere Ebenen.
   */
  @Test
  void nachfahrenDerQuellkarte_zaehlenImFortschrittMit() throws Exception {
    Fixture f = fixture("oe-tree");
    JsonNode quelle = createCard(f, "Anforderung");
    JsonNode kind = createCardMitHerkunft(f, "Plan", quelle.get("number").asInt());
    createCardMitHerkunft(f, "Arbeitspaket", kind.get("number").asInt());

    openEpic(f.session, quelle.get("id").asLong(), "{\"name\":\"Vorgang\"}")
        .andExpect(status().isCreated());

    // Quellkarte + Kind + Enkel = 3 Mitglieder, ohne dass jemand sie einzeln zugeordnet hat.
    mvc.perform(get("/api/boards/" + f.boardId + "/epics").cookie(f.session))
        .andExpect(jsonPath("$[0].total").value(3))
        .andExpect(jsonPath("$[0].memberNumbers.length()").value(3));
  }

  @Test
  void ohneKuerzel_wirdAngelegt() throws Exception {
    Fixture f = fixture("oe-nokey");
    JsonNode quelle = createCard(f, "Anforderung");

    openEpic(f.session, quelle.get("id").asLong(), "{\"name\":\"Vorgang\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.shortcode").doesNotExist());
  }

  @Test
  void quelleIstVorhaben_wirdAbgelehnt() throws Exception {
    Fixture f = fixture("oe-epic");
    String body =
        mvc.perform(
                post("/api/boards/" + f.boardId + "/cards")
                    .cookie(f.session)
                    .contentType("application/json")
                    .content("{\"title\":\"Vorhaben\",\"type\":\"EPIC\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    openEpic(f.session, json.readTree(body).get("id").asLong(), "{\"name\":\"V\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void archivierteQuelle_wirdAbgelehnt() throws Exception {
    Fixture f = fixture("oe-archived");
    long id = createCard(f, "Anforderung").get("id").asLong();
    mvc.perform(post("/api/cards/" + id + "/archive").cookie(f.session)).andExpect(status().isOk());

    openEpic(f.session, id, "{\"name\":\"V\"}").andExpect(status().isBadRequest());
  }

  @Test
  void quelleImPapierkorb_wirdAbgelehnt() throws Exception {
    Fixture f = fixture("oe-trash");
    long id = createCard(f, "Anforderung").get("id").asLong();
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/cards/" + id)
                .cookie(f.session))
        .andExpect(status().isNoContent());

    openEpic(f.session, id, "{\"name\":\"V\"}").andExpect(status().isBadRequest());
  }

  @Test
  void bereitsZugeordneteQuelle_wirdAbgelehnt() throws Exception {
    Fixture f = fixture("oe-parent");
    JsonNode quelle = createCard(f, "Anforderung");
    long quelleId = quelle.get("id").asLong();

    // Erster Vorgang geht durch und setzt parentId ...
    openEpic(f.session, quelleId, "{\"name\":\"Erster\"}").andExpect(status().isCreated());

    // ... ein zweiter wuerde die Karte still umhaengen und wird deshalb abgelehnt.
    openEpic(f.session, quelleId, "{\"name\":\"Zweiter\"}").andExpect(status().isBadRequest());
  }

  /**
   * Rollback: Der Fehler wird gezielt zwischen dem Anlegen des Vorhabens und dem Setzen der
   * Anforderung injiziert. Danach darf <b>kein</b> Vorhaben in der Datenbank stehen — geprüft wird
   * der Bestand, nicht der Statuscode.
   */
  @Test
  void fehlerNachDemAnlegen_laesstKeinVorhabenZurueck() throws Exception {
    Fixture f = fixture("oe-rollback");
    JsonNode quelle = createCard(f, "Anforderung");

    // Der zweite Speicherzugriff ist der, der die Anforderung setzt — er scheitert.
    doThrow(new IllegalStateException("Speichern gescheitert"))
        .when(cards)
        .save(argThat(c -> c != null && c.requirementCardId() != null));

    openEpic(f.session, quelle.get("id").asLong(), "{\"name\":\"Vorgang\"}")
        .andExpect(status().is5xxServerError());

    // Der Bestand ist unveraendert: kein Vorhaben, und die Quellkarte ist niemandem zugeordnet.
    org.mockito.Mockito.reset(cards);
    mvc.perform(get("/api/boards/" + f.boardId + "/epics").cookie(f.session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get("/api/cards/" + quelle.get("id").asLong()).cookie(f.session))
        .andExpect(jsonPath("$.parentId").doesNotExist());
  }

  // --- Helfer ---------------------------------------------------------------

  private record Fixture(Cookie session, long boardId, long columnId) {}

  private ResultActions openEpic(Cookie session, long cardId, String body) throws Exception {
    return mvc.perform(
        post("/api/cards/" + cardId + "/open-epic")
            .cookie(session)
            .contentType("application/json")
            .content(body));
  }

  private Fixture fixture(String prefix) throws Exception {
    String email = prefix + "-owner@example.com";
    Cookie session = loginAs(email, PlatformRole.USER);
    long projectId = createProject(email, "P-" + prefix);
    JsonNode board = createBoard(session, projectId);
    return new Fixture(
        session, board.get("id").asLong(), board.get("columns").get(0).get("id").asLong());
  }

  private Cookie loginAs(String email, PlatformRole rolle) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "Person", true, rolle));
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
    Cookie admin = loginAs("oe-admin@example.com", PlatformRole.ADMIN);
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

  private JsonNode createCard(Fixture f, String titel) throws Exception {
    return createCardMitHerkunft(f, titel, null);
  }

  private JsonNode createCardMitHerkunft(Fixture f, String titel, Integer herkunft)
      throws Exception {
    String h = herkunft == null ? "" : ",\"derivedFrom\":" + herkunft;
    String body =
        mvc.perform(
                post("/api/boards/" + f.boardId + "/cards")
                    .cookie(f.session)
                    .contentType("application/json")
                    .content(
                        "{\"columnId\":%d,\"title\":\"%s\"%s}".formatted(f.columnId, titel, h)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }
}
