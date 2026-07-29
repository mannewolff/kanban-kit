package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-End-Test des Spalten-Sortierens nach Kartennummer (Issue #504): Reihenfolge in beide
 * Richtungen, Unversehrtheit der Karten außerhalb des aktiven Positions-Namespace und der übrigen
 * Spalten sowie die Rechte-/404-Wachen.
 *
 * <p>Positionen werden direkt aus der Datenbank gelesen: archivierte, gelöschte und
 * Ideen-Speicher-Karten sowie Epics erscheinen in der Board-Kartenliste gar nicht oder ohne
 * Position — genau sie müssen aber nachweislich unverändert bleiben.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardSortByNumberIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  private Cookie login;
  private long projectId;
  private long boardId;
  private long backlog;
  private long ready;

  private Cookie loginAs(String email, PlatformRole role) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "Person", true, role));
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

  private void setup(String email) throws Exception {
    login = loginAs(email, PlatformRole.USER);
    Cookie admin = loginAs("sort-admin@example.com", PlatformRole.ADMIN);
    String project =
        mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"P\",\"ownerEmail\":\"%s\"}".formatted(email)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    projectId = json.readTree(project).get("id").asLong();
    JsonNode board =
        json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/boards")
                        .cookie(login)
                        .contentType("application/json")
                        .content("{\"name\":\"B\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    boardId = board.get("id").asLong();
    // Default-Spalten: [0]=Backlog, [1]=Ready, …
    backlog = board.get("columns").get(0).get("id").asLong();
    ready = board.get("columns").get(1).get("id").asLong();
  }

  private long createCard(long columnId, String title) throws Exception {
    return createCard("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title));
  }

  private long createCard(String body) throws Exception {
    String response =
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(login)
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(response).get("id").asLong();
  }

  private void move(long cardId, long columnId, int position) throws Exception {
    mvc.perform(
            post("/api/cards/" + cardId + "/move")
                .cookie(login)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"position\":%d}".formatted(columnId, position)))
        .andExpect(status().isOk());
  }

  private void sort(long columnId, String direction) throws Exception {
    sort(login, columnId, direction).andExpect(status().isNoContent());
  }

  private org.springframework.test.web.servlet.ResultActions sort(
      Cookie session, long columnId, String direction) throws Exception {
    return mvc.perform(
        post("/api/columns/" + columnId + "/cards/sort-by-number")
            .cookie(session)
            .contentType("application/json")
            .content("{\"direction\":\"%s\"}".formatted(direction)));
  }

  private int position(long cardId) {
    Integer position =
        jdbc.queryForObject(
            "SELECT position_in_column FROM card WHERE id = ?", Integer.class, cardId);
    return position == null ? -1 : position;
  }

  @Test
  void ascendingOrdersActiveCardsByNumberWithoutGaps() throws Exception {
    setup("sort-asc@example.com");
    long a = createCard(backlog, "A"); // #1
    long b = createCard(backlog, "B"); // #2
    long c = createCard(backlog, "C"); // #3
    move(c, backlog, 0); // ungeordnet: C, A, B

    sort(backlog, "ASC");

    Assertions.assertThat(position(a)).isZero();
    Assertions.assertThat(position(b)).isEqualTo(1);
    Assertions.assertThat(position(c)).isEqualTo(2);
  }

  @Test
  void descendingOrdersActiveCardsByNumberDescending() throws Exception {
    setup("sort-desc@example.com");
    long a = createCard(backlog, "A"); // #1
    long b = createCard(backlog, "B"); // #2
    long c = createCard(backlog, "C"); // #3

    sort(backlog, "DESC");

    Assertions.assertThat(position(c)).isZero();
    Assertions.assertThat(position(b)).isEqualTo(1);
    Assertions.assertThat(position(a)).isEqualTo(2);
  }

  @Test
  void leavesInactiveCardsEpicsAndOtherColumnsUntouched() throws Exception {
    setup("sort-scope@example.com");
    long a = createCard(backlog, "A"); // #1, pos 0
    long b = createCard(backlog, "B"); // #2, pos 1
    long archived = createCard(backlog, "Archiviert"); // #3, pos 2
    mvc.perform(post("/api/cards/" + archived + "/archive").cookie(login))
        .andExpect(status().isOk());
    long trashed = createCard(backlog, "Papierkorb"); // #4, pos 3
    mvc.perform(delete("/api/cards/" + trashed).cookie(login)).andExpect(status().isNoContent());
    long idea =
        createCard("{\"columnId\":%d,\"title\":\"Idee\",\"ideaStored\":true}".formatted(backlog));
    long epic = createCard("{\"title\":\"Epic\",\"type\":\"EPIC\"}");
    long other = createCard(ready, "Andere Spalte"); // Ready, pos 0
    move(b, backlog, 0); // Backlog ungeordnet: B, A

    int archivedBefore = position(archived);
    int trashedBefore = position(trashed);
    int ideaBefore = position(idea);
    int epicBefore = position(epic);
    int otherBefore = position(other);

    sort(backlog, "ASC");

    Assertions.assertThat(position(a)).isZero();
    Assertions.assertThat(position(b)).isEqualTo(1);
    Assertions.assertThat(position(archived)).isEqualTo(archivedBefore);
    Assertions.assertThat(position(trashed)).isEqualTo(trashedBefore);
    Assertions.assertThat(position(idea)).isEqualTo(ideaBefore);
    Assertions.assertThat(position(epic)).isEqualTo(epicBefore);
    Assertions.assertThat(position(other)).isEqualTo(otherBefore);
  }

  @Test
  void emptyColumnIsSortedWithoutError() throws Exception {
    setup("sort-empty@example.com");

    sort(ready, "ASC");
  }

  @Test
  void unknownColumnIsNotFound() throws Exception {
    setup("sort-404@example.com");

    sort(login, 999_999L, "ASC").andExpect(status().isNotFound());
  }

  @Test
  void memberWithoutCardMoveIsForbidden() throws Exception {
    setup("sort-403-owner@example.com");
    Cookie viewer = loginAs("sort-403-viewer@example.com", PlatformRole.USER);
    memberships.save(
        new ProjectMembership(
            null,
            projectId,
            users.findByEmail("sort-403-viewer@example.com").orElseThrow().id(),
            ProjectRole.VIEWER,
            Instant.now()));

    sort(viewer, backlog, "ASC").andExpect(status().isForbidden());
  }

  @Test
  void nonMemberIsNotFound() throws Exception {
    setup("sort-stranger-owner@example.com");
    Cookie stranger = loginAs("sort-stranger@example.com", PlatformRole.USER);

    // Nichtmitglied: 404 statt 403 — kein Existenz-Leak.
    sort(stranger, backlog, "ASC").andExpect(status().isNotFound());
  }
}
