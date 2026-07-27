package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-End-Test für Karten-CRUD, projektweite Nummern, Abhängigkeiten und Archiv-Flow. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
// End-to-End-Suite deckt Karten-CRUD, Nummerierung, Abhängigkeiten, Transfer und Archiv-Flow
// ab; die Methodenzahl ist für eine IT dieses Umfangs gewollt, nicht ein Refactoring-Signal.
@SuppressWarnings("PMD.TooManyMethods")
class CardIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

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
  void cardNumbersAreSequentialAndProjectScoped() throws Exception {
    Cookie alice = loginAs("card-owner@example.com");
    long projectId = createProject("card-owner@example.com", "P");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();

    int n1 = createCard(alice, boardId, columnId, "A", null).get("number").asInt();
    int n2 = createCard(alice, boardId, columnId, "B", null).get("number").asInt();
    int n3 = createCard(alice, boardId, columnId, "C", null).get("number").asInt();
    org.assertj.core.api.Assertions.assertThat(new int[] {n1, n2, n3}).containsExactly(1, 2, 3);

    // Zweites Board desselben Projekts zählt projektweit weiter (nicht wieder bei 1).
    JsonNode board2 = createBoard(alice, projectId);
    long boardId2 = board2.get("id").asLong();
    long columnId2 = board2.get("columns").get(0).get("id").asLong();
    org.assertj.core.api.Assertions.assertThat(
            createCard(alice, boardId2, columnId2, "X", null).get("number").asInt())
        .isEqualTo(4);
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
  void ideaStorageMovesCardIntoTheProjectwideIdeaPool() throws Exception {
    Cookie alice = loginAs("idea-owner@example.com");
    long projectId = createProject("idea-owner@example.com", "Idea");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();
    JsonNode created = createCard(alice, boardId, columnId, "Idee", null);
    long cardId = created.get("id").asLong();
    int number = created.get("number").asInt();

    // In den Ideen-Speicher: die Karte wird board-los (#433), behält ihre Nummer und notiert das
    // bisherige Board als Zielboard-Hinweis.
    mvc.perform(post("/api/cards/" + cardId + "/idea-storage").cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ideaStored").value(true))
        .andExpect(jsonPath("$.boardId").doesNotExist())
        .andExpect(jsonPath("$.number").value(number));

    // Sie verschwindet aus der Board-Kartenliste ...
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/boards/" + boardId + "/cards")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(0));

    // ... und erscheint stattdessen im projektweiten Ideen-Pool, mit unveränderter Nummer.
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/projects/" + projectId + "/ideas")
                .cookie(alice))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(cardId))
        .andExpect(jsonPath("$[0].number").value(number));

    // Eine neue Karte an Position 0 derselben Spalte kollidiert nicht (die Idee belegt keine
    // aktive Position mehr).
    createCard(alice, boardId, columnId, "Nachrücker", null);

    // Zurück aufs Board (Einplanen) -> wieder in der ersten Spalte, dieselbe Nummer.
    mvc.perform(
            put("/api/cards/" + cardId + "/plan")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"targetBoardId\":" + boardId + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ideaStored").value(false))
        .andExpect(jsonPath("$.columnId").value((int) columnId))
        .andExpect(jsonPath("$.number").value(number));
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

  /**
   * Pinnt den Reihenfolge-Vertrag von {@code bulk-transfer}: die übergebenen Karten landen in
   * Eingabereihenfolge als geschlossener Block am Ende der Zielspalte, die dort bereits liegenden
   * Karten behalten ihre Reihenfolge und die Quellspalte wird lückenlos nachgezogen. Das Backend
   * sortiert bewusst nicht selbst um — die Sichtreihenfolge herzustellen ist Sache des Aufrufers.
   */
  @Test
  void bulkTransferAppendsInInputOrderToPopulatedTargetColumn() throws Exception {
    Cookie alice = loginAs("bulk-xfer-order@example.com");
    long p1 = createProject("bulk-xfer-order@example.com", "BulkXferOrder1");
    long p2 = createProject("bulk-xfer-order@example.com", "BulkXferOrder2");
    JsonNode boardA = createBoard(alice, p1);
    JsonNode boardB = createBoard(alice, p2);
    long boardIdA = boardA.get("id").asLong();
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    // Zielspalte ist bereits befüllt: der Block muss hinter diesen beiden landen.
    long alt1 = createCard(alice, boardIdB, colB, "Alt1", null).get("id").asLong();
    long alt2 = createCard(alice, boardIdB, colB, "Alt2", null).get("id").asLong();
    long c1 = createCard(alice, boardIdA, colA, "Eins", null).get("id").asLong();
    long c2 = createCard(alice, boardIdA, colA, "Zwei", null).get("id").asLong();
    long c3 = createCard(alice, boardIdA, colA, "Drei", null).get("id").asLong();
    long bleibt = createCard(alice, boardIdA, colA, "Bleibt", null).get("id").asLong();

    // Eingabereihenfolge bewusst ungleich der Anlage-Reihenfolge.
    mvc.perform(
            post("/api/cards/bulk-transfer")
                .cookie(alice)
                .contentType("application/json")
                .content(
                    "{\"cardIds\":[%d,%d,%d],\"targetBoardId\":%d,\"targetColumnId\":%d}"
                        .formatted(c3, c1, c2, boardIdB, colB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));

    JsonNode target = boardCards(alice, boardIdB);
    org.assertj.core.api.Assertions.assertThat(
            new int[] {
              positionOf(target, alt1),
              positionOf(target, alt2),
              positionOf(target, c3),
              positionOf(target, c1),
              positionOf(target, c2)
            })
        .containsExactly(0, 1, 2, 3, 4);

    // Quellspalte lückenlos nachgezogen: die verbliebene Karte rutscht auf Position 0.
    JsonNode source = boardCards(alice, boardIdA);
    org.assertj.core.api.Assertions.assertThat(source.size()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(positionOf(source, bleibt)).isZero();
  }

  private JsonNode boardCards(Cookie session, long boardId) throws Exception {
    return json.readTree(
        mvc.perform(get("/api/boards/" + boardId + "/cards").cookie(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private int positionOf(JsonNode cards, long cardId) {
    for (JsonNode c : cards) {
      if (c.get("id").asLong() == cardId) {
        return c.get("positionInColumn").asInt();
      }
    }
    throw new AssertionError("Karte " + cardId + " nicht in der Antwort");
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

  @Test
  void startNumberFloorsCardNumbering() throws Exception {
    Cookie alice = loginAs("startnum-owner@example.com");
    long projectId = createProject("startnum-owner@example.com", "StartNum");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();

    // Startnummer 13457 setzen — der Owner-Endpoint dafür kommt in #390, hier direkt per SQL.
    jdbc.update("UPDATE project SET next_card_number = 13457 WHERE id = ?", projectId);

    // Leeres Projekt + Startnummer → erste Karte 13457, zweite 13458 (ab dann gewinnt max+1).
    org.assertj.core.api.Assertions.assertThat(
            createCard(alice, boardId, columnId, "A", null).get("number").asInt())
        .isEqualTo(13457);
    org.assertj.core.api.Assertions.assertThat(
            createCard(alice, boardId, columnId, "B", null).get("number").asInt())
        .isEqualTo(13458);

    // Startnummer unter dem aktuellen Max → GREATEST ignoriert sie, max+1 (13459) gewinnt.
    jdbc.update("UPDATE project SET next_card_number = 100 WHERE id = ?", projectId);
    org.assertj.core.api.Assertions.assertThat(
            createCard(alice, boardId, columnId, "C", null).get("number").asInt())
        .isEqualTo(13459);
  }

  @Test
  void nextCardNumberEndpoint_setsAndGuardsStartNumber() throws Exception {
    Cookie alice = loginAs("nextnum-owner@example.com");
    Cookie mallory = loginAs("nextnum-member@example.com");
    long projectId = createProject("nextnum-owner@example.com", "NextNum");
    JsonNode board = createBoard(alice, projectId);
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();

    // GET auf leerem Projekt → effektiv 1.
    mvc.perform(get("/api/projects/" + projectId + "/next-card-number").cookie(alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextCardNumber").value(1));

    // Owner setzt 13457 → GET/PUT liefern 13457, erste Karte trägt 13457.
    mvc.perform(
            put("/api/projects/" + projectId + "/next-card-number")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"nextCardNumber\":13457}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextCardNumber").value(13457));
    org.assertj.core.api.Assertions.assertThat(
            createCard(alice, boardId, columnId, "A", null).get("number").asInt())
        .isEqualTo(13457);

    // Wert ≤ höchste vergebene Nummer (jetzt 13457) → 400.
    mvc.perform(
            put("/api/projects/" + projectId + "/next-card-number")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"nextCardNumber\":13457}"))
        .andExpect(status().isBadRequest());

    // Nicht-Owner (nur Mitglied) → 403.
    memberships.save(
        new ProjectMembership(
            null,
            projectId,
            userId("nextnum-member@example.com"),
            ProjectRole.MEMBER,
            Instant.now()));
    mvc.perform(
            put("/api/projects/" + projectId + "/next-card-number")
                .cookie(mallory)
                .contentType("application/json")
                .content("{\"nextCardNumber\":99999}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void dependencyReferencesAnotherBoardInSameProject() throws Exception {
    Cookie alice = loginAs("xboard-dep-owner@example.com");
    long projectId = createProject("xboard-dep-owner@example.com", "XBoardDep");
    JsonNode boardA = createBoard(alice, projectId);
    JsonNode boardB = createBoard(alice, projectId);
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();

    // #1 auf Board A; #2 auf Board B hängt projektweit von #1 (anderes Board) ab — vor #386 wäre
    // die board-lokale Validierung an der fremden Nummer gescheitert.
    createCard(alice, boardA.get("id").asLong(), colA, "OnA", null); // Nummer 1
    JsonNode onB = createCard(alice, boardIdB, colB, "OnB", "[1]");
    org.assertj.core.api.Assertions.assertThat(onB.get("number").asInt()).isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(onB.get("dependencies").get(0).asInt()).isEqualTo(1);
  }

  @Test
  void transferWithinProjectKeepsNumberAndDependencies() throws Exception {
    Cookie alice = loginAs("xfer-same-owner@example.com");
    long projectId = createProject("xfer-same-owner@example.com", "XferSame");
    JsonNode boardA = createBoard(alice, projectId);
    JsonNode boardB = createBoard(alice, projectId);
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();

    createCard(alice, boardA.get("id").asLong(), colA, "Dep", null); // Nummer 1
    JsonNode card =
        createCard(alice, boardA.get("id").asLong(), colA, "Wanderer", "[1]"); // Nummer 2
    long cardId = card.get("id").asLong();

    // Umzug ins Schwesterboard desselben Projekts: Nummer bleibt 2, Abhängigkeit auf #1 bleibt.
    String moved =
        mvc.perform(
                post("/api/cards/" + cardId + "/transfer")
                    .cookie(alice)
                    .contentType("application/json")
                    .content(
                        "{\"targetBoardId\":%d,\"targetColumnId\":%d}".formatted(boardIdB, colB)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.boardId").value((int) boardIdB))
            .andExpect(jsonPath("$.number").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    org.assertj.core.api.Assertions.assertThat(
            json.readTree(moved).get("dependencies").get(0).asInt())
        .isEqualTo(1);
  }

  @Test
  void transferAcrossProjectsReassignsNumberAndDropsDependencies() throws Exception {
    Cookie alice = loginAs("xfer-drop-owner@example.com");
    long p1 = createProject("xfer-drop-owner@example.com", "XferDrop1");
    long p2 = createProject("xfer-drop-owner@example.com", "XferDrop2");
    JsonNode boardA = createBoard(alice, p1);
    JsonNode boardB = createBoard(alice, p2);
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();

    createCard(alice, boardA.get("id").asLong(), colA, "Dep", null); // P1 Nummer 1
    JsonNode card =
        createCard(alice, boardA.get("id").asLong(), colA, "Wanderer", "[1]"); // P1 Nr. 2
    long cardId = card.get("id").asLong();

    // Ins leere Projekt P2: neue projektweite Nummer (1); projekt-lokale Abhängigkeiten entfallen.
    mvc.perform(
            post("/api/cards/" + cardId + "/transfer")
                .cookie(alice)
                .contentType("application/json")
                .content("{\"targetBoardId\":%d,\"targetColumnId\":%d}".formatted(boardIdB, colB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value(1))
        .andExpect(jsonPath("$.dependencies.length()").value(0));
  }

  private void addMember(long projectId, String email, ProjectRole role) {
    memberships.save(new ProjectMembership(null, projectId, userId(email), role, Instant.now()));
  }

  private String transferBody(long boardId, long columnId) {
    return "{\"targetBoardId\":%d,\"targetColumnId\":%d}".formatted(boardId, columnId);
  }

  private String bulkTransferBody(long cardId, long boardId, long columnId) {
    return "{\"cardIds\":[%d],\"targetBoardId\":%d,\"targetColumnId\":%d}"
        .formatted(cardId, boardId, columnId);
  }

  @Test
  void transferWithinProjectAllowedForMemberWithCardMove() throws Exception {
    Cookie alice = loginAs("xfer-move-owner@example.com");
    Cookie bob = loginAs("xfer-move-member@example.com");
    long projectId = createProject("xfer-move-owner@example.com", "XferMove");
    JsonNode boardA = createBoard(alice, projectId);
    JsonNode boardB = createBoard(alice, projectId);
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long cardId =
        createCard(alice, boardA.get("id").asLong(), colA, "Wanderkarte", null).get("id").asLong();
    // MEMBER trägt CARD_MOVE, ist aber nicht Eigentümer.
    addMember(projectId, "xfer-move-member@example.com", ProjectRole.MEMBER);

    mvc.perform(
            post("/api/cards/" + cardId + "/transfer")
                .cookie(bob)
                .contentType("application/json")
                .content(transferBody(boardIdB, colB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.boardId").value((int) boardIdB))
        .andExpect(jsonPath("$.columnId").value((int) colB))
        // Regression zu #386: selbes Projekt → Nummer bleibt erhalten.
        .andExpect(jsonPath("$.number").value(1));
  }

  @Test
  void transferWithinProjectGuardsMissingCardMoveAndNonMembers() throws Exception {
    Cookie alice = loginAs("xfer-guard-owner@example.com");
    Cookie viewer = loginAs("xfer-guard-viewer@example.com");
    Cookie stranger = loginAs("xfer-guard-stranger@example.com");
    long projectId = createProject("xfer-guard-owner@example.com", "XferGuard");
    JsonNode boardA = createBoard(alice, projectId);
    JsonNode boardB = createBoard(alice, projectId);
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long cardId =
        createCard(alice, boardA.get("id").asLong(), colA, "Karte", null).get("id").asLong();
    // VIEWER ist Mitglied, hat aber kein CARD_MOVE.
    addMember(projectId, "xfer-guard-viewer@example.com", ProjectRole.VIEWER);

    mvc.perform(
            post("/api/cards/" + cardId + "/transfer")
                .cookie(viewer)
                .contentType("application/json")
                .content(transferBody(boardIdB, colB)))
        .andExpect(status().isForbidden());

    // Nichtmitglied: 404 statt 403 — kein Existenz-Leak.
    mvc.perform(
            post("/api/cards/" + cardId + "/transfer")
                .cookie(stranger)
                .contentType("application/json")
                .content(transferBody(boardIdB, colB)))
        .andExpect(status().isNotFound());
  }

  @Test
  void transferAcrossProjectsStillRequiresOwnerDespiteCardMove() throws Exception {
    Cookie alice = loginAs("xfer-cross-owner@example.com");
    Cookie bob = loginAs("xfer-cross-member@example.com");
    long p1 = createProject("xfer-cross-owner@example.com", "XferCross1");
    long p2 = createProject("xfer-cross-owner@example.com", "XferCross2");
    JsonNode boardA = createBoard(alice, p1);
    JsonNode boardB = createBoard(alice, p2);
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long cardId =
        createCard(alice, boardA.get("id").asLong(), colA, "Karte", null).get("id").asLong();
    // MEMBER in BEIDEN Projekten — CARD_MOVE reicht über Projektgrenzen weiterhin nicht.
    addMember(p1, "xfer-cross-member@example.com", ProjectRole.MEMBER);
    addMember(p2, "xfer-cross-member@example.com", ProjectRole.MEMBER);

    mvc.perform(
            post("/api/cards/" + cardId + "/transfer")
                .cookie(bob)
                .contentType("application/json")
                .content(transferBody(boardIdB, colB)))
        .andExpect(status().isForbidden());
  }

  @Test
  void bulkTransferWithinProjectAllowedForMemberWithCardMove() throws Exception {
    Cookie alice = loginAs("bulk-move-owner@example.com");
    Cookie bob = loginAs("bulk-move-member@example.com");
    long projectId = createProject("bulk-move-owner@example.com", "BulkMove");
    JsonNode boardA = createBoard(alice, projectId);
    JsonNode boardB = createBoard(alice, projectId);
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long cardId =
        createCard(alice, boardA.get("id").asLong(), colA, "Karte", null).get("id").asLong();
    addMember(projectId, "bulk-move-member@example.com", ProjectRole.MEMBER);

    mvc.perform(
            post("/api/cards/bulk-transfer")
                .cookie(bob)
                .contentType("application/json")
                .content(bulkTransferBody(cardId, boardIdB, colB)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].boardId").value((int) boardIdB))
        .andExpect(jsonPath("$[0].number").value(1));
  }

  @Test
  void bulkTransferWithinProjectGuardsMissingCardMoveAndNonMembers() throws Exception {
    Cookie alice = loginAs("bulk-guard-owner@example.com");
    Cookie viewer = loginAs("bulk-guard-viewer@example.com");
    Cookie stranger = loginAs("bulk-guard-stranger@example.com");
    long projectId = createProject("bulk-guard-owner@example.com", "BulkGuard");
    JsonNode boardA = createBoard(alice, projectId);
    JsonNode boardB = createBoard(alice, projectId);
    long boardIdA = boardA.get("id").asLong();
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long cardId = createCard(alice, boardIdA, colA, "Karte", null).get("id").asLong();
    addMember(projectId, "bulk-guard-viewer@example.com", ProjectRole.VIEWER);

    mvc.perform(
            post("/api/cards/bulk-transfer")
                .cookie(viewer)
                .contentType("application/json")
                .content(bulkTransferBody(cardId, boardIdB, colB)))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/api/cards/bulk-transfer")
                .cookie(stranger)
                .contentType("application/json")
                .content(bulkTransferBody(cardId, boardIdB, colB)))
        .andExpect(status().isNotFound());

    // Rollback: die Karte liegt weiterhin im Quellboard.
    mvc.perform(get("/api/boards/" + boardIdA + "/cards").cookie(alice))
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void bulkTransferAcrossProjectsStillRequiresOwnerDespiteCardMove() throws Exception {
    Cookie alice = loginAs("bulk-cross-owner@example.com");
    Cookie bob = loginAs("bulk-cross-member@example.com");
    long p1 = createProject("bulk-cross-owner@example.com", "BulkCross1");
    long p2 = createProject("bulk-cross-owner@example.com", "BulkCross2");
    JsonNode boardA = createBoard(alice, p1);
    JsonNode boardB = createBoard(alice, p2);
    long colA = boardA.get("columns").get(0).get("id").asLong();
    long boardIdB = boardB.get("id").asLong();
    long colB = boardB.get("columns").get(0).get("id").asLong();
    long cardId =
        createCard(alice, boardA.get("id").asLong(), colA, "Karte", null).get("id").asLong();
    addMember(p1, "bulk-cross-member@example.com", ProjectRole.MEMBER);
    addMember(p2, "bulk-cross-member@example.com", ProjectRole.MEMBER);

    mvc.perform(
            post("/api/cards/bulk-transfer")
                .cookie(bob)
                .contentType("application/json")
                .content(bulkTransferBody(cardId, boardIdB, colB)))
        .andExpect(status().isForbidden());
  }
}
