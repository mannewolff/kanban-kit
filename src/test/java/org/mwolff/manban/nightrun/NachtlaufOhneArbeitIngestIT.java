package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Der Grund, warum ein Nachtlauf nichts abgearbeitet hat, ueber den ganzen Weg (Issue #1068, Plan
 * #1067, fachlich #1060): Einlieferung per Token bis in die Antwort der Laufliste.
 *
 * <p><b>Eigene Klasse statt eines Anbaus an {@link NightRunIngestIT}</b>: Jene Klasse steht mit
 * ihren Faellen genau auf der projektkalibrierten Methodengrenze (PMD {@code TooManyMethods}, 30).
 * Zwei Tests mehr rissen das Tor, und die Schwelle zu heben oder die Regel zu unterdruecken hiesse,
 * ein Signal abzuschalten statt ihm zu folgen. Der Preis ist das wiederholte Aufbau-Geruest
 * darunter; es ist die kleinere Schuld.
 *
 * <p>Geprueft wird hier das <b>Drahtformat</b> und der Rundlauf durch die Datenbank. Wann
 * ueberhaupt ein Grund entsteht, ist Sache der Regel und steht in {@code NightRunServiceTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NachtlaufOhneArbeitIngestIT extends AbstractIntegrationTest {

  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String INGEST_PFAD = "/api/kanban/night-runs";
  private static final String START = "2026-09-16T22:31:00Z";
  private static final String PASSWORD = "Passwort-123";
  private static final String RUECKFALL = "Nichts abgearbeitet — Grund unbekannt";
  private static final String GEMELDET = "Kein Eintrag trug das Label kit:nightrun";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  /**
   * Ein abgeschlossen gemeldeter Nachtlauf <b>ohne</b> Arbeitspakete. Das Feld steht nur dann im
   * Rumpf, wenn ein Grund uebergeben wird — so belegt derselbe Bauer beide Faelle aus E3: die
   * Meldung einer aelteren Kit-Kopie ohne das Feld und die einer neuen mit.
   */
  private static String ohneArbeit(String grund) {
    String feld = grund.isEmpty() ? "" : "\"noWorkReason\":\"%s\",".formatted(grund);
    return """
        {"startedAt":"%s","mode":"CHAIN","durationMs":4320000,"processedCount":0,
         "skippedCount":0,"unparsedCount":0,"complete":true,%s
         "items":[]}"""
        .formatted(START, feld);
  }

  private static String mitArbeit() {
    return """
        {"startedAt":"%s","mode":"CHAIN","durationMs":4320000,"processedCount":1,
         "skippedCount":0,"unparsedCount":0,"complete":true,
         "items":[{"cardNumber":917,"title":"Paket 917","state":"GREEN"}]}"""
        .formatted(START);
  }

  /**
   * Der ganze Weg in einem Fall, weil alle Schritte denselben Lauf betreffen: Eine Meldung ohne das
   * Feld kommt an (additiv, E3) und bekommt den Rueckfalltext; die Zweitmeldung mit Grund ersetzt
   * denselben Lauf und traegt ihn woertlich; eine zu lange Meldung wird abgewiesen, ohne den Stand
   * anzutasten. Am Ende steht der Wert in der Antwort der Laufliste — dort liest ihn die Anzeige.
   *
   * <p>Dieselbe Antwort traegt seit Issue #1185 den Ausgang {@code NO_WORK} — fuer den
   * Rueckfalltext wie fuer den gemeldeten Grund. Er steht hier neben dem Text, weil erst beides
   * zusammen die Zusage ist: gespeichert wird woertlich, gelesen wird als ruhiger Lauf.
   */
  @Test
  void derGrundKommtDurchBisInDieLaufliste_undDieLaengengrenzeGilt() throws Exception {
    Aufbau aufbau = aufbau("ohnearbeit-ingest");

    melde(aufbau, ohneArbeit("")).andExpect(jsonPath("$.outcome").value("CREATED"));
    JsonNode rueckfall = ersterLauf(aufbau);
    assertThat(rueckfall.get("noWorkReason").asText())
        .as("ohne gemeldetes Feld setzt der Server den Rueckfalltext (AK 2)")
        .isEqualTo(RUECKFALL);
    assertThat(rueckfall.get("outcome").get("verdict").asText())
        .as("und die Sicht liest ihn als ruhigen Lauf, nicht als Stoerung (Issue #1185)")
        .isEqualTo("NO_WORK");

    melde(aufbau, ohneArbeit(GEMELDET)).andExpect(jsonPath("$.outcome").value("REPLACED"));
    JsonNode gemeldet = ersterLauf(aufbau);
    assertThat(gemeldet.get("noWorkReason").asText())
        .as("der gemeldete Grund steht woertlich am ersetzten Lauf")
        .isEqualTo(GEMELDET);
    assertThat(gemeldet.get("outcome").get("verdict").asText())
        .as("derselbe Ausgang wie beim Rueckfall — der Wortlaut entscheidet nicht mehr")
        .isEqualTo("NO_WORK");

    melde(aufbau, ohneArbeit("x".repeat(301))).andExpect(status().isBadRequest());
    assertThat(grundInDerLaufliste(aufbau))
        .as("die abgewiesene Meldung hat den Stand nicht angetastet")
        .isEqualTo(GEMELDET);
  }

  /** Die Gegenprobe: Ein Lauf mit Arbeit traegt keinen Grund — auch nicht einen leeren Text. */
  @Test
  void einLaufMitArbeitTraegtInDerLaufsListeKeinenGrund() throws Exception {
    Aufbau aufbau = aufbau("mitarbeit-ingest");

    melde(aufbau, mitArbeit()).andExpect(status().isOk());

    mvc.perform(get("/api/projects/" + aufbau.projectId() + "/night-runs").cookie(aufbau.session()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].processedCount").value(1))
        .andExpect(jsonPath("$[0].noWorkReason").value(nullValue()));
  }

  private ResultActions melde(Aufbau aufbau, String rumpf) throws Exception {
    return mvc.perform(
        post(INGEST_PFAD)
            .header(TOKEN_HEADER, aufbau.token())
            .contentType("application/json")
            .content(rumpf));
  }

  private String grundInDerLaufliste(Aufbau aufbau) throws Exception {
    return ersterLauf(aufbau).get("noWorkReason").asText();
  }

  /** Der erste — hier einzige — Lauf der Laufliste, mit Grund und Befund in einer Antwort. */
  private JsonNode ersterLauf(Aufbau aufbau) throws Exception {
    JsonNode liste =
        json.readTree(
            mvc.perform(
                    get("/api/projects/" + aufbau.projectId() + "/night-runs")
                        .cookie(aufbau.session()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return liste.get(0);
  }

  private record Aufbau(Cookie session, long projectId, String token) {}

  private Aufbau aufbau(String kennung) throws Exception {
    Cookie session = session(kennung + "@example.com");
    long projectId = createProject(session, kennung + "-Projekt", kennung + "@example.com");
    long boardId = createBoard(session, projectId);
    JsonNode angelegt =
        json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(session)
                        .contentType("application/json")
                        .content(
                            "{\"name\":\"%s-Token\",\"projectId\":%d,\"boardId\":%d}"
                                .formatted(kennung, projectId, boardId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    return new Aufbau(session, projectId, angelegt.get("plaintext").asText());
  }

  private long createProject(Cookie admin, String name, String ownerEmail) throws Exception {
    return id(
        mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType("application/json")
                    .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private long createBoard(Cookie session, long projectId) throws Exception {
    return id(
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"Board\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private long id(String antwort) throws Exception {
    return json.readTree(antwort).get("id").asLong();
  }

  private Cookie session(String email) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "P", true, PlatformRole.ADMIN));
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
