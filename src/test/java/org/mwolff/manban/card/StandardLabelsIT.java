package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Standard-Labels auf allen Boards aller Projekte (Issue #1485), Ende zu Ende: aktive Boards
 * bekommen den Satz, archivierte nicht, vorhandene Labels bleiben, und jeder weitere Aufruf legt
 * nichts mehr an.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class StandardLabelsIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final int SATZ = 20;
  private static final String ADMIN = "standardlabels-admin@example.com";
  private static final String OWNER = "standardlabels-owner@example.com";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void derSatzNenntZwanzigLabelsMitGruppeUndFarbe() throws Exception {
    mvc.perform(get("/api/admin/standard-labels").cookie(login(ADMIN, PlatformRole.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(SATZ))
        .andExpect(jsonPath("$[0].name").value("kit:durchziehen"))
        .andExpect(jsonPath("$[0].gruppe").value("Kit"))
        .andExpect(jsonPath("$[0].farbe").value("#6a1b9a"))
        .andExpect(jsonPath("$[19].name").value("planreview:2"));
  }

  @Test
  void legtDenSatzAufAllenAktivenBoardsAllerProjekteAn_undIstIdempotent() throws Exception {
    Cookie admin = login(ADMIN, PlatformRole.ADMIN);
    Cookie owner = login(OWNER, PlatformRole.USER);
    long projektA = createProject(admin, "Projekt A");
    long projektB = createProject(admin, "Projekt B");
    long aktivA = createBoard(owner, projektA, "Aktiv A");
    long aktivB = createBoard(owner, projektB, "Aktiv B");
    long archiviertA = createBoard(owner, projektA, "Alt A");
    long archiviertB = createBoard(owner, projektB, "Alt B");
    mvc.perform(delete("/api/boards/" + archiviertA).cookie(owner))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/boards/" + archiviertB).cookie(owner))
        .andExpect(status().isNoContent());
    // Ein vorhandenes Label mit eigener Farbe bleibt, wie es ist.
    long vorhanden = createLabel(owner, aktivA, "kit:night", "#ff0000");

    // Jedes Projekt bringt sein Standard-Board mit (DefaultBoardCreator): vier aktive Boards.
    int aktive = 4;
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM board WHERE archived_at IS NULL", Integer.class))
        .isEqualTo(aktive);

    JsonNode erstes = anlegen(admin);

    assertThat(erstes.get("boards").asInt()).isEqualTo(aktive);
    assertThat(erstes.get("angelegt").asInt()).isEqualTo(aktive * SATZ - 1);
    assertThat(erstes.get("uebersprungen").asInt()).isEqualTo(1);
    assertThat(labelZahl(aktivA)).isEqualTo(SATZ);
    assertThat(labelZahl(aktivB)).isEqualTo(SATZ);
    assertThat(labelZahl(archiviertA)).isZero();
    assertThat(labelZahl(archiviertB)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT color FROM label WHERE id = ? AND name = 'kit:night'",
                String.class,
                vorhanden))
        .isEqualTo("#ff0000");

    for (int aufruf = 2; aufruf <= 10; aufruf++) {
      JsonNode weiteres = anlegen(admin);
      assertThat(weiteres.get("angelegt").asInt()).isZero();
      assertThat(weiteres.get("uebersprungen").asInt()).isEqualTo(aktive * SATZ);
    }
    assertThat(labelZahl(aktivA)).isEqualTo(SATZ);
    assertThat(labelZahl(aktivB)).isEqualTo(SATZ);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM label", Integer.class))
        .isEqualTo(aktive * SATZ);
  }

  @Test
  void einNichtAdminErhaelt403UndEsWirdNichtsAngelegt() throws Exception {
    Cookie admin = login(ADMIN, PlatformRole.ADMIN);
    Cookie owner = login(OWNER, PlatformRole.USER);
    long board = createBoard(owner, createProject(admin, "Projekt"), "Aktiv");

    mvc.perform(post("/api/admin/standard-labels/anlegen").cookie(owner))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/standard-labels").cookie(owner)).andExpect(status().isForbidden());

    assertThat(labelZahl(board)).isZero();
  }

  private JsonNode anlegen(Cookie admin) throws Exception {
    String body =
        mvc.perform(post("/api/admin/standard-labels/anlegen").cookie(admin))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private int labelZahl(long board) {
    Integer zahl =
        jdbc.queryForObject("SELECT count(*) FROM label WHERE board_id = ?", Integer.class, board);
    return zahl == null ? 0 : zahl;
  }

  private Cookie login(String email, PlatformRole rolle) throws Exception {
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

  private long createProject(Cookie admin, String name) throws Exception {
    String body =
        mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, OWNER)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private long createBoard(Cookie session, long projekt, String name) throws Exception {
    String body =
        mvc.perform(
                post("/api/projects/" + projekt + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private long createLabel(Cookie session, long board, String name, String farbe) throws Exception {
    String body =
        mvc.perform(
                post("/api/boards/" + board + "/labels")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"color\":\"%s\"}".formatted(name, farbe)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }
}
