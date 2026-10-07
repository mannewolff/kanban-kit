package org.mwolff.manban.nightrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Ein Nachtlauf, der nicht angelaufen ist, über den ganzen Weg (Issue #1500, Plan #1498, fachlich
 * #1493): Einlieferung per Token mit {@code abortReason} und {@code abortKind} bis in die Laufliste
 * des Projekts und den Plattform-Leitstand.
 *
 * <p><b>Eigene Klasse statt eines Anbaus an {@link NightRunIngestIT}</b>, aus demselben Grund wie
 * {@link NachtlaufOhneArbeitIngestIT}: Jene Klasse steht auf der Methodengrenze von PMD.
 *
 * <p>Geprüft wird das <b>Drahtformat</b> und der Rundlauf durch die Datenbank. Wann ein Lauf nicht
 * angelaufen ist, entscheidet {@code NightRunOutcome} und steht in dessen Test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NachtlaufNichtAngelaufenIngestIT extends AbstractIntegrationTest {

  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String INGEST_PFAD = "/api/kanban/night-runs";
  private static final String LEITSTAND_PFAD = "/api/admin/leitstand";
  private static final String PASSWORD = "Passwort-123";
  private static final String GRUND =
      "Working Tree ist nicht sauber. Bitte committen oder aufraeumen, dann neu starten.";
  private static final String OHNE_PAKET = "";
  private static final String ROTES_PAKET =
      "{\"cardNumber\":917,\"title\":\"Paket 917\",\"state\":\"RED\","
          + "\"errorClass\":\"CHECKS_RED\"}";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;

  /**
   * Eine abgeschlossene Meldung mit Abbruchgrund. Das Feld {@code abortKind} steht nur dann im
   * Rumpf, wenn eine Art übergeben wird — so belegt derselbe Bauer die Meldung einer älteren
   * Kit-Kopie ohne das Feld (A4).
   *
   * <p>Der Start ist jetzt: Der Leitstand zeigt unter den durchgeführten nur die Läufe der
   * laufenden Nacht.
   */
  private static String abgebrochen(String art, String paket) {
    String feld = art.isEmpty() ? "" : "\"abortKind\":\"%s\",".formatted(art);
    return """
        {"startedAt":"%s","mode":"CHAIN","durationMs":12000,"processedCount":%d,
         "skippedCount":0,"unparsedCount":0,"complete":true,"abortReason":"%s",%s
         "items":[%s]}"""
        .formatted(
            Instant.now().truncatedTo(ChronoUnit.SECONDS),
            paket.isEmpty() ? 0 : 1,
            GRUND,
            feld,
            paket);
  }

  /**
   * AK 1 bis 3 der fachlichen Quelle #1493: Ein selbst gemeldeter Abbruch ohne Paket ist in der
   * Laufliste „nicht angelaufen" mit dem gemeldeten Grund — und auf dem Plattform-Leitstand steht
   * er unter den durchgeführten Läufen, aber nicht unter den Störungen.
   */
  @Test
  void einSelbstGemeldeterAbbruchOhnePaketIstNichtAngelaufen_undKeineStoerung() throws Exception {
    Aufbau aufbau = aufbau("nichtangelaufen");

    melde(aufbau, abgebrochen("REPORTED", OHNE_PAKET));

    JsonNode lauf = ersterLauf(aufbau);
    assertThat(lauf.get("outcome").get("verdict").asText()).isEqualTo("NOT_STARTED");
    assertThat(lauf.get("outcome").get("abortReason").asText()).isEqualTo(GRUND);
    assertThat(lauf.has("abortKind"))
        .as("die Art wirkt nur über den Ausgang, die Sicht führt sie nicht (E10)")
        .isFalse();

    JsonNode leitstand = leitstand(aufbau);
    assertThat(leitstand.get("stoerungen")).as("keine Störung (AK 3)").isEmpty();
    assertThat(leitstand.get("durchgefuehrte")).hasSize(1);
    assertThat(leitstand.get("durchgefuehrte").get(0).get("outcome").get("verdict").asText())
        .isEqualTo("NOT_STARTED");
  }

  /** AK 5: Hat der Lauf ein Paket angefasst, bleibt der Abbruch gescheitert und eine Störung. */
  @Test
  void einSelbstGemeldeterAbbruchMitRotemPaketBleibtEineStoerung() throws Exception {
    Aufbau aufbau = aufbau("abbruchmitpaket");

    melde(aufbau, abgebrochen("REPORTED", ROTES_PAKET));

    assertGescheitertUndGestoert(aufbau);
  }

  /** AK 6: Den Lauf, den der Wächter abschloss, hat niemand gesehen — er bleibt eine Störung. */
  @Test
  void einVomWaechterAbgeschlossenerLaufBleibtEineStoerung() throws Exception {
    Aufbau aufbau = aufbau("verstummt");

    melde(aufbau, abgebrochen("SILENCED", OHNE_PAKET));

    assertGescheitertUndGestoert(aufbau);
  }

  /** A4: Eine ältere Kit-Kopie meldet keine Art — ihr Abbruch bleibt wie bisher eine Störung. */
  @Test
  void einAbbruchOhneArtBleibtEineStoerung() throws Exception {
    Aufbau aufbau = aufbau("ohneart");

    melde(aufbau, abgebrochen(OHNE_PAKET, OHNE_PAKET));

    assertGescheitertUndGestoert(aufbau);
  }

  private void assertGescheitertUndGestoert(Aufbau aufbau) throws Exception {
    assertThat(ersterLauf(aufbau).get("outcome").get("verdict").asText()).isEqualTo("FAILED");
    JsonNode stoerungen = leitstand(aufbau).get("stoerungen");
    assertThat(stoerungen).hasSize(1);
    assertThat(stoerungen.get(0).get("outcome").get("verdict").asText()).isEqualTo("FAILED");
  }

  private void melde(Aufbau aufbau, String rumpf) throws Exception {
    mvc.perform(
            post(INGEST_PFAD)
                .header(TOKEN_HEADER, aufbau.token())
                .contentType("application/json")
                .content(rumpf))
        .andExpect(status().isOk());
  }

  /** Der erste — hier einzige — Lauf der Laufliste des Projekts. */
  private JsonNode ersterLauf(Aufbau aufbau) throws Exception {
    return lies(get("/api/projects/" + aufbau.projectId() + "/night-runs"), aufbau).get(0);
  }

  private JsonNode leitstand(Aufbau aufbau) throws Exception {
    return lies(get(LEITSTAND_PFAD).param("zone", "UTC"), aufbau);
  }

  private JsonNode lies(MockHttpServletRequestBuilder anfrage, Aufbau aufbau) throws Exception {
    return json.readTree(
        mvc.perform(anfrage.cookie(aufbau.session()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private record Aufbau(Cookie session, long projectId, String token) {}

  /**
   * Ein Projekt mit Board und Token, dessen Besitzer Plattform-Admin ist; das Projekt nimmt am
   * Plattform-Leitstand teil.
   */
  private Aufbau aufbau(String kennung) throws Exception {
    Cookie session = session(kennung + "@example.com");
    long projectId = createProject(session, kennung + "-Projekt", kennung + "@example.com");
    long boardId = createBoard(session, projectId);
    mvc.perform(
            put("/api/projects/" + projectId + "/dashboard-participation")
                .cookie(session)
                .contentType("application/json")
                .content("{\"participating\":true}"))
        .andExpect(status().isOk());
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
