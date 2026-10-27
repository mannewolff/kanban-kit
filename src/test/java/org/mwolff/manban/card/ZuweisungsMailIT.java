package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.PayloadFields;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Nachricht an neu Zuständige über die echten Schreibwege (Issue #1916): Session und Token reihen
 * je neuem Zuständigen genau einen Outbox-Eintrag ein, die Selbstzuweisung keinen, und eine
 * gescheiterte Zuweisung (kein Projektmitglied) ebenfalls keinen — die Vormerkung teilt die
 * Transaktion der Zuweisung.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ZuweisungsMailIT extends AbstractIntegrationTest {

  private static final String TYP = "mail.card-assigned";
  private static final String OWNER = "zuweisung-owner@example.com";
  private static final String BO = "zuweisung-bo@example.com";
  private static final String CARLA = "zuweisung-carla@example.com";
  private static final String FREMD = "zuweisung-fremd@example.com";

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private AppUserRepository users;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;

  private Cookie owner;
  private long ownerId;
  private long boId;
  private long carlaId;
  private long projectId;
  private long boardId;
  private long columnId;

  @BeforeEach
  void projektMitDreiMitgliedern() throws Exception {
    ownerId = users.save(new AppUser(null, OWNER, "Manne", true, PlatformRole.USER)).requireId();
    owner = anmeldung.anmelden(OWNER, PlatformRole.USER);
    Cookie admin = anmeldung.anmelden("zuweisung-admin@example.com", PlatformRole.ADMIN);
    boId = anmeldung.konto(BO, PlatformRole.USER).requireId();
    carlaId = anmeldung.konto(CARLA, PlatformRole.USER).requireId();
    anmeldung.konto(FREMD, PlatformRole.USER);
    projectId =
        lies(post("/api/projects")
                .cookie(admin)
                .contentType("application/json")
                .content("{\"name\":\"Zweierprojekt\",\"ownerEmail\":\"%s\"}".formatted(OWNER)))
            .get("id")
            .asLong();
    for (String email : List.of(BO, CARLA)) {
      mvc.perform(
              post("/api/projects/" + projectId + "/invitations")
                  .cookie(owner)
                  .contentType("application/json")
                  .content("{\"email\":\"%s\",\"role\":\"MEMBER\"}".formatted(email)))
          .andExpect(status().isAccepted());
    }
    JsonNode board =
        lies(
            post("/api/projects/" + projectId + "/boards")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"name\":\"Board B\"}"));
    boardId = board.get("id").asLong();
    columnId = board.get("columns").get(0).get("id").asLong();
  }

  @Test
  void sessionReihtJeNeuemZustaendigenGenauEinenEintragEin() throws Exception {
    JsonNode karte = karte("Session-Karte");

    zuweisen(karte, status().isOk(), boId, carlaId);

    List<List<String>> eintraege = eintraege();
    assertThat(eintraege).extracting(f -> f.get(0)).containsExactlyInAnyOrder(BO, CARLA);
    assertThat(eintraege.get(0))
        .containsSequence(
            karte.get("number").asText(), "Session-Karte", "Zweierprojekt", "Board B", "Manne")
        .last()
        .asString()
        .endsWith("/projects/" + projectId + "/karten/" + karte.get("number").asText());

    // Dieselbe Liste noch einmal: niemand kommt hinzu, keine weitere Nachricht.
    zuweisen(karte, status().isOk(), boId, carlaId);
    assertThat(eintraege()).hasSize(2);

    // Entfernen schickt nichts; wer danach wieder zugewiesen wird, bekommt eine neue Nachricht.
    zuweisen(karte, status().isOk(), carlaId);
    assertThat(eintraege()).hasSize(2);
    zuweisen(karte, status().isOk(), boId, carlaId);
    assertThat(eintraege()).hasSize(3);
  }

  @Test
  void tokenReihtEinenEintragImNamenSeinesInhabersEin() throws Exception {
    JsonNode karte = karte("Token-Karte");
    String token =
        lies(post("/api/access-tokens")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"name\":\"Skript\"}"))
            .get("plaintext")
            .asText();

    senden(
        put("/api/cards/" + karte.get("id").asLong() + "/assignees")
            .header("X-Kanban-Token", token),
        status().isOk(),
        boId);

    List<List<String>> eintraege = eintraege();
    assertThat(eintraege).hasSize(1);
    assertThat(eintraege.get(0).get(0)).isEqualTo(BO);
    assertThat(eintraege.get(0).get(5)).isEqualTo("Manne");
  }

  @Test
  void selbstzuweisungReihtKeinenEintragEin() throws Exception {
    JsonNode karte = karte("Eigene Karte");

    zuweisen(karte, status().isOk(), ownerId);

    assertThat(eintraege()).isEmpty();
  }

  @Test
  void gescheiterteZuweisungReihtKeinenEintragEin() throws Exception {
    JsonNode karte = karte("Fremde Karte");
    long fremdId = anmeldung.konto(FREMD, PlatformRole.USER).requireId();

    zuweisen(karte, status().is4xxClientError(), boId, fremdId);

    assertThat(eintraege()).isEmpty();
  }

  /** Zuweisung über die Session des Projekt-Owners. */
  private void zuweisen(JsonNode karte, ResultMatcher erwartet, Long... zustaendige)
      throws Exception {
    senden(
        put("/api/cards/" + karte.get("id").asLong() + "/assignees").cookie(owner),
        erwartet,
        zustaendige);
  }

  private void senden(
      MockHttpServletRequestBuilder request, ResultMatcher erwartet, Long... zustaendige)
      throws Exception {
    mvc.perform(
            request
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("assignees", zustaendige))))
        .andExpect(erwartet);
  }

  private JsonNode karte(String titel) throws Exception {
    return lies(
        post("/api/boards/" + boardId + "/cards")
            .cookie(owner)
            .contentType("application/json")
            .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, titel)));
  }

  /** Die vorgemerkten Zuweisungs-Nachrichten, je Eintrag die dekodierten Felder. */
  private List<List<String>> eintraege() {
    return jdbc
        .queryForList(
            "SELECT payload FROM outbox_entry WHERE event_type = ? ORDER BY id", String.class, TYP)
        .stream()
        .map(payload -> PayloadFields.split(payload, 7))
        .toList();
  }

  private JsonNode lies(MockHttpServletRequestBuilder request) throws Exception {
    return json.readTree(
        mvc.perform(request)
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
