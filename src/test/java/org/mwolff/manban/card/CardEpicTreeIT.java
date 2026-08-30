package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
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
 * End-to-End des Herkunftsbaums <b>eines Vorhabens</b> (Issue #643): {@code GET
 * /api/boards/{boardId}/epics/{epicId}/tree}.
 *
 * <p>Eigene Klasse und nicht ein weiterer Fall in {@link CardDerivationTreeIT}: Das ist ein anderer
 * Endpunkt mit anderer Bezugsmenge, und jene Klasse steht bereits an der PMD-Methodengrenze.
 *
 * <p>Die Ableitungen selbst — Präorder, Tiefe, Extern-Kennzeichnung, {@code blocked} — sind in
 * {@code CardServiceTest} unit-getestet, weil PIT nur Unit-Tests misst. Hier geht es um das
 * Zusammenspiel an echten Daten und um die Rechteprüfung.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardEpicTreeIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  @Test
  void dreistufigeKette_kommtAlsBaumDesVorhabens() throws Exception {
    Fixture f = fixture("et-kette");
    long epic = id(vorhaben(f, "Vorhaben"));
    JsonNode anforderung = karte(f, "Anforderung", null);
    ordneZu(f, id(anforderung), epic);
    JsonNode plan = karte(f, "Plan", number(anforderung));
    karte(f, "Arbeitspaket", number(plan));

    JsonNode baum = baum(f, epic);

    assertThat(nummern(baum)).containsExactly(number(anforderung), number(plan), number(plan) + 1);
    assertThat(tiefen(baum)).containsExactly(0, 1, 2);
  }

  /** Ein Vorhaben ohne jede Zuordnung ist kein Fehler — es ist leer. */
  @Test
  void vorhabenOhneZuordnung_liefertLeereListe() throws Exception {
    Fixture f = fixture("et-leer");
    long epic = id(vorhaben(f, "Leer"));
    karte(f, "Unbeteiligt", null);

    assertThat(baum(f, epic)).isEmpty();
  }

  @Test
  void keinVorhabenDiesesBoards_liefert404() throws Exception {
    Fixture f = fixture("et-404");
    long gewoehnlich = id(karte(f, "Gewoehnlich", null));

    mvc.perform(get(pfad(f, gewoehnlich)).cookie(f.session)).andExpect(status().isNotFound());
    mvc.perform(get(pfad(f, 999_999L)).cookie(f.session)).andExpect(status().isNotFound());
  }

  /**
   * Leserecht wie bei den übrigen Board-Lesepfaden: Projekt-Mitgliedschaft. Ein Fremder darf den
   * Baum nicht sehen — und erfährt auch nicht, ob es ihn gibt.
   */
  @Test
  void nichtMitglied_bekommtDenBaumNicht() throws Exception {
    Fixture f = fixture("et-recht");
    long epic = id(vorhaben(f, "Vorhaben"));
    ordneZu(f, id(karte(f, "Anforderung", null)), epic);

    Cookie fremd = loginAs("et-fremd@example.com", PlatformRole.USER);

    mvc.perform(get(pfad(f, epic)).cookie(fremd)).andExpect(status().isNotFound());
    // Gegenprobe: Fuer das Mitglied liefert derselbe Aufruf den Baum.
    mvc.perform(get(pfad(f, epic)).cookie(f.session)).andExpect(status().isOk());
  }

  // --- Helfer ---------------------------------------------------------------

  private record Fixture(Cookie session, long boardId, long columnId) {}

  private String pfad(Fixture f, long epicId) {
    return "/api/boards/" + f.boardId + "/epics/" + epicId + "/tree";
  }

  private JsonNode baum(Fixture f, long epicId) throws Exception {
    String body =
        mvc.perform(get(pfad(f, epicId)).cookie(f.session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private static List<Integer> nummern(JsonNode baum) {
    List<Integer> werte = new ArrayList<>();
    baum.forEach(z -> werte.add(z.get("number").asInt()));
    return werte;
  }

  private static List<Integer> tiefen(JsonNode baum) {
    List<Integer> werte = new ArrayList<>();
    baum.forEach(z -> werte.add(z.get("depth").asInt()));
    return werte;
  }

  private static long id(JsonNode karte) {
    return karte.get("id").asLong();
  }

  private static int number(JsonNode karte) {
    return karte.get("number").asInt();
  }

  private void ordneZu(Fixture f, long cardId, long epicId) throws Exception {
    mvc.perform(
            patch("/api/cards/" + cardId + "/parent")
                .cookie(f.session)
                .contentType("application/json")
                .content("{\"parentId\":%d}".formatted(epicId)))
        .andExpect(status().isOk());
  }

  private JsonNode vorhaben(Fixture f, String titel) throws Exception {
    return legeAn(f, "{\"title\":\"%s\",\"type\":\"EPIC\"}".formatted(titel));
  }

  private JsonNode karte(Fixture f, String titel, Integer herkunft) throws Exception {
    String h = herkunft == null ? "" : ",\"derivedFrom\":" + herkunft;
    return legeAn(f, "{\"columnId\":%d,\"title\":\"%s\"%s}".formatted(f.columnId, titel, h));
  }

  /** Nicht `post`: Der Name verdeckte sonst den statischen Import von MockMvcRequestBuilders. */
  private JsonNode legeAn(Fixture f, String body) throws Exception {
    String antwort =
        mvc.perform(
                post("/api/boards/" + f.boardId + "/cards")
                    .cookie(f.session)
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(antwort);
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
    Cookie admin = loginAs("et-admin@example.com", PlatformRole.ADMIN);
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
}
