package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardColumnRepository;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardArchiveService;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.card.application.LabelService;
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
 * Die Übersicht „Heute Nacht“ Ende zu Ende (Issue #1454, Plan #1447 E4): {@code GET
 * /api/projects/{projectId}/night-runs/tonight}.
 *
 * <p>Karten und Labels entstehen über die Dienste mit dem Plattform-Admin, der das Projekt anlegt —
 * ohne Sicherheitskontext gilt keine Herkunft {@code TOKEN}, und {@code kit:night} lässt sich
 * setzen wie im Board. Gelesen wird über HTTP mit Session, wie die Runner-Seite es tut.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NightRunHeuteNachtIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final String JSON = "application/json";
  private static final String NACHT = "kit:night";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private BoardService boards;
  @Autowired private BoardColumnRepository columns;
  @Autowired private CardService cards;
  @Autowired private CardArchiveService archiv;
  @Autowired private LabelService labels;

  @Test
  void derOwnerSiehtDieFreigegebenenKartenBeiderBoardsUndNichtsAnderes() throws Exception {
    Aufbau a = aufbau("heute");
    long zweitesBoard = board(a, "Betrieb");
    int ausErstem = karte(a, a.boardId(), "[Fachlich] Import", NACHT, "planreview:2");
    int ausZweitem = karte(a, zweitesBoard, "[Plan] Export", NACHT, "ziel:umsetzung");
    karte(a, a.boardId(), "[Fachlich] Ohne Freigabe", "ziel:plan");
    karte(a, a.boardId(), "Ohne Praefix", NACHT);
    int imPapierkorb = karte(a, a.boardId(), "[Fachlich] Geloescht", NACHT);
    archiv.delete(a.adminId(), cardId(a.projectId(), imPapierkorb));
    long archiviert = board(a, "Archiv");
    karte(a, archiviert, "[Fachlich] Archiviert", NACHT);
    boards.deleteBoard(a.adminId(), archiviert);
    Aufbau fremd = aufbau("heute-fremd");
    karte(fremd, fremd.boardId(), "[Fachlich] Fremd", NACHT);

    JsonNode antwort = heuteNacht(a.owner(), a.projectId(), 200);

    assertThat(zeilen(antwort))
        .containsExactly(
            ausErstem + " [Fachlich] Import Board FACHPLAN PAKETE 2",
            ausZweitem + " [Plan] Export Betrieb PLAN UMSETZUNG null");
  }

  @Test
  void ohneFreigabeIstDieListeLeer() throws Exception {
    Aufbau a = aufbau("heute-leer");
    karte(a, a.boardId(), "[Fachlich] Ohne Freigabe");

    assertThat(heuteNacht(a.owner(), a.projectId(), 200)).isEmpty();
  }

  @Test
  void einMemberBekommt403() throws Exception {
    Aufbau a = aufbau("heute-member");
    Cookie member = session("heute-member-m@example.com", PlatformRole.USER);
    long memberId = users.findByEmail("heute-member-m@example.com").orElseThrow().requireId();
    memberships.save(
        new ProjectMembership(null, a.projectId(), memberId, ProjectRole.MEMBER, Instant.now()));

    heuteNacht(member, a.projectId(), 403);
  }

  @Test
  void derPlattformAdminLiestNurBeiTeilnahme() throws Exception {
    Aufbau a = aufbau("heute-admin");
    karte(a, a.boardId(), "[Plan] Teilnahme", NACHT);

    teilnahme(a.projectId(), false);
    heuteNacht(a.admin(), a.projectId(), 403);
    teilnahme(a.projectId(), true);
    assertThat(heuteNacht(a.admin(), a.projectId(), 200)).hasSize(1);
  }

  @Test
  void einNutzerOhneProjektzugehoerigkeitBekommt404() throws Exception {
    Aufbau a = aufbau("heute-aussen");
    Cookie fremd = session("heute-aussen-x@example.com", PlatformRole.USER);

    heuteNacht(fremd, a.projectId(), 404);
  }

  // --- Aufbau ---------------------------------------------------------------------------------

  private record Aufbau(Cookie owner, Cookie admin, long adminId, long projectId, long boardId) {}

  private Aufbau aufbau(String kennung) throws Exception {
    String ownerEmail = kennung + "-owner@example.com";
    String adminEmail = kennung + "-admin@example.com";
    Cookie owner = session(ownerEmail, PlatformRole.USER);
    Cookie admin = session(adminEmail, PlatformRole.ADMIN);
    long adminId = users.findByEmail(adminEmail).orElseThrow().requireId();
    long projectId =
        json.readTree(
                mvc.perform(
                        post("/api/projects")
                            .cookie(admin)
                            .contentType(JSON)
                            .content(
                                "{\"name\":\"%s\",\"ownerEmail\":\"%s\"}"
                                    .formatted(kennung, ownerEmail)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    Aufbau ohneBoard = new Aufbau(owner, admin, adminId, projectId, 0L);
    return new Aufbau(owner, admin, adminId, projectId, board(ohneBoard, "Board"));
  }

  private long board(Aufbau a, String name) {
    long boardId = boards.createBoard(a.adminId(), a.projectId(), name).id();
    for (String label : List.of(NACHT, "ziel:plan", "ziel:umsetzung", "planreview:2")) {
      labels.create(a.adminId(), boardId, label, "#000000");
    }
    return boardId;
  }

  /** Legt eine Karte mit den genannten Labels an und liefert ihre Nummer. */
  private int karte(Aufbau a, long boardId, String titel, String... labelNamen) {
    long columnId = columns.findByBoardId(boardId).getFirst().requireId();
    var karte = cards.create(a.adminId(), boardId, columnId, titel, null, null, null);
    for (String name : labelNamen) {
      labels.addToCard(a.adminId(), karte.id(), name);
    }
    return karte.number();
  }

  private long cardId(long projectId, int number) {
    Long id =
        jdbc.queryForObject(
            "SELECT id FROM card WHERE project_id = ? AND number = ?",
            Long.class,
            projectId,
            number);
    return id == null ? 0L : id;
  }

  /** Direkt in der Spalte, wie in {@code NightRunTeilnahmeZugriffIT}. */
  private void teilnahme(long projectId, boolean teilnehmend) {
    jdbc.update(
        "UPDATE project SET dashboard_participation = ? WHERE id = ?", teilnehmend, projectId);
  }

  private JsonNode heuteNacht(Cookie wer, long projectId, int erwartet) throws Exception {
    String text =
        mvc.perform(get("/api/projects/" + projectId + "/night-runs/tonight").cookie(wer))
            .andExpect(status().is(erwartet))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return text.isEmpty() ? json.nullNode() : json.readTree(text);
  }

  private static List<String> zeilen(JsonNode antwort) {
    List<String> ergebnis = new ArrayList<>();
    antwort.forEach(
        k ->
            ergebnis.add(
                String.join(
                    " ",
                    k.get("number").asText(),
                    k.get("title").asText(),
                    k.get("boardName").asText(),
                    k.get("start").asText(),
                    k.get("ziel").asText(),
                    k.get("pruefer").isNull() ? "null" : k.get("pruefer").asText())));
    return ergebnis;
  }

  private Cookie session(String email, PlatformRole role) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, role));
    }
    return mvc.perform(
            post("/api/auth/login")
                .contentType(JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }
}
