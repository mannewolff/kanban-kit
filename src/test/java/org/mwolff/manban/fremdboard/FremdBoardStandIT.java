package org.mwolff.manban.fremdboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.fremdboard.application.VerbindungRepository;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Die knappe Auskunft {@code GET /api/projects/{id}/fremdboard/stand} für jedes Mitglied (Issue
 * #2223, Plan #2221 E1, E2, E3, fachliche Quelle #2203): genau vier Felder, für jede Projekt-Rolle
 * und den Plattform-Admin ohne Mitgliedschaft gleich; ein Nichtmitglied bekommt 404, gleich wo das
 * Board liegt. Die Zeiten des Abgleichs setzt der Test über den Port, GitLab wird nicht gerufen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class FremdBoardStandIT extends AbstractIntegrationTest {

  private static final String ADMIN = "admin-stand@example.com";
  private static final String OWNER = "owner-stand@example.com";
  private static final String PLATTFORM_ADMIN = "PLATTFORM_ADMIN";
  private static final String KIT_KONTO = "kit-bot-stand";
  private static final String ADRESSE = "https://gitlab.intern.example.org";
  private static final String PFAD = "gruppe/projekt-stand";
  private static final String FEHLER = "GitLab antwortete mit 503 Service Unavailable";

  /** Heute um 9:58 Uhr ist der letzte Abgleich gelungen. */
  private static final Instant GELUNGEN = Instant.parse("2026-11-23T09:58:00Z");

  /** Seit einer Stunde danach scheitert der Abgleich. */
  private static final Instant GESCHEITERT = GELUNGEN.plusSeconds(3600);

  private static final List<String> VIER_FELDER =
      List.of("imFremdsystem", "art", "standAm", "abgleichGescheitert");

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private VerbindungRepository verbindungen;

  private Cookie admin;

  @BeforeEach
  void aufbau() {
    admin = anmeldung.anmelden(ADMIN, PlatformRole.ADMIN);
    anmeldung.konto(OWNER, PlatformRole.USER);
  }

  /** Jede Projekt-Rolle und der Plattform-Admin ohne Mitgliedschaft. */
  static Stream<String> aufrufer() {
    return Stream.concat(
        Stream.of(ProjectRole.values()).map(ProjectRole::name), Stream.of(PLATTFORM_ADMIN));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("aufrufer")
  @DisplayName("Szenario: Ein Mitglied ohne Owner-Rolle öffnet ein Board aus GitLab")
  void szenarioMitgliedOeffnetBoardAusGitLab(String wer) throws Exception {
    // Angenommen ein Projekt mit Board in GitLab, und der letzte Abgleich ist heute um 9:58
    // gelungen
    long projekt = projektMitGitLab("Gelungen-" + wer);
    abgleichGelingt(projekt);

    // Wenn ein Mitglied das Board öffnet
    JsonNode stand = stand(sitzung(wer, projekt), projekt);

    // Dann erfährt es, dass das Board in GitLab liegt, mit dem Stand von 9:58 Uhr
    assertThat(felder(stand)).containsExactlyInAnyOrderElementsOf(VIER_FELDER);
    assertThat(stand.get("imFremdsystem").asBoolean()).isTrue();
    assertThat(stand.get("art").asText()).isEqualTo("GITLAB");
    assertThat(Instant.parse(stand.get("standAm").asText())).isEqualTo(GELUNGEN);
    assertThat(stand.get("abgleichGescheitert").asBoolean()).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("aufrufer")
  @DisplayName("Szenario: GitLab ist für ein Mitglied ohne Owner-Rolle nicht erreichbar")
  void szenarioGitLabNichtErreichbar(String wer) throws Exception {
    // Angenommen ein Projekt mit Board in GitLab, und der Abgleich scheitert seit einer Stunde
    long projekt = projektMitGitLab("Gescheitert-" + wer);
    abgleichGelingt(projekt);
    abgleichScheitert(projekt);

    // Wenn ein Mitglied das Board öffnet
    ResultActions antwort = abrufen(sitzung(wer, projekt), projekt).andExpect(status().isOk());
    JsonNode stand = lies(antwort);

    // Dann sieht es, dass GitLab nicht erreicht wird und von wann der Stand ist
    assertThat(felder(stand)).containsExactlyInAnyOrderElementsOf(VIER_FELDER);
    assertThat(stand.get("imFremdsystem").asBoolean()).isTrue();
    assertThat(stand.get("abgleichGescheitert").asBoolean()).isTrue();
    assertThat(Instant.parse(stand.get("standAm").asText())).isEqualTo(GELUNGEN);
    // Und es sieht nicht, woran der Abgleich gescheitert ist
    assertThat(text(antwort)).doesNotContain(FEHLER).doesNotContain("503");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("aufrufer")
  @DisplayName("Szenario: Ein Betrachter sieht keine Verwaltungsangaben der Anbindung")
  void szenarioBetrachterSiehtKeineVerwaltungsangaben(String wer) throws Exception {
    // Angenommen ein Projekt mit Board in einem selbst betriebenen GitLab, Abgleich gescheitert
    long projekt = projektMitGitLab("Betrachter-" + wer);
    abgleichGelingt(projekt);
    abgleichScheitert(projekt);
    Cookie betrachter = mitRolle("betrachter-" + wer + "@example.com", projekt, "VIEWER");

    // Wenn ein Betrachter (und jede andere Rolle) das Board öffnet
    ResultActions antwort = abrufen(sitzung(wer, projekt), projekt).andExpect(status().isOk());
    JsonNode stand = lies(antwort);

    // Dann erfährt er Ort, Stand und dass der letzte Abgleich gescheitert ist
    assertThat(felder(stand)).containsExactlyInAnyOrderElementsOf(VIER_FELDER);
    assertThat(stand.get("art").asText()).isEqualTo("GITLAB");
    assertThat(Instant.parse(stand.get("standAm").asText())).isEqualTo(GELUNGEN);
    assertThat(stand.get("abgleichGescheitert").asBoolean()).isTrue();
    // Und er sieht weder Konto noch Fehlertext, Adresse oder Projektpfad
    assertThat(text(antwort))
        .doesNotContain(KIT_KONTO)
        .doesNotContain(FEHLER)
        .doesNotContain(ADRESSE)
        .doesNotContain(PFAD);
    // Die Abfrage des Owners bleibt dem Betrachter verschlossen
    mvc.perform(get("/api/projects/" + projekt + "/fremdboard").cookie(betrachter))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("aufrufer")
  @DisplayName("Szenario: Ein Board ist angebunden, aber noch nicht abgeglichen")
  void szenarioAngebundenAberNochNichtAbgeglichen(String wer) throws Exception {
    // Angenommen der Owner hat das Board eben an GitLab angebunden
    long projekt = projektMitGitLab("Neu-" + wer);
    Cookie mitglied = sitzung(wer, projekt);

    // Wenn ein Mitglied das Board öffnet — direkt nach dem Anbinden
    JsonNode direkt = stand(mitglied, projekt);
    // … und während der erste Abgleich beansprucht, aber noch nicht fertig ist
    assertThat(verbindungen.beanspruchen(projekt, null, GELUNGEN)).isTrue();
    JsonNode waehrend = stand(mitglied, projekt);

    // Dann liegt das Board in GitLab, und noch liegt kein Stand von dort vor
    for (JsonNode stand : List.of(direkt, waehrend)) {
      assertThat(felder(stand)).containsExactlyInAnyOrderElementsOf(VIER_FELDER);
      assertThat(stand.get("imFremdsystem").asBoolean()).isTrue();
      assertThat(stand.get("art").asText()).isEqualTo("GITLAB");
      assertThat(stand.get("standAm").isNull()).isTrue();
      assertThat(stand.get("abgleichGescheitert").asBoolean()).isFalse();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("aufrufer")
  @DisplayName("Szenario: Ein Mitglied öffnet ein Board in kanban-kit")
  void szenarioMitgliedOeffnetBoardInKanbanKit(String wer) throws Exception {
    // Angenommen ein Projekt, dessen Board in kanban-kit liegt
    long projekt = projekt("Eigen-" + wer);

    // Wenn ein Mitglied das Board öffnet
    JsonNode stand = stand(sitzung(wer, projekt), projekt);

    // Dann gibt es keinen Hinweis auf GitLab und keine Zeit eines Stands
    assertThat(felder(stand)).containsExactlyInAnyOrderElementsOf(VIER_FELDER);
    assertThat(stand.get("imFremdsystem").asBoolean()).isFalse();
    assertThat(stand.get("art").isNull()).isTrue();
    assertThat(stand.get("standAm").isNull()).isTrue();
    assertThat(stand.get("abgleichGescheitert").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("Szenario: Wer nicht Mitglied ist, erfährt nichts")
  void szenarioNichtmitgliedErfaehrtNichts() throws Exception {
    // Angenommen ein Projekt mit Board in GitLab — und eines mit Board in kanban-kit
    long gitlab = projektMitGitLab("Fremd");
    abgleichScheitert(gitlab);
    long eigen = projekt("Eigen");
    Cookie fremder = anmeldung.anmelden("fremder-stand@example.com", PlatformRole.USER);

    // Wenn jemand, der weder Mitglied noch Plattform-Admin ist, die Auskunft abruft
    JsonNode beiGitLab = problem(abrufen(fremder, gitlab).andExpect(status().isNotFound()));
    JsonNode beiEigenem = problem(abrufen(fremder, eigen).andExpect(status().isNotFound()));
    JsonNode beiUnbekanntem =
        problem(abrufen(fremder, Long.MAX_VALUE).andExpect(status().isNotFound()));

    // Dann verrät nichts, ob das Board in GitLab oder in kanban-kit liegt — oder ob es das Projekt
    // überhaupt gibt
    assertThat(beiGitLab).isEqualTo(beiEigenem).isEqualTo(beiUnbekanntem);
    // Ein Plattform-Admin ohne Mitgliedschaft bekommt die Auskunft
    assertThat(stand(admin, gitlab).get("imFremdsystem").asBoolean()).isTrue();
  }

  /** Projekt des Owners mit dem Board aus der Projektanlage; liefert die Projekt-Id. */
  private long projekt(String name) throws Exception {
    return lies(mvc.perform(
                post("/api/projects")
                    .cookie(admin)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\",\"ownerEmail\":\"" + OWNER + "\"}"))
            .andExpect(status().isCreated()))
        .get("id")
        .asLong();
  }

  /** Ein Projekt, dessen Board eben an ein selbst betriebenes GitLab gebunden wurde. */
  private long projektMitGitLab(String name) throws Exception {
    long projekt = projekt(name);
    long board =
        jdbc.queryForObject("SELECT id FROM board WHERE project_id = ?", Long.class, projekt);
    verbindungen.speichere(
        new Verbindung(
            projekt,
            FremdBoardArt.GITLAB,
            ADRESSE,
            PFAD,
            "v1:verschluesselt",
            KIT_KONTO,
            StatusLabels.STANDARD,
            board,
            GELUNGEN.minusSeconds(600),
            null,
            null,
            null));
    return projekt;
  }

  private void abgleichGelingt(long projekt) {
    assertThat(verbindungen.beanspruchen(projekt, null, GELUNGEN)).isTrue();
    verbindungen.abgleichGelungen(projekt, GELUNGEN, GELUNGEN);
  }

  private void abgleichScheitert(long projekt) {
    Instant bisher = verbindungen.finde(projekt).orElseThrow().letzterAbgleichAm();
    assertThat(verbindungen.beanspruchen(projekt, bisher, GESCHEITERT)).isTrue();
    verbindungen.abgleichGescheitert(projekt, GESCHEITERT, bisher, FEHLER);
  }

  /** Sitzung des Aufrufers: Owner, eine weitere Rolle im Projekt oder Plattform-Admin. */
  private Cookie sitzung(String wer, long projekt) {
    if (PLATTFORM_ADMIN.equals(wer)) {
      return admin;
    }
    if (ProjectRole.OWNER.name().equals(wer)) {
      return anmeldung.anmelden(OWNER, PlatformRole.USER);
    }
    return mitRolle(wer.toLowerCase(Locale.ROOT) + "-stand@example.com", projekt, wer);
  }

  private Cookie mitRolle(String email, long projekt, String rolle) {
    Cookie session = anmeldung.anmelden(email, PlatformRole.USER);
    long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, email);
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role) VALUES (?, ?, ?)",
        projekt,
        userId,
        rolle);
    return session;
  }

  private ResultActions abrufen(Cookie wer, long projekt) throws Exception {
    return mvc.perform(get("/api/projects/" + projekt + "/fremdboard/stand").cookie(wer));
  }

  private JsonNode stand(Cookie wer, long projekt) throws Exception {
    return lies(abrufen(wer, projekt).andExpect(status().isOk()));
  }

  /** Der Problem-Body ohne {@code instance}, das nur den aufgerufenen Pfad wiederholt. */
  private JsonNode problem(ResultActions antwort) throws Exception {
    ObjectNode body = (ObjectNode) lies(antwort);
    body.remove("instance");
    return body;
  }

  private JsonNode lies(ResultActions antwort) throws Exception {
    return json.readTree(text(antwort));
  }

  private static String text(ResultActions antwort) throws Exception {
    return antwort.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private static List<String> felder(JsonNode knoten) {
    List<String> namen = new ArrayList<>();
    knoten.fieldNames().forEachRemaining(namen::add);
    return namen;
  }
}
