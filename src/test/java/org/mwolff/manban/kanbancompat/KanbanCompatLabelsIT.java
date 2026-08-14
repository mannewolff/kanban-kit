package org.mwolff.manban.kanbancompat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * End-to-End der atomaren Label-Operationen über kanbancompat (#574): {@code POST/DELETE
 * /api/kanban/items/{id}/labels} ergänzt beziehungsweise entfernt <em>genau ein</em> Label einer
 * Karte des gebundenen Boards und lässt alle übrigen unangetastet.
 *
 * <p>Der Weg geht bewusst über die echte PAT-Kette (Filter → Controller → Services), nicht über den
 * Service unter Umgehung der Sicherheitsfilter — die Rechte- und Bindungsprüfungen sind Teil des
 * geprüften Verhaltens.
 */
// PMD.TooManyMethods: methodenreiche IT-Suite — je Erfolgs- und Fehlerpfad ein eigener Test.
@SuppressWarnings("PMD.TooManyMethods")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class KanbanCompatLabelsIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String NIGHTRUN = "kit:nightrun";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void addAndRemoveTouchOnlyTheNamedLabel() throws Exception {
    Fixture f = fixture("labels-single");
    long bug = createLabel(f, "Bug");
    long ux = createLabel(f, "Ux");
    createLabel(f, NIGHTRUN);
    long cardId = directIngest(f.token, "Karte");
    setLabels(f.session, cardId, List.of(bug, ux));

    // Hinzufügen ergänzt genau eines und lässt die übrigen stehen.
    mvc.perform(addLabel(f.token, cardId, NIGHTRUN)).andExpect(status().isNoContent());
    assertThat(labelsOf(f.token, cardId)).containsExactlyInAnyOrder("Bug", "Ux", NIGHTRUN);

    // Entfernen nimmt genau eines und lässt die übrigen stehen.
    mvc.perform(removeLabel(f.token, cardId, "Ux")).andExpect(status().isNoContent());
    assertThat(labelsOf(f.token, cardId)).containsExactlyInAnyOrder("Bug", NIGHTRUN);
  }

  @Test
  void addAndRemoveAreIdempotentAndAlwaysAnswerNoContent() throws Exception {
    // Ein Nachtlauf muss nach einem Teilfehler wiederholbar sein: derselbe Aufruf zweimal ist
    // Erfolg, und der zweite verändert nichts.
    Fixture f = fixture("labels-idempotent");
    createLabel(f, NIGHTRUN);
    long cardId = directIngest(f.token, "Karte");

    mvc.perform(addLabel(f.token, cardId, NIGHTRUN)).andExpect(status().isNoContent());
    mvc.perform(addLabel(f.token, cardId, NIGHTRUN)).andExpect(status().isNoContent());
    assertThat(assignmentCount(cardId)).isEqualTo(1);

    mvc.perform(removeLabel(f.token, cardId, NIGHTRUN)).andExpect(status().isNoContent());
    mvc.perform(removeLabel(f.token, cardId, NIGHTRUN)).andExpect(status().isNoContent());
    assertThat(assignmentCount(cardId)).isZero();
  }

  @Test
  void resolvesTheNameOnTheCardsBoardOnly() throws Exception {
    // Labelnamen sind nur boardweit eindeutig. Beide Boards definieren "Bug"; zugeordnet werden
    // darf ausschließlich die Label-ID des Kartenboards.
    Fixture f = fixture("labels-board-scope");
    long ownBug = createLabel(f, "Bug");
    long otherBoard = createBoard(f.session, f.projectId, "Anderes Board");
    long foreignBug = createLabel(f.session, otherBoard, "Bug");
    long cardId = directIngest(f.token, "Karte");

    mvc.perform(addLabel(f.token, cardId, "Bug")).andExpect(status().isNoContent());
    assertThat(assignedLabelIds(cardId)).containsExactly(ownBug).doesNotContain(foreignBug);

    // Auch das Entfernen greift nur das Label des Kartenboards ab: die fremde Definition bleibt.
    mvc.perform(removeLabel(f.token, cardId, "Bug")).andExpect(status().isNoContent());
    assertThat(assignedLabelIds(cardId)).isEmpty();
    assertThat(labelExists(foreignBug)).isTrue();
  }

  @Test
  void rejectsUnknownLabelNameWithoutCreatingIt() throws Exception {
    // Ein Tippfehler im Nachtlauf darf kein Label anlegen — sonst entsteht unbemerkt Label-Müll.
    Fixture f = fixture("labels-unknown");
    long cardId = directIngest(f.token, "Karte");

    mvc.perform(addLabel(f.token, cardId, "gibt-es-nicht")).andExpect(status().isNotFound());
    mvc.perform(removeLabel(f.token, cardId, "gibt-es-nicht")).andExpect(status().isNotFound());

    assertThat(labelCountByName("gibt-es-nicht")).isZero();
    assertThat(assignmentCount(cardId)).isZero();
  }

  @Test
  void rejectsEpics() throws Exception {
    // GET /items liefert auch Epics; Labels gibt es dort nicht (wie im UI-Pfad).
    Fixture f = fixture("labels-epic");
    createLabel(f, NIGHTRUN);
    long epicId = createEpic(f, "Sammel-Epic", "SAM");

    mvc.perform(addLabel(f.token, epicId, NIGHTRUN)).andExpect(status().isBadRequest());
    mvc.perform(removeLabel(f.token, epicId, NIGHTRUN)).andExpect(status().isBadRequest());
    assertThat(assignmentCount(epicId)).isZero();
  }

  @Test
  void memberMayLabelAndViewerMayNot() throws Exception {
    // TICKET_UPDATE (ab MEMBER), nicht BOARD_UPDATE: Zuordnen ist Kartenarbeit, nicht Pflege der
    // Label-Definitionen des Boards.
    Fixture f = fixture("labels-rbac");
    createLabel(f, NIGHTRUN);
    long cardId = directIngest(f.token, "Karte");

    String memberEmail = "labels-rbac-member@example.com";
    Cookie member = session(memberEmail, PlatformRole.USER);
    invite(f, memberEmail, "MEMBER");
    String memberToken = boundToken(member, f.projectId, f.boardId);

    mvc.perform(addLabel(memberToken, cardId, NIGHTRUN)).andExpect(status().isNoContent());
    mvc.perform(removeLabel(memberToken, cardId, NIGHTRUN)).andExpect(status().isNoContent());

    // Dieselbe Person, dasselbe Token — nur noch VIEWER.
    changeRole(f, memberEmail, "VIEWER");
    mvc.perform(addLabel(memberToken, cardId, NIGHTRUN)).andExpect(status().isForbidden());
    mvc.perform(removeLabel(memberToken, cardId, NIGHTRUN)).andExpect(status().isForbidden());
    assertThat(assignmentCount(cardId)).isZero();
  }

  @Test
  void foreignBoardCardIsNotFound() throws Exception {
    // Eine existierende Karte eines anderen Boards desselben Projekts: die Projektberechtigung
    // allein schützt hier nicht, der Board-Guard tut es.
    Fixture f = fixture("labels-foreign");
    long bug = createLabel(f, "Bug");
    long otherBoard = createBoard(f.session, f.projectId, "Anderes Board");
    long foreignCard = createCard(f.session, otherBoard, "Fremd");
    createLabel(f.session, otherBoard, "Bug");
    setLabels(f.session, foreignCard, List.of(labelIdOf(otherBoard, "Bug")));

    mvc.perform(addLabel(f.token, foreignCard, "Bug")).andExpect(status().isNotFound());
    mvc.perform(removeLabel(f.token, foreignCard, "Bug")).andExpect(status().isNotFound());

    // Unverändert: weiterhin genau das Label des fremden Boards, und keines von diesem hier.
    assertThat(assignedLabelIds(foreignCard)).containsExactly(labelIdOf(otherBoard, "Bug"));
    assertThat(assignedLabelIds(foreignCard)).doesNotContain(bug);
  }

  @Test
  void requiresValidBoundTokenAndKnownCard() throws Exception {
    Fixture f = fixture("labels-token");
    createLabel(f, NIGHTRUN);
    long cardId = directIngest(f.token, "Karte");

    // Ohne Token: 401.
    mvc.perform(
            post("/api/kanban/items/" + cardId + "/labels")
                .contentType("application/json")
                .content("{\"name\":\"" + NIGHTRUN + "\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/kanban/items/" + cardId + "/labels").param("name", NIGHTRUN))
        .andExpect(status().isUnauthorized());

    // Ungültiges Token: 401.
    mvc.perform(addLabel("tk_bogus", cardId, NIGHTRUN)).andExpect(status().isUnauthorized());
    mvc.perform(removeLabel("tk_bogus", cardId, NIGHTRUN)).andExpect(status().isUnauthorized());

    // Gültiges, aber an kein Board gebundenes Token: 409.
    String unbound = unboundToken(f.session);
    mvc.perform(addLabel(unbound, cardId, NIGHTRUN)).andExpect(status().isConflict());
    mvc.perform(removeLabel(unbound, cardId, NIGHTRUN)).andExpect(status().isConflict());

    // Unbekannte Karten-ID: 404.
    mvc.perform(addLabel(f.token, 999_999L, NIGHTRUN)).andExpect(status().isNotFound());
    mvc.perform(removeLabel(f.token, 999_999L, NIGHTRUN)).andExpect(status().isNotFound());

    assertThat(assignmentCount(cardId)).isZero();
  }

  @Test
  void validatesTheLabelName() throws Exception {
    Fixture f = fixture("labels-validation");
    createLabel(f, NIGHTRUN);
    long cardId = directIngest(f.token, "Karte");
    String tooLong = "x".repeat(61);

    // POST: fehlender, blanker und zu langer Name.
    mvc.perform(
            post("/api/kanban/items/" + cardId + "/labels")
                .header("X-Kanban-Token", f.token)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(addLabel(f.token, cardId, "   ")).andExpect(status().isBadRequest());
    mvc.perform(addLabel(f.token, cardId, tooLong)).andExpect(status().isBadRequest());

    // DELETE: fehlender Query-Parameter, blanker und zu langer Name.
    mvc.perform(delete("/api/kanban/items/" + cardId + "/labels").header("X-Kanban-Token", f.token))
        .andExpect(status().isBadRequest());
    mvc.perform(removeLabel(f.token, cardId, "   ")).andExpect(status().isBadRequest());
    mvc.perform(removeLabel(f.token, cardId, tooLong)).andExpect(status().isBadRequest());

    assertThat(assignmentCount(cardId)).isZero();
  }

  @Test
  void acceptsNamesWithSlashesOverTheQueryParameter() throws Exception {
    // Der Name steht beim DELETE im Query-Parameter, weil er jedes Zeichen tragen darf — ein
    // Pfadsegment trüge einen Slash nicht (kodierte Slashes lehnt Tomcat per Default ab).
    Fixture f = fixture("labels-slash");
    createLabel(f, "kit/night run");
    long cardId = directIngest(f.token, "Karte");

    mvc.perform(addLabel(f.token, cardId, "kit/night run")).andExpect(status().isNoContent());
    assertThat(labelsOf(f.token, cardId)).containsExactly("kit/night run");

    mvc.perform(removeLabel(f.token, cardId, "kit/night run")).andExpect(status().isNoContent());
    assertThat(labelsOf(f.token, cardId)).isEmpty();
  }

  // --- Requests -------------------------------------------------------------

  private RequestBuilder addLabel(String token, long cardId, String name) throws Exception {
    return post("/api/kanban/items/" + cardId + "/labels")
        .header("X-Kanban-Token", token)
        .contentType("application/json")
        .content(json.writeValueAsString(java.util.Map.of("name", name)));
  }

  private RequestBuilder removeLabel(String token, long cardId, String name) {
    return delete("/api/kanban/items/" + cardId + "/labels")
        .header("X-Kanban-Token", token)
        .param("name", name);
  }

  private List<String> labelsOf(String token, long cardId) throws Exception {
    JsonNode grouped =
        json.readTree(
            mvc.perform(get("/api/kanban/items").header("X-Kanban-Token", token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode column : grouped) {
      for (JsonNode item : column) {
        if (item.get("id").asLong() == cardId) {
          List<String> names = new ArrayList<>();
          item.get("labels").forEach(label -> names.add(label.asText()));
          return names;
        }
      }
    }
    throw new AssertionError("Karte " + cardId + " nicht in den Items gefunden");
  }

  // --- Datenbankstand -------------------------------------------------------

  private int assignmentCount(long cardId) {
    Integer value =
        jdbc.queryForObject(
            "SELECT count(*) FROM card_label WHERE card_id = ?", Integer.class, cardId);
    return value == null ? 0 : value;
  }

  private List<Long> assignedLabelIds(long cardId) {
    return jdbc.queryForList(
        "SELECT label_id FROM card_label WHERE card_id = ? ORDER BY label_id", Long.class, cardId);
  }

  private int labelCountByName(String name) {
    Integer value =
        jdbc.queryForObject("SELECT count(*) FROM label WHERE name = ?", Integer.class, name);
    return value == null ? 0 : value;
  }

  private boolean labelExists(long labelId) {
    Integer value =
        jdbc.queryForObject("SELECT count(*) FROM label WHERE id = ?", Integer.class, labelId);
    return value != null && value > 0;
  }

  private long labelIdOf(long boardId, String name) {
    Long value =
        jdbc.queryForObject(
            "SELECT id FROM label WHERE board_id = ? AND name = ?", Long.class, boardId, name);
    return value == null ? -1 : value;
  }

  // --- Fixtures -------------------------------------------------------------

  private record Fixture(Cookie session, long projectId, long boardId, String token) {}

  private Fixture fixture(String prefix) throws Exception {
    Cookie owner = session(prefix + "-owner@example.com", PlatformRole.USER);
    Cookie admin = session(prefix + "-admin@example.com", PlatformRole.ADMIN);
    long projectId = createProject(admin, "Projekt " + prefix, prefix + "-owner@example.com");
    long boardId = createBoard(owner, projectId, "Board " + prefix);
    return new Fixture(owner, projectId, boardId, boundToken(owner, projectId, boardId));
  }

  private long createLabel(Fixture f, String name) throws Exception {
    return createLabel(f.session, f.boardId, name);
  }

  private long createLabel(Cookie session, long boardId, String name) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/boards/" + boardId + "/labels")
                        .cookie(session)
                        .contentType("application/json")
                        .content(
                            json.writeValueAsString(
                                java.util.Map.of("name", name, "color", "#ff0000"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private void setLabels(Cookie session, long cardId, List<Long> labelIds) throws Exception {
    mvc.perform(
            put("/api/cards/" + cardId + "/labels")
                .cookie(session)
                .contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("labels", labelIds))))
        .andExpect(status().isOk());
  }

  private long directIngest(String token, String title) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/kanban/items")
                        .header("X-Kanban-Token", token)
                        .contentType("application/json")
                        .content("{\"title\":\"%s\",\"direct\":true}".formatted(title)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private long createEpic(Fixture f, String title, String shortcode) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/boards/" + f.boardId + "/cards")
                        .cookie(f.session)
                        .contentType("application/json")
                        .content(
                            "{\"title\":\"%s\",\"type\":\"EPIC\",\"shortcode\":\"%s\"}"
                                .formatted(title, shortcode)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private long createCard(Cookie session, long boardId, String title) throws Exception {
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
                        .content("{\"title\":\"%s\",\"columnId\":%d}".formatted(title, columnId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private void invite(Fixture f, String email, String role) throws Exception {
    mvc.perform(
            post("/api/projects/" + f.projectId + "/invitations")
                .cookie(f.session)
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role)))
        .andExpect(status().isAccepted());
  }

  private void changeRole(Fixture f, String email, String role) throws Exception {
    long userId = users.findByEmail(email).orElseThrow().requireId();
    mvc.perform(
            patch("/api/projects/" + f.projectId + "/members/" + userId)
                .cookie(f.session)
                .contentType("application/json")
                .content("{\"role\":\"%s\"}".formatted(role)))
        .andExpect(status().isOk());
  }

  private String boundToken(Cookie session, long projectId, long boardId) throws Exception {
    return json.readTree(
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
                .getContentAsString())
        .get("plaintext")
        .asText();
  }

  private String unboundToken(Cookie session) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(session)
                        .contentType("application/json")
                        .content("{\"name\":\"unbound\"}"))
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
