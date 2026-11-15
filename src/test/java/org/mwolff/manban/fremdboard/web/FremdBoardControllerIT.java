package org.mwolff.manban.fremdboard.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.FremdBoardQuelle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code GET/PUT /api/projects/{id}/fremdboard} (Issue #2166, Plan #2152 A2, A13, A18): Nur der
 * Owner richtet ein und liest; der Zugang steht in keiner Antwort, weder im Klartext noch
 * verschlüsselt. GitLab selbst ist nachgestellt — die Quelle antwortet auf den Probeabruf mit einer
 * leeren Liste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "manban.fremdboard.secret-key="
          + "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
      "manban.fremdboard.hosts=gitlab.example.org"
    })
class FremdBoardControllerIT extends AbstractIntegrationTest {

  private static final String OWNER = "owner-fremdboard@example.com";
  private static final String ADMIN = "admin-fremdboard@example.com";
  private static final String MITGLIED = "mitglied-fremdboard@example.com";
  private static final String ZUGANG = "glpat-sehr-geheim-0123456789";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private FremdBoardQuelle quelle;

  private Cookie owner;
  private long projekt;
  private long board;

  @BeforeEach
  void aufbau() throws Exception {
    when(quelle.art()).thenReturn(FremdBoardArt.GITLAB);
    when(quelle.geaenderteKarten(any(), any())).thenReturn(List.of());
    Cookie admin = anmeldung.anmelden(ADMIN, PlatformRole.ADMIN);
    anmeldung.konto(OWNER, PlatformRole.USER);
    projekt =
        lies(mvc.perform(
                    post("/api/projects")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fremd\",\"ownerEmail\":\"" + OWNER + "\"}"))
                .andExpect(status().isCreated()))
            .get("id")
            .asLong();
    owner = anmeldung.anmelden(OWNER, PlatformRole.USER);
    board = jdbc.queryForObject("SELECT id FROM board WHERE project_id = ?", Long.class, projekt);
  }

  private ResultActions speichern(Cookie wer, String zugang) throws Exception {
    return mvc.perform(
        put("/api/projects/" + projekt + "/fremdboard")
            .cookie(wer)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"art":"GITLAB","basisUrl":"https://gitlab.example.org","projektPfad":"gruppe/projekt",
                 "zugang":"%s","kitKonto":"kit-bot"}"""
                    .formatted(zugang)));
  }

  private JsonNode lies(ResultActions antwort) throws Exception {
    return json.readTree(
        antwort.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  private static String text(ResultActions antwort) throws Exception {
    return antwort.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private String verschluesselt() {
    return jdbc.queryForObject(
        "SELECT zugang_verschluesselt FROM fremdboard_verbindung WHERE project_id = ?",
        String.class,
        projekt);
  }

  private Cookie mitRolle(String email, String rolle) {
    Cookie session = anmeldung.anmelden(email, PlatformRole.USER);
    long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, email);
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role) VALUES (?, ?, ?)",
        projekt,
        userId,
        rolle);
    return session;
  }

  @Test
  void ownerBindetAnUndDerZugangStehtInKeinerAntwort() throws Exception {
    // When
    ResultActions angebunden =
        speichern(owner, ZUGANG)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.angebunden").value(true))
            .andExpect(jsonPath("$.art").value("GITLAB"))
            .andExpect(jsonPath("$.basisUrl").value("https://gitlab.example.org"))
            .andExpect(jsonPath("$.projektPfad").value("gruppe/projekt"))
            .andExpect(jsonPath("$.kitKonto").value("kit-bot"))
            .andExpect(jsonPath("$.statuslabels.inProgress").value("In progress"))
            .andExpect(jsonPath("$.zugangLesbar").value(true))
            .andExpect(jsonPath("$.zugang").doesNotExist());
    ResultActions gelesen =
        mvc.perform(get("/api/projects/" + projekt + "/fremdboard").cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.angebunden").value(true))
            .andExpect(jsonPath("$.letzterAbgleichAm").isEmpty())
            .andExpect(jsonPath("$.letzterFehler").isEmpty());
    ResultActions neuHinterlegt = speichern(owner, ZUGANG + "-neu").andExpect(status().isOk());

    // Then: Das Spiegel-Board ist das Board aus der Projektanlage, und der Zugang steht nirgends.
    assertThat(
            jdbc.queryForObject(
                "SELECT board_id FROM fremdboard_verbindung WHERE project_id = ?",
                Long.class,
                projekt))
        .isEqualTo(board);
    String gespeichert = verschluesselt();
    assertThat(gespeichert).doesNotContain(ZUGANG);
    for (ResultActions antwort : List.of(angebunden, gelesen, neuHinterlegt)) {
      assertThat(text(antwort)).doesNotContain(ZUGANG).doesNotContain(gespeichert);
    }
  }

  @Test
  void einNichtOwnerBekommt403() throws Exception {
    for (Cookie andere :
        List.of(mitRolle(MITGLIED, "ADMIN"), mitRolle("m2" + MITGLIED, "MEMBER"))) {
      mvc.perform(get("/api/projects/" + projekt + "/fremdboard").cookie(andere))
          .andExpect(status().isForbidden());
      speichern(andere, ZUGANG).andExpect(status().isForbidden());
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM fremdboard_verbindung WHERE project_id = ?",
                Integer.class,
                projekt))
        .isZero();
  }

  @Test
  void projektMitKartenWirdMit409Abgelehnt() throws Exception {
    // Given
    long spalte =
        jdbc.queryForObject(
            "SELECT id FROM board_column WHERE board_id = ? ORDER BY position LIMIT 1",
            Long.class,
            board);
    mvc.perform(
            post("/api/boards/" + board + "/cards")
                .cookie(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"columnId\":" + spalte + ",\"title\":\"Schon da\"}"))
        .andExpect(status().isCreated());

    // When / Then
    speichern(owner, ZUGANG)
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1 auf Boards")));
  }

  @Test
  void derGebundeneZugangDesKitsZeigtAufDasSpiegelBoard() throws Exception {
    // Given: ein an das Board gebundener Zugang des Kits, angelegt vor dem Anbinden.
    String kit =
        lies(mvc.perform(
                    post("/api/access-tokens")
                        .cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"name\":\"Kit\",\"projectId\":%d,\"boardId\":%d}"
                                .formatted(projekt, board)))
                .andExpect(status().isCreated()))
            .get("plaintext")
            .asText();

    // When
    speichern(owner, ZUGANG).andExpect(status().isOk());

    // Then: Der Zugang liest weiter das Board — jetzt das Spiegel-Board.
    mvc.perform(get("/api/kanban/labels").header("X-Kanban-Token", kit)).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT t.board_id FROM kanban_access_token t WHERE t.name = 'Kit'", Long.class))
        .isEqualTo(
            jdbc.queryForObject(
                "SELECT board_id FROM fremdboard_verbindung WHERE project_id = ?",
                Long.class,
                projekt));
  }
}
