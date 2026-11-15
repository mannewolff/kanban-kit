package org.mwolff.manban.fremdboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.project.application.FremdesBoardException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Die Schreibsperre bei fremdem Board über alle schreibenden Endpunkte (Issue #2164, Plan #2152
 * A10, Finding 7 der Plan-Prüfung): Der Test liest jeden {@code POST}-, {@code PUT}-, {@code
 * PATCH}- und {@code DELETE}-Pfad aus {@code /api/openapi}, der Karten, Kommentare, Anhänge, Boards
 * oder Vorhaben betrifft, ruft ihn bei einem Projekt mit fremdem Board auf und erwartet 409 mit dem
 * Problem-Typ {@value FremdesBoardException#PROBLEM_TYP}. Ein neuer schreibender Endpunkt ohne
 * Sperre macht ihn damit rot, ohne dass ihn jemand hier eintragen muss.
 *
 * <p>Pfadparameter und Rumpf entstehen aus der Beschreibung selbst: Parameter nach ihrem Namen,
 * Rümpfe aus dem Schema mit den Pflichtfeldern, Kennungen nach dem Feldnamen. Ein Endpunkt, für den
 * das nicht reicht, scheitert ebenfalls laut — mit Methode, Pfad, Status und Antwort.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
// PMD.GodClass, PMD.CouplingBetweenObjects, PMD.ExcessiveImports: Der
// Test erzeugt Aufrufe aus der API-Beschreibung — Pfadparameter, Rümpfe, Dateien und Zugänge. Die
// Helfer dafür gehören zu genau diesem einen Prüfweg; aufgeteilt läse ihn niemand mehr am Stück.
@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects", "PMD.ExcessiveImports"})
class SchreibsperreIT extends AbstractIntegrationTest {

  private static final String JSON = "application/json";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String OWNER = "owner-sperre@example.com";
  private static final String NIGHT_RUNS = "POST /api/kanban/night-runs";

  /** Format, mit dem die Beschreibung ein Dateifeld kennzeichnet. */
  private static final String BINAER = "binary";

  /** Schreibende Methoden, wie sie in der Beschreibung stehen. */
  private static final Set<String> SCHREIBEND = Set.of("post", "put", "patch", "delete");

  /**
   * Wörter eines Pfads mit Bezug auf Karten, Kommentare, Anhänge, Boards oder Vorhaben. Jeder Pfad
   * unter {@code /api/kanban} zählt mit: Sein Zugang ist an ein Board gebunden.
   */
  private static final Set<String> BEZUG =
      Set.of(
          "card",
          "cards",
          "comment",
          "comments",
          "attachment",
          "attachments",
          "board",
          "boards",
          "column",
          "columns",
          "label",
          "labels",
          "item",
          "items",
          "epic",
          "epics",
          "vorhaben",
          "fremdboard",
          "kanban");

  /** Trennt einen Pfad in Wörter: an Schrägstrich und Bindestrich. */
  private static final Pattern WORTGRENZE = Pattern.compile("[/-]");

  /**
   * Welches Wort im Namen eines Felds oder Parameters auf welche angelegte Kennung zeigt, in dieser
   * Reihenfolge: Vorhaben und die zweite Karte vor der Karte, die Kartenteile vor der Karte.
   */
  private static final List<Map.Entry<String, String>> KENNUNGEN =
      List.of(
          Map.entry("parent", "vorhaben"),
          Map.entry("epic", "vorhaben"),
          Map.entry("vorhaben", "vorhaben"),
          Map.entry("requirement", "zweite"),
          Map.entry("targetcard", "zweite"),
          Map.entry("attachment", "attachment"),
          Map.entry("comment", "comment"),
          Map.entry("label", "label"),
          Map.entry("column", "column"),
          Map.entry("card", "card"),
          Map.entry("board", "board"),
          Map.entry("project", "project"),
          Map.entry("assignee", "user"),
          Map.entry("user", "user"));

  private static final Pattern PARAMETER = Pattern.compile("\\{([^}]+)}");

  /**
   * Bleiben bei fremdem Board offen: Durchläufe und Kosten (sonst gibt es keine Laufdaten), der
   * Anstoß des Abgleichs und das Einrichten der Verbindung (Issue #2164). Dazu die Meldung der
   * Auslieferungen, die wie die Durchläufe den Leitstand speist, und zwei Wege, die kein Board
   * beschreiben: das eigene Push-Abo eines Menschen für ein Board und das Anlegen der
   * Standard-Labels durch den Plattform-Admin, das Projekte mit fremdem Board überspringt (Issue
   * #2161).
   */
  private static final List<Pattern> AUSGENOMMEN =
      List.of(
          Pattern.compile("POST /api/kanban/night-runs.*"),
          Pattern.compile("POST /api/kanban/fremdboard/anstoss"),
          Pattern.compile("PUT /api/projects/\\{id}/fremdboard"),
          Pattern.compile("POST /api/kanban/deployments"),
          Pattern.compile("PUT /api/push/boards/\\{boardId}"),
          Pattern.compile("POST /api/admin/standard-labels/anlegen"));

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private JdbcTemplate jdbc;

  private Cookie session;
  private String token;
  private Map<String, Long> ids;
  private JsonNode beschreibung;

  /** Eine schreibende Operation der Beschreibung. */
  private record Operation(String methode, String pfad, JsonNode spec) {
    String name() {
      return methode.toUpperCase(Locale.ROOT) + " " + pfad;
    }
  }

  @BeforeEach
  void aufbau() throws Exception {
    session = anmeldung.anmelden(OWNER, PlatformRole.ADMIN);
    beschreibung = lies(mvc.perform(get("/api/openapi").cookie(session)));
    long projekt =
        id(
            schreibe(
                post("/api/projects"), "{\"name\":\"Sperre\",\"ownerEmail\":\"" + OWNER + "\"}"));
    long board = id(schreibe(post("/api/projects/" + projekt + "/boards"), "{\"name\":\"Board\"}"));
    long spalte =
        id(schreibe(post("/api/boards/" + board + "/columns"), "{\"name\":\"Zusatzspalte\"}"));
    String karte = "{\"columnId\":%d,\"title\":\"%s\"}";
    long card =
        id(schreibe(post("/api/boards/" + board + "/cards"), karte.formatted(spalte, "Karte")));
    long zweite =
        id(schreibe(post("/api/boards/" + board + "/cards"), karte.formatted(spalte, "Zweite")));
    long vorhaben =
        id(
            schreibe(
                post("/api/boards/" + board + "/cards"),
                "{\"title\":\"Vorhaben\",\"type\":\"EPIC\",\"shortcode\":\"VH\"}"));
    long label =
        id(
            schreibe(
                post("/api/boards/" + board + "/labels"),
                "{\"name\":\"Fehler\",\"color\":\"#c62828\"}"));
    long kommentar =
        id(schreibe(post("/api/cards/" + card + "/comments"), "{\"body\":\"Kommentar\"}"));
    long anhang =
        id(
            mvc.perform(
                    multipart("/api/cards/" + card + "/attachments").file(datei()).cookie(session))
                .andExpect(status().isCreated()));
    token =
        lies(schreibe(
                post("/api/access-tokens"),
                "{\"name\":\"Kit\",\"projectId\":%d,\"boardId\":%d}".formatted(projekt, board)))
            .get("plaintext")
            .asText();
    long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, OWNER);
    ids =
        Map.of(
            "project", projekt,
            "board", board,
            "column", spalte,
            "card", card,
            "zweite", zweite,
            "vorhaben", vorhaben,
            "label", label,
            "comment", kommentar,
            "attachment", anhang,
            "user", userId);
    verbindeMitFremdemBoard(projekt, board);
  }

  @Test
  void jederSchreibendeEndpunktMitBoardbezugIstBeiFremdemBoardGesperrt() throws Exception {
    List<Operation> gepruefte = schreibendeMitBezug(true);

    assertThat(gepruefte)
        .extracting(Operation::name)
        .contains(
            "POST /api/cards/{cardId}/comments",
            "PATCH /api/comments/{commentId}",
            "DELETE /api/comments/{commentId}",
            "POST /api/cards/{cardId}/attachments",
            "DELETE /api/attachments/{id}",
            "POST /api/kanban/items",
            "POST /api/kanban/items/{id}/comments",
            "PATCH /api/kanban/items/{id}/comments/{commentId}",
            "PUT /api/kanban/items/{id}/move");
    assertThat(ungesperrt(gepruefte)).isEmpty();
  }

  @Test
  void gegenprobeEinUngesperrterPfadMachtDenTestRot() throws Exception {
    // Given: ein schreibender Pfad, der absichtlich offen bleibt, als wäre er nicht ausgenommen.
    List<Operation> mitUngesperrtem = schreibendeMitBezug(false);
    assertThat(mitUngesperrtem).extracting(Operation::name).contains(NIGHT_RUNS);

    // When
    List<String> befund =
        ungesperrt(mitUngesperrtem.stream().filter(o -> NIGHT_RUNS.equals(o.name())).toList());

    // Then
    assertThat(befund).singleElement().asString().startsWith(NIGHT_RUNS);
  }

  @Test
  @DisplayName("Zugang der App wird bei fremdem Board abgewiesen")
  void zugangDerAppWirdBeiFremdemBoardAbgewiesen() throws Exception {
    // Given: Die Desktop-App (#2097) schreibt über /api/kanban; geantwortet wird in GitLab.
    String app =
        lies(schreibe(
                post("/api/access-tokens/app"),
                "{\"projectId\":%d,\"boardId\":%d}"
                    .formatted(ids.get("project"), ids.get("board"))))
            .get("plaintext")
            .asText();

    // When / Then
    mvc.perform(
            post("/api/kanban/items/" + ids.get("card") + "/comments")
                .header(TOKEN_HEADER, app)
                .contentType(JSON)
                .content("{\"body\":\"Antwort aus der App\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value(FremdesBoardException.PROBLEM_TYP));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM comment WHERE card_id = ?", Integer.class, ids.get("card")))
        .isEqualTo(1);
  }

  @Test
  void durchlaeufeWerdenBeiFremdemBoardWeiterAngenommen() throws Exception {
    mvc.perform(
            post("/api/kanban/night-runs")
                .header(TOKEN_HEADER, token)
                .contentType(JSON)
                .content(
                    """
                    {"startedAt":"2026-11-15T01:00:00Z","mode":"CHAIN","durationMs":60000,
                     "processedCount":0,"skippedCount":0,"unparsedCount":0,"complete":true,
                     "items":[]}"""))
        .andExpect(status().isOk());
  }

  /** Die schreibenden Operationen mit Bezug, auf Wunsch ohne die ausgenommenen. */
  private List<Operation> schreibendeMitBezug(boolean ohneAusgenommene) {
    List<Operation> ergebnis = new ArrayList<>();
    for (Map.Entry<String, JsonNode> pfad : beschreibung.get("paths").properties()) {
      if (!hatBezug(pfad.getKey())) {
        continue;
      }
      for (Map.Entry<String, JsonNode> methode : pfad.getValue().properties()) {
        if (!SCHREIBEND.contains(methode.getKey())) {
          continue;
        }
        Operation op = new Operation(methode.getKey(), pfad.getKey(), methode.getValue());
        if (!ohneAusgenommene
            || AUSGENOMMEN.stream().noneMatch(a -> a.matcher(op.name()).matches())) {
          ergebnis.add(op);
        }
      }
    }
    return ergebnis;
  }

  private static boolean hatBezug(String pfad) {
    String ohneParameter = PARAMETER.matcher(pfad).replaceAll("");
    return WORTGRENZE
        .splitAsStream(ohneParameter.toLowerCase(Locale.ROOT))
        .anyMatch(BEZUG::contains);
  }

  /** Ruft jede Operation auf und liefert die, die nicht mit der Sperre antworten. */
  private List<String> ungesperrt(List<Operation> operationen) throws Exception {
    List<String> befund = new ArrayList<>();
    for (Operation op : operationen) {
      MockHttpServletResponse antwort = mvc.perform(aufruf(op)).andReturn().getResponse();
      String typ = typ(antwort);
      if (antwort.getStatus() != 409 || !FremdesBoardException.PROBLEM_TYP.equals(typ)) {
        befund.add(
            "%s → %d %s".formatted(op.name(), antwort.getStatus(), antwort.getContentAsString()));
      }
    }
    return befund;
  }

  private @Nullable String typ(MockHttpServletResponse antwort) throws Exception {
    String text = antwort.getContentAsString();
    if (text.isBlank() || !text.trim().startsWith("{")) {
      return null;
    }
    JsonNode typ = json.readTree(text).get("type");
    return typ == null ? null : typ.asText();
  }

  private MockHttpServletRequestBuilder aufruf(Operation op) {
    String url = pfadMitKennungen(op.pfad());
    JsonNode rumpf = op.spec().path("requestBody").path("content");
    MockHttpServletRequestBuilder anfrage;
    String dateifeld = dateifeld(rumpf);
    if (dateifeld != null) {
      MockMultipartHttpServletRequestBuilder mehrteilig = multipart(url).file(datei(dateifeld));
      mehrteilig.with(
          r -> {
            r.setMethod(op.methode().toUpperCase(Locale.ROOT));
            return r;
          });
      anfrage = mehrteilig;
    } else {
      anfrage = request(HttpMethod.valueOf(op.methode().toUpperCase(Locale.ROOT)), url);
      JsonNode schema = rumpf.path(JSON).path("schema");
      if (!schema.isMissingNode()) {
        anfrage.contentType(JSON).content(beispiel(schema, "").toString());
      }
    }
    anfrage.queryParams(abfrageParameter(op));
    return op.pfad().startsWith("/api/kanban/")
        ? anfrage.header(TOKEN_HEADER, token)
        : anfrage.cookie(session);
  }

  /**
   * Das Feld der hochgeladenen Datei, wenn der Rumpf eine trägt: als {@code multipart/form-data}
   * oder, wie springdoc einen {@code MultipartFile}-Parameter beschreibt, als Feld mit dem Format
   * {@code binary}.
   */
  private @Nullable String dateifeld(JsonNode inhalt) {
    for (Map.Entry<String, JsonNode> art : inhalt.properties()) {
      for (Map.Entry<String, JsonNode> feld :
          aufgeloest(art.getValue().path("schema")).path("properties").properties()) {
        if (BINAER.equals(feld.getValue().path("format").asText())) {
          return feld.getKey();
        }
      }
    }
    return null;
  }

  /** Pflichtige Abfrageparameter mit einem Beispielwert. */
  private MultiValueMap<String, String> abfrageParameter(Operation op) {
    MultiValueMap<String, String> werte = new LinkedMultiValueMap<>();
    for (JsonNode parameter : op.spec().path("parameters")) {
      if ("query".equals(parameter.path("in").asText()) && parameter.path("required").asBoolean()) {
        String name = parameter.get("name").asText();
        JsonNode wert = beispiel(parameter.path("schema"), name);
        werte.add(name, wert.isTextual() ? wert.asText() : wert.toString());
      }
    }
    return werte;
  }

  private String pfadMitKennungen(String pfad) {
    Matcher m = PARAMETER.matcher(pfad);
    StringBuilder url = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(url, String.valueOf(kennungFuerParameter(pfad, m.group(1))));
    }
    m.appendTail(url);
    return url.toString();
  }

  private long kennungFuerParameter(String pfad, String name) {
    if (!"id".equals(name)) {
      return kennungFuerName(name);
    }
    if (pfad.startsWith("/api/attachments/")) {
      return ids.get("attachment");
    }
    if (pfad.startsWith("/api/kanban/items/")) {
      return ids.get("card");
    }
    if (pfad.startsWith("/api/projects/")) {
      return ids.get("project");
    }
    throw new IllegalStateException("Unbekannter Pfadparameter id in " + pfad);
  }

  /**
   * Eine Kennung nach dem Feld- oder Parameternamen: das erste Wort aus {@link #KENNUNGEN}, das im
   * Namen vorkommt.
   */
  private long kennungFuerName(String name) {
    String n = name.toLowerCase(Locale.ROOT);
    return KENNUNGEN.stream()
        .filter(k -> n.contains(k.getKey()))
        .findFirst()
        .map(k -> ids.get(k.getValue()))
        .orElseThrow(() -> new IllegalStateException("Keine Kennung für " + name));
  }

  /** Ein Beispielwert zum Schema; bei Objekten nur die Pflichtfelder. */
  private JsonNode beispiel(JsonNode schema, String name) {
    JsonNode s = aufgeloest(schema);
    if (s.has("example") && !istKennung(name)) {
      return s.get("example");
    }
    if (s.has("enum")) {
      return s.get("enum").get(0);
    }
    String typ = s.path("type").asText(s.has("properties") ? "object" : "string");
    return switch (typ) {
      case "object" -> objekt(s);
      case "array" -> {
        ArrayNode liste = json.createArrayNode();
        liste.add(beispiel(s.path("items"), name));
        yield liste;
      }
      case "integer", "number" ->
          json.getNodeFactory().numberNode(istKennung(name) ? kennungFuerName(name) : 1L);
      case "boolean" -> json.getNodeFactory().booleanNode(false);
      default -> json.getNodeFactory().textNode(text(s));
    };
  }

  /**
   * Ein Objekt mit den Pflichtfeldern und den einzelnen Kennungen, etwa der Zielspalte einer neuen
   * Karte; nennt das Schema keine Pflichtfelder, mit allen Feldern.
   */
  private ObjectNode objekt(JsonNode s) {
    ObjectNode objekt = json.createObjectNode();
    Set<String> pflicht = new HashSet<>();
    s.path("required").forEach(r -> pflicht.add(r.asText()));
    boolean alle = pflicht.isEmpty();
    for (Map.Entry<String, JsonNode> feld : s.path("properties").properties()) {
      boolean kennung =
          istKennung(feld.getKey()) && "integer".equals(feld.getValue().path("type").asText());
      if (alle || kennung || pflicht.contains(feld.getKey())) {
        objekt.set(feld.getKey(), beispiel(feld.getValue(), feld.getKey()));
      }
    }
    return objekt;
  }

  private static String text(JsonNode s) {
    String format = s.path("format").asText();
    return switch (format) {
      case "date-time" -> "2026-11-15T10:00:00Z";
      case "date" -> "2026-11-15";
      default -> "Wert";
    };
  }

  private static boolean istKennung(String name) {
    String n = name.toLowerCase(Locale.ROOT);
    return n.endsWith("id") || n.endsWith("ids") || "assignees".equals(n) || "labels".equals(n);
  }

  private JsonNode aufgeloest(JsonNode schema) {
    JsonNode s = schema;
    while (s.has("$ref")) {
      String ref = s.get("$ref").asText();
      s = beschreibung.at("/components/schemas/" + ref.substring(ref.lastIndexOf('/') + 1));
    }
    if (s.has("allOf") && s.get("allOf").size() == 1) {
      return aufgeloest(s.get("allOf").get(0));
    }
    return s;
  }

  private void verbindeMitFremdemBoard(long projekt, long board) {
    jdbc.update(
        "INSERT INTO fremdboard_verbindung (project_id, art, basis_url, projekt_pfad,"
            + " zugang_verschluesselt, statuslabels, board_id, angebunden_am)"
            + " VALUES (?, 'GITLAB', 'https://gitlab.com', 'gruppe/projekt', 'geheim',"
            + " '{\"ready\":\"Ready\"}'::jsonb, ?, now())",
        projekt,
        board);
  }

  private static MockMultipartFile datei() {
    return datei("file");
  }

  private static MockMultipartFile datei(String feld) {
    return new MockMultipartFile(
        feld, "notiz.txt", "text/plain", "Inhalt".getBytes(StandardCharsets.UTF_8));
  }

  private ResultActions schreibe(MockHttpServletRequestBuilder anfrage, String rumpf)
      throws Exception {
    return mvc.perform(anfrage.cookie(session).contentType(JSON).content(rumpf))
        .andExpect(status().is2xxSuccessful());
  }

  private JsonNode lies(ResultActions antwort) throws Exception {
    return json.readTree(antwort.andReturn().getResponse().getContentAsString());
  }

  private long id(ResultActions antwort) throws Exception {
    return lies(antwort).get("id").asLong();
  }
}
