package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-End-Test für Karten-CRUD, board-scoped Nummern, Abhängigkeiten und Archiv-Flow. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CardIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().id();
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
    if (users.findByEmail(ownerEmail).isEmpty()) {
      users.save(
          new AppUser(
              null,
              ownerEmail,
              passwordEncoder.encode(PASSWORD),
              "Person",
              true,
              PlatformRole.USER));
    }
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
    String email = "project-admin@example.com";
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

  private JsonNode createCard(
      Cookie session, long boardId, long columnId, String title, String depsJson) throws Exception {
    String deps = depsJson == null ? "" : ",\"dependencies\":" + depsJson;
    String body =
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"columnId\":%d,\"title\":\"%s\"%s}".formatted(columnId, title, deps)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private long createEpic(Cookie session, long boardId, String shortcode) throws Exception {
    String body =
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(session)
                    .contentType("application/json")
                    .content(
                        "{\"title\":\"Epic\",\"type\":\"EPIC\",\"shortcode\":\"%s\"}"
                            .formatted(shortcode)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  @Test
  void cardNumbersAreSequentialAndBoardScoped() throws Exception {
    Cookie alice = loginAs("card-owner@example.com");
    long projectId = createProject("card-owner@example.com", "P");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();

    int n1 = createCard(alice, boardId, columnId, "A", null).get("number").asInt();
    int n2 = createCard(alice, boardId, columnId, "B", null).get("number").asInt();
    int n3 = createCard(alice, boardId, columnId, "C", null).get("number").asInt();
    org.assertj.core.api.Assertions.assertThat(new int[] {n1, n2, n3}).containsExactly(1, 2, 3);

    // Zweites Board startet wieder bei 1 (board-scoped).
    JsonNode board2 = createBoard(alice, projectId);
    long boardId2 = board2.get("id").asLong();
    long columnId2 = board2.get("columns").get(0).get("id").asLong();
    org.assertj.core.api.Assertions.assertThat(
            createCard(alice, boardId2, columnId2, "X", null).get("number").asInt())
        .isEqualTo(1);
  }

  @Test
  void dependencyValidation() throws Exception {
    Cookie alice = loginAs("dep-owner@example.com");
    long projectId = createProject("dep-owner@example.com", "Dep");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();

    createCard(alice, boardId, columnId, "First", null); // number 1
    JsonNode second = createCard(alice, boardId, columnId, "Second", "[1]"); // hängt von #1 ab
    org.assertj.core.api.Assertions.assertThat(second.get("dependencies").get(0).asInt())
        .isEqualTo(1);
    long secondId = second.get("id").asLong();
    int secondNumber = second.get("number").asInt();

    // Unbekannte Nummer -> 400
    mvc.perform(
            post("/api/boards/" + boardId + "/cards")
                .cookie(alice)
                .contentType("application/json")
                .content(
                    "{\"columnId\":%d,\"title\":\"Z\",\"dependencies\":[999]}".formatted(columnId)))
        .andExpect(status().isBadRequest());

    // Selbstreferenz -> 400
    mvc.perform(
            patch("/api/cards/" + secondId)
                .cookie(alice)
                .contentType("application/json")
                .content("{\"title\":\"Second\",\"dependencies\":[%d]}".formatted(secondNumber)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void archiveRestoreFlow() throws Exception {
    Cookie alice = loginAs("arch-owner@example.com");
    long projectId = createProject("arch-owner@example.com", "Arch");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    long cardId = createCard(alice, boardId, columnId, "Karte", null).get("id").asLong();

    mvc.perform(post("/api/cards/" + cardId + "/archive").cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true));

    // Eine neue Karte in derselben Spalte kollidiert nicht (archivierte liegt außerhalb des
    // Namespace).
    createCard(alice, boardId, columnId, "Nachrücker", null);

    mvc.perform(post("/api/cards/" + cardId + "/restore").cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(false));
  }

  @Test
  void ideaStorageAndPromoteFlow() throws Exception {
    Cookie alice = loginAs("idea-owner@example.com");
    long projectId = createProject("idea-owner@example.com", "Idea");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    long cardId = createCard(alice, boardId, columnId, "Idee", null).get("id").asLong();

    // Demotion: Karte in den Ideen-Speicher -> ideaStored=true, aktive Position fällt weg.
    mvc.perform(post("/api/cards/" + cardId + "/idea-storage").cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ideaStored").value(true));

    // Die Idee taucht weiter in der Kartenliste auf (mit ideaStored=true) — Board-Unsichtbarkeit
    // filtert das Frontend.
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardId + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].ideaStored").value(true));

    // Eine neue Karte an Position 0 derselben Spalte kollidiert nicht (Idee außerhalb des
    // Namespace).
    createCard(alice, boardId, columnId, "Nachrücker", null);

    // Promotion: Idee zurück ins Backlog (erste Spalte) am Ende -> ideaStored=false.
    mvc.perform(post("/api/cards/" + cardId + "/promote").cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ideaStored").value(false))
        .andExpect(jsonPath("$.columnId").value((int) columnId));
  }

  @Test
  void createCardDirectlyAsIdeaViaRest() throws Exception {
    Cookie alice = loginAs("create-idea-owner@example.com");
    long projectId = createProject("create-idea-owner@example.com", "CreateIdea");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();

    // Anlegen mit ideaStored=true erzeugt direkt eine Idee.
    mvc.perform(
            post("/api/boards/" + boardId + "/cards")
                .cookie(alice)
                .contentType("application/json")
                .content(
                    "{\"columnId\":%d,\"title\":\"Direkt-Idee\",\"ideaStored\":true}"
                        .formatted(columnId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ideaStored").value(true));
  }

  @Test
  void ideaStorageRejectsEpic() throws Exception {
    Cookie alice = loginAs("idea-epic-owner@example.com");
    long projectId = createProject("idea-epic-owner@example.com", "IdeaEpic");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long epicId = createEpic(alice, boardId, "EP");

    mvc.perform(post("/api/cards/" + epicId + "/idea-storage").cookie(alice))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/cards/" + epicId + "/promote").cookie(alice))
        .andExpect(status().isBadRequest());
  }

  @Test
  void bulkArchiveArchivesEveryCardAndEpic() throws Exception {
    Cookie alice = loginAs("bulk-arch-owner@example.com");
    long projectId = createProject("bulk-arch-owner@example.com", "BulkArch");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    long c1 = createCard(alice, boardId, columnId, "Eins", null).get("id").asLong();
    long c2 = createCard(alice, boardId, columnId, "Zwei", null).get("id").asLong();
    String epicBody =
        mvc.perform(
                post("/api/boards/" + boardId + "/cards")
                    .cookie(alice)
                    .contentType("application/json")
                    .content("{\"title\":\"Epic\",\"type\":\"EPIC\",\"shortcode\":\"EP\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long epicId = json.readTree(epicBody).get("id").asLong();

    mvc.perform(
            post("/api/cards/bulk-archive")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"cardIds\":[%d,%d,%d]}".formatted(c1, c2, epicId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].archived").value(true))
        .andExpect(jsonPath("$[1].archived").value(true))
        .andExpect(jsonPath("$[2].archived").value(true)); // die Epic (3. in der Liste)

    // Persistenz: die Karten bleiben in der (client-seitig gefilterten) Board-Liste, aber
    // archiviert.
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardId + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].archived").value(true))
        .andExpect(jsonPath("$[1].archived").value(true));
  }

  @Test
  void bulkArchiveRollsBackWhenPermissionMissingOnOneCard() throws Exception {
    Cookie alice = loginAs("bulk-rb-owner@example.com");
    Cookie bob = loginAs("bulk-rb-bob@example.com");
    long p1 = createProject("bulk-rb-owner@example.com", "BulkRb1");
    long p2 = createProject("bulk-rb-bob@example.com", "BulkRb2");
    JsonNode boardA = createBoard(alice, p1);
    JsonNode boardB = createBoard(bob, p2);
    long boardIdA = boardA.get("id").asLong();
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long ownCard = createCard(alice, boardIdA, colA, "Meine", null).get("id").asLong();
    long foreignCard = createCard(bob, boardIdB, colB, "Fremde", null).get("id").asLong();
    // alice ist in bobs Projekt nur VIEWER -> kein TICKET_DELETE.
    memberships.save(
        new ProjectMembership(
            null, p2, userId("bulk-rb-owner@example.com"), ProjectRole.VIEWER, Instant.now()));

    mvc.perform(
            post("/api/cards/bulk-archive")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"cardIds\":[%d,%d]}".formatted(ownCard, foreignCard)))
        .andExpect(status().isForbidden());

    // Rollback: alices eigene Karte ist weiterhin aktiv (nicht archiviert).
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardIdA + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].archived").value(false));
  }

  @Test
  void bulkArchiveRejectsEmptyAndOversizedList() throws Exception {
    Cookie alice = loginAs("bulk-val-owner@example.com");

    mvc.perform(
            post("/api/cards/bulk-archive")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"cardIds\":[]}"))
        .andExpect(status().isBadRequest());

    String tooMany =
        java.util.stream.IntStream.rangeClosed(1, 201)
            .mapToObj(Integer::toString)
            .collect(java.util.stream.Collectors.joining(","));
    mvc.perform(
            post("/api/cards/bulk-archive")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"cardIds\":[%s]}".formatted(tooMany)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void bulkTransferMovesEveryCardToTargetForOwner() throws Exception {
    Cookie alice = loginAs("bulk-xfer-owner@example.com");
    long p1 = createProject("bulk-xfer-owner@example.com", "BulkXfer1");
    long p2 = createProject("bulk-xfer-owner@example.com", "BulkXfer2");
    JsonNode boardA = createBoard(alice, p1);
    JsonNode boardB = createBoard(alice, p2);
    long boardIdA = boardA.get("id").asLong();
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long c1 = createCard(alice, boardIdA, colA, "Eins", null).get("id").asLong();
    long c2 = createCard(alice, boardIdA, colA, "Zwei", null).get("id").asLong();

    mvc.perform(
            post("/api/cards/bulk-transfer")
                .cookie(alice)
                .contentType("application/json")
                .content(
                    "{\"cardIds\":[%d,%d],\"targetBoardId\":%d,\"targetColumnId\":%d}"
                        .formatted(c1, c2, boardIdB, colB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].boardId").value((int) boardIdB))
        .andExpect(jsonPath("$[1].boardId").value((int) boardIdB));

    // Quellboard leer, Zielboard hält beide (umgehängten) Karten.
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardIdA + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardIdB + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void bulkTransferRollsBackWhenNotOwnerInTargetProject() throws Exception {
    Cookie alice = loginAs("bulk-xfer-rb-owner@example.com");
    Cookie bob = loginAs("bulk-xfer-rb-bob@example.com");
    long p1 = createProject("bulk-xfer-rb-owner@example.com", "BulkXferRb1");
    long p2 = createProject("bulk-xfer-rb-bob@example.com", "BulkXferRb2");
    JsonNode boardA = createBoard(alice, p1);
    JsonNode boardB = createBoard(bob, p2);
    long boardIdA = boardA.get("id").asLong();
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long c1 = createCard(alice, boardIdA, colA, "Eins", null).get("id").asLong();
    long c2 = createCard(alice, boardIdA, colA, "Zwei", null).get("id").asLong();
    // alice ist in bobs Zielprojekt nur MEMBER -> kein Owner-Recht zum Transfer.
    memberships.save(
        new ProjectMembership(
            null, p2, userId("bulk-xfer-rb-owner@example.com"), ProjectRole.MEMBER, Instant.now()));

    mvc.perform(
            post("/api/cards/bulk-transfer")
                .cookie(alice)
                .contentType("application/json")
                .content(
                    "{\"cardIds\":[%d,%d],\"targetBoardId\":%d,\"targetColumnId\":%d}"
                        .formatted(c1, c2, boardIdB, colB)))
        .andExpect(status().isForbidden());

    // Rollback: beide Karten liegen weiterhin im Quellboard.
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardIdA + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void bulkTransferRollsBackWhenSelectionContainsEpic() throws Exception {
    Cookie alice = loginAs("bulk-xfer-epic-owner@example.com");
    long p1 = createProject("bulk-xfer-epic-owner@example.com", "BulkXferEpic1");
    long p2 = createProject("bulk-xfer-epic-owner@example.com", "BulkXferEpic2");
    JsonNode boardA = createBoard(alice, p1);
    JsonNode boardB = createBoard(alice, p2);
    long boardIdA = boardA.get("id").asLong();
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long c1 = createCard(alice, boardIdA, colA, "Karte", null).get("id").asLong();
    long epicId = createEpic(alice, boardIdA, "EP");

    mvc.perform(
            post("/api/cards/bulk-transfer")
                .cookie(alice)
                .contentType("application/json")
                .content(
                    "{\"cardIds\":[%d,%d],\"targetBoardId\":%d,\"targetColumnId\":%d}"
                        .formatted(c1, epicId, boardIdB, colB)))
        .andExpect(status().isBadRequest());

    // Rollback: die Karte ist nicht ins Zielboard gewandert.
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardIdA + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardIdB + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void bulkTransferRejectsEmptyAndOversizedList() throws Exception {
    Cookie alice = loginAs("bulk-xfer-val-owner@example.com");

    mvc.perform(
            post("/api/cards/bulk-transfer")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"cardIds\":[],\"targetBoardId\":1,\"targetColumnId\":1}"))
        .andExpect(status().isBadRequest());

    String tooMany =
        java.util.stream.IntStream.rangeClosed(1, 201)
            .mapToObj(Integer::toString)
            .collect(java.util.stream.Collectors.joining(","));
    mvc.perform(
            post("/api/cards/bulk-transfer")
                .cookie(alice)
                .contentType("application/json")
                .content(
                    "{\"cardIds\":[%s],\"targetBoardId\":1,\"targetColumnId\":1}"
                        .formatted(tooMany)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void bulkDeleteMovesEveryCardToTrash() throws Exception {
    Cookie alice = loginAs("bulk-del-owner@example.com");
    long projectId = createProject("bulk-del-owner@example.com", "BulkDel");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    long c1 = createCard(alice, boardId, columnId, "Eins", null).get("id").asLong();
    long c2 = createCard(alice, boardId, columnId, "Zwei", null).get("id").asLong();

    mvc.perform(
            post("/api/cards/bulk-delete")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"cardIds\":[%d,%d]}".formatted(c1, c2)))
        .andExpect(status().isNoContent());

    // Aktive Liste leer, beide Karten im Papierkorb.
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardId + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardId + "/trash")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void bulkDeleteRollsBackWhenPermissionMissingOnOneCard() throws Exception {
    Cookie alice = loginAs("bulk-del-rb-owner@example.com");
    Cookie bob = loginAs("bulk-del-rb-bob@example.com");
    long p1 = createProject("bulk-del-rb-owner@example.com", "BulkDelRb1");
    long p2 = createProject("bulk-del-rb-bob@example.com", "BulkDelRb2");
    JsonNode boardA = createBoard(alice, p1);
    JsonNode boardB = createBoard(bob, p2);
    long boardIdA = boardA.get("id").asLong();
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long ownCard = createCard(alice, boardIdA, colA, "Meine", null).get("id").asLong();
    long foreignCard = createCard(bob, boardIdB, colB, "Fremde", null).get("id").asLong();
    // alice ist in bobs Projekt nur VIEWER -> kein TICKET_DELETE.
    memberships.save(
        new ProjectMembership(
            null, p2, userId("bulk-del-rb-owner@example.com"), ProjectRole.VIEWER, Instant.now()));

    mvc.perform(
            post("/api/cards/bulk-delete")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"cardIds\":[%d,%d]}".formatted(ownCard, foreignCard)))
        .andExpect(status().isForbidden());

    // Rollback: alices eigene Karte ist weiterhin aktiv (nicht im Papierkorb).
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardIdA + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].archived").value(false));
  }

  @Test
  void bulkDeleteRejectsEmptyAndOversizedList() throws Exception {
    Cookie alice = loginAs("bulk-del-val-owner@example.com");

    mvc.perform(
            post("/api/cards/bulk-delete")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"cardIds\":[]}"))
        .andExpect(status().isBadRequest());

    String tooMany =
        java.util.stream.IntStream.rangeClosed(1, 201)
            .mapToObj(Integer::toString)
            .collect(java.util.stream.Collectors.joining(","));
    mvc.perform(
            post("/api/cards/bulk-delete")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"cardIds\":[%s]}".formatted(tooMany)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateAndDelete() throws Exception {
    Cookie alice = loginAs("ud-owner@example.com");
    long projectId = createProject("ud-owner@example.com", "UD");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    long cardId = createCard(alice, boardId, columnId, "Alt", null).get("id").asLong();

    mvc.perform(
            patch("/api/cards/" + cardId)
                .cookie(alice)
                .contentType("application/json")
                .content("{\"title\":\"Neu\",\"description\":\"**md**\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Neu"))
        .andExpect(jsonPath("$.description").value("**md**"));

    mvc.perform(delete("/api/cards/" + cardId).cookie(alice)).andExpect(status().isNoContent());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardId + "/cards")
                .cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void viewerCannotCreateCard() throws Exception {
    Cookie alice = loginAs("cc-owner@example.com");
    Cookie viewer = loginAs("cc-viewer@example.com");
    long projectId = createProject("cc-owner@example.com", "V");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    memberships.save(
        new ProjectMembership(
            null, projectId, userId("cc-viewer@example.com"), ProjectRole.VIEWER, Instant.now()));

    mvc.perform(
            post("/api/boards/" + boardId + "/cards")
                .cookie(viewer)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"title\":\"X\"}".formatted(columnId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void transferMovesCardAcrossProjectsForOwner() throws Exception {
    Cookie alice = loginAs("xfer-owner@example.com");
    long p1 = createProject("xfer-owner@example.com", "XferP1");
    long p2 = createProject("xfer-owner@example.com", "XferP2");
    JsonNode boardA = createBoard(alice, p1);
    JsonNode boardB = createBoard(alice, p2);
    long boardIdA = boardA.get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long cardId = createCard(alice, boardIdA, colA, "Wanderkarte", null).get("id").asLong();

    mvc.perform(
            post("/api/cards/" + cardId + "/transfer")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"targetBoardId\":%d,\"targetColumnId\":%d}".formatted(boardIdB, colB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.boardId").value((int) boardIdB))
        .andExpect(jsonPath("$.columnId").value((int) colB));

    // Quellboard leer, Zielboard hält die (umgehängte) Karte.
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardIdA + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardIdB + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value((int) cardId));
  }

  @Test
  void transferForbiddenForNonOwner() throws Exception {
    Cookie alice = loginAs("xfer-rbac-owner@example.com");
    Cookie mallory = loginAs("xfer-rbac-member@example.com");
    long p1 = createProject("xfer-rbac-owner@example.com", "XferRbac1");
    long p2 = createProject("xfer-rbac-owner@example.com", "XferRbac2");
    JsonNode boardA = createBoard(alice, p1);
    JsonNode boardB = createBoard(alice, p2);
    long boardIdA = boardA.get("id").asLong();
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long cardId = createCard(alice, boardIdA, colA, "Karte", null).get("id").asLong();
    memberships.save(
        new ProjectMembership(
            null, p1, userId("xfer-rbac-member@example.com"), ProjectRole.MEMBER, Instant.now()));

    mvc.perform(
            post("/api/cards/" + cardId + "/transfer")
                .cookie(mallory)
                .contentType("application/json")
                .content("{\"targetBoardId\":%d,\"targetColumnId\":%d}".formatted(boardIdB, colB)))
        .andExpect(status().isForbidden());
  }
}
