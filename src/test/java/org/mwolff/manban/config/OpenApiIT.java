package org.mwolff.manban.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die API-Beschreibung unter {@code /api/openapi} (Issue #1402, Plan #1400), Teil (a): der Zugang.
 * Abrufen darf sie nur, wer per Session angemeldet ist (E1) — ein Zugriffs-Token wird wie bei
 * {@code /api/access-tokens} mit 403 abgewiesen, ein ungültiges oder widerrufenes mit 401.
 *
 * <p>Teil (d) (Issue #1403): Die verlässlichen Operationen stimmen mit dem Vertragsschnappschuss
 * {@value #SCHNAPPSCHUSS} überein — Pfad, Methode, Parameter, Schemas und {@code abgekuendigtSeit}.
 * Beschreibungen und Beispiele gehören nicht zum Vertrag und bleiben außen vor. Der Schnappschuss
 * bemerkt eine Änderung, er erzwingt die Abkündigungsfrist nicht; die prüft der Review am Diff.
 *
 * <p>Teil (b) und (c) (Issue #1409): Jeder Handler unter {@code /api/**} erscheint als Operation,
 * und jede Operation trägt die Pflichtangaben; ein neuer Endpunkt ohne Beschreibung macht den Build
 * rot. Die Prüfroutinen und ihre Gegenproben stehen in {@link OpenApiPflichtpruefung}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class OpenApiIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;

  @Autowired private AppUserRepository users;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private ObjectMapper json;

  /** Klassenpfad des Vertragsschnappschusses der verlässlichen Aufrufe. */
  static final String SCHNAPPSCHUSS = "/openapi/verlaessliche-aufrufe.json";

  /** Hierhin schreibt Teil (d) bei einer Abweichung den Ist-Stand, zum Prüfen und Übernehmen. */
  private static final Path IST_STAND = Path.of("target", "openapi", "verlaessliche-aufrufe.json");

  /** Die zwölf verlässlichen Operationen zum Start (Plan #1400, E4). */
  private static final List<String> VERLAESSLICH_ZUM_START =
      List.of(
          "DELETE /api/kanban/items/{id}/labels",
          "GET /api/kanban/epics",
          "GET /api/kanban/items",
          "GET /api/kanban/items/{id}/activity",
          "GET /api/kanban/items/{id}/comments",
          "PATCH /api/kanban/items/{id}/comments/{commentId}",
          "POST /api/kanban/items",
          "POST /api/kanban/items/{id}/comments",
          "POST /api/kanban/items/{id}/labels",
          "POST /api/kanban/night-runs",
          "PUT /api/kanban/items/{id}",
          "PUT /api/kanban/items/{id}/move");

  private static final Set<String> METHODEN =
      Set.of("get", "put", "post", "delete", "patch", "head", "options", "trace");

  /** Zeile, die {@code ApiBeschreibungsAnreicherung} einem abgekündigten Aufruf voranstellt. */
  private static final Pattern ABGEKUENDIGT = Pattern.compile("^Abgekündigt seit (\\d+\\.\\d+)");

  /** Nicht Teil des Vertrags: Texte und Beispiele ändern sich, ohne dass ein Aufrufer bricht. */
  private static final Set<String> KEIN_VERTRAG = Set.of("description", "example", "examples");

  private static final String REF_PRAEFIX = "#/components/schemas/";

  /** Die Operation und ihr Parameter, die die Gegenprobe im Schnappschuss umbenennt. */
  private static final String GEGENPROBE_OPERATION = "DELETE /api/kanban/items/{id}/labels";

  private static final String GEGENPROBE_PARAMETER = "name";

  /** Das Schema, dessen Feld die zweite Gegenprobe ändert, und die Operation, die es verwendet. */
  private static final String GEGENPROBE_SCHEMA = "IngestRequest";

  private static final String GEGENPROBE_SCHEMA_OPERATION = "POST /api/kanban/night-runs";

  private static final String ABWEICHEND = "Abweichende verlässliche Aufrufe: ";

  private Cookie loginAs(String email) throws Exception {
    users.save(
        new AppUser(
            null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.USER));
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }

  private JsonNode tokenAnlegen(Cookie session) throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType("application/json")
                    .content("{\"name\":\"Spezifikation\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private JsonNode spezifikation(String email) throws Exception {
    String body =
        mvc.perform(get("/api/openapi").cookie(loginAs(email)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return json.readTree(body);
  }

  @Test
  void ohneAnmeldungAntwortet401() throws Exception {
    mvc.perform(get("/api/openapi")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/openapi.yaml")).andExpect(status().isUnauthorized());
  }

  @Test
  void mitGueltigemTokenAntwortet403() throws Exception {
    String token = tokenAnlegen(loginAs("spec-token@example.com")).get("plaintext").asText();

    mvc.perform(get("/api/openapi").header("X-Kanban-Token", token))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/openapi.yaml").header("X-Kanban-Token", token))
        .andExpect(status().isForbidden());
  }

  @Test
  void mitUngueltigemTokenAntwortet401() throws Exception {
    mvc.perform(get("/api/openapi").header("X-Kanban-Token", "tk_deadbeef"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void mitWiderrufenemTokenAntwortet401() throws Exception {
    Cookie session = loginAs("spec-revoked@example.com");
    JsonNode token = tokenAnlegen(session);
    mvc.perform(delete("/api/access-tokens/" + token.get("id").asLong()).cookie(session))
        .andExpect(status().is2xxSuccessful());

    mvc.perform(get("/api/openapi").header("X-Kanban-Token", token.get("plaintext").asText()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void mitSessionLiefertDieBeschreibungAlsJson() throws Exception {
    Cookie session = loginAs("spec-json@example.com");

    String body =
        mvc.perform(get("/api/openapi").cookie(session))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.info.title").value(OpenApiConfig.TITEL))
            .andExpect(
                jsonPath("$.components.securitySchemes.sessionCookie.name").value("manban_session"))
            .andExpect(
                jsonPath("$.components.securitySchemes.projektToken.name").value("X-Kanban-Token"))
            .andExpect(jsonPath("$.components.schemas.ProblemDetail.type").value("object"))
            .andExpect(
                jsonPath("$.components.schemas.ProblemDetail.properties.fieldErrors.type")
                    .value("object"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode spec = json.readTree(body);

    // Version aus build-info (E8), nicht der Rückfall.
    assertThat(spec.at("/info/version").asText())
        .isNotBlank()
        .isNotEqualTo(OpenApiConfig.VERSION_UNBEKANNT);
    // Zugang je Pfad aus derselben Tabelle wie die Security-Regeln.
    assertThat(spec.at("/paths/~1api~1auth~1login/post/x-zugang").asText())
        .isEqualTo("oeffentlich");
    assertThat(spec.at("/paths/~1api~1kanban~1items/get/x-zugang").asText())
        .isEqualTo("nur-projekt-token");
    assertThat(spec.at("/paths/~1api~1kanban~1items/get/responses/401").isMissingNode()).isFalse();
    // springdoc blendet @AuthenticationPrincipal-Parameter selbst aus (E13).
    JsonNode archivieren = spec.at("/paths/~1api~1cards~1{cardId}~1archive/post");
    assertThat(archivieren.isMissingNode()).isFalse();
    assertThat(archivieren.at("/parameters").findValuesAsText("name")).containsExactly("cardId");
  }

  @Test
  void mitSessionLiefertDieBeschreibungAlsYaml() throws Exception {
    Cookie session = loginAs("spec-yaml@example.com");

    String body =
        mvc.perform(get("/api/openapi.yaml").cookie(session))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/vnd.oai.openapi"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(body).startsWith("openapi: ").contains("title: " + OpenApiConfig.TITEL);
  }

  // --- Teil (b) und (c): Vollständigkeit und Pflichtangaben (Issue #1409) ------------------

  @Test
  void jederHandlerErscheintAlsOperation() throws Exception {
    JsonNode spec = spezifikation("spec-vollstaendig@example.com");
    Map<String, String> handler =
        OpenApiPflichtpruefung.handler(mvc.getDispatcherServlet().getWebApplicationContext());

    assertThat(handler).isNotEmpty().containsKey("POST /api/kanban/items");
    assertThat(OpenApiPflichtpruefung.fehlendeHandler(handler, OpenApiPflichtpruefung.modell(spec)))
        .as("Handler unter /api/** ohne Operation in /api/openapi")
        .isEmpty();
  }

  @Test
  void jedeOperationTraegtDiePflichtangaben() throws Exception {
    JsonNode spec = spezifikation("spec-pflicht@example.com");

    assertThat(OpenApiPflichtpruefung.pflichtangabenMaengel(OpenApiPflichtpruefung.modell(spec)))
        .as("Operationen mit fehlenden Pflichtangaben")
        .isEmpty();
  }

  // --- Teil (d): Vertragsschnappschuss der verlässlichen Aufrufe (Issue #1403) ----------------

  @Test
  void verlaesslicheAufrufeStimmenMitDemSchnappschussUeberein() throws Exception {
    JsonNode ist = vertrag(spezifikation("spec-vertrag@example.com"));

    assertThat(operationen(ist)).containsExactlyElementsOf(VERLAESSLICH_ZUM_START);
    vergleiche(schnappschussLesen(), ist);
  }

  @Test
  void abhaengigkeitenBleibenAenderbar() throws Exception {
    JsonNode spec = spezifikation("spec-aenderbar@example.com");

    assertThat(
            spec.at("/paths/~1api~1kanban~1items~1{id}~1dependencies/put/x-stabilitaet").asText())
        .isEqualTo("aenderbar");
    assertThat(operationen(schnappschussLesen()))
        .doesNotContain("PUT /api/kanban/items/{id}/dependencies");
  }

  /** Gegenprobe: Ein geänderter Parametername an einer verlässlichen Operation fällt auf. */
  @Test
  void geaenderterParameternameLaesstDenVergleichFehlschlagen() throws Exception {
    JsonNode ist = vertrag(spezifikation("spec-gegenprobe@example.com"));
    JsonNode manipuliert = schnappschussLesen().deepCopy();
    ObjectNode parameter = null;
    for (JsonNode operation : manipuliert.get("operationen")) {
      if (GEGENPROBE_OPERATION.equals(schluessel(operation))) {
        for (JsonNode p : operation.get("parameter")) {
          if (GEGENPROBE_PARAMETER.equals(p.path("name").asText())) {
            parameter = (ObjectNode) p;
          }
        }
      }
    }
    assertThat(parameter).as("Query-Parameter name im Schnappschuss").isNotNull();
    parameter.put("name", "labelName");

    assertThatThrownBy(() -> vergleiche(manipuliert, ist))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(ABWEICHEND + GEGENPROBE_OPERATION + ";")
        .hasMessageContaining("labelName");
  }

  /**
   * Gegenprobe (Issue #1523): Ein geändertes Feld eines Schemas nennt über die Verweishülle die
   * Operation, die es verwendet — und nur sie.
   */
  @Test
  void geaendertesSchemaNenntDieVerwendendeOperation() throws Exception {
    JsonNode ist = vertrag(spezifikation("spec-gegenprobe-schema@example.com"));
    JsonNode manipuliert = schnappschussLesen().deepCopy();
    JsonNode felder = manipuliert.path("schemas").path(GEGENPROBE_SCHEMA).path("properties");
    assertThat(felder.has("complete")).as("Feld complete in %s", GEGENPROBE_SCHEMA).isTrue();
    ((ObjectNode) felder.get("complete")).put("type", "string");

    assertThatThrownBy(() -> vergleiche(manipuliert, ist))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(ABWEICHEND + GEGENPROBE_SCHEMA_OPERATION + ";");
  }

  private JsonNode schnappschussLesen() throws IOException {
    try (@Nullable InputStream in = OpenApiIT.class.getResourceAsStream(SCHNAPPSCHUSS)) {
      return in == null ? json.createObjectNode() : json.readTree(in);
    }
  }

  /**
   * Vergleicht den Ist-Vertrag mit dem erwarteten. Bei einer Abweichung liegt der Ist-Stand danach
   * unter {@link #IST_STAND}: Ist die Änderung gewollt, wird er als Schnappschuss übernommen.
   */
  private void vergleiche(JsonNode erwartet, JsonNode ist) throws IOException {
    if (erwartet.equals(ist)) {
      return;
    }
    Files.createDirectories(IST_STAND.getParent());
    Files.writeString(
        IST_STAND, json.writerWithDefaultPrettyPrinter().writeValueAsString(ist) + "\n");
    assertThat(json.writerWithDefaultPrettyPrinter().writeValueAsString(ist))
        .as(
            "%s%s; Vertrag der verlässlichen Aufrufe weicht vom Schnappschuss %s ab; Ist-Stand: %s",
            ABWEICHEND,
            String.join(", ", abweichendeAufrufe(erwartet, ist)),
            SCHNAPPSCHUSS,
            IST_STAND)
        .isEqualTo(json.writerWithDefaultPrettyPrinter().writeValueAsString(erwartet));
  }

  /**
   * Die Schlüssel {@code METHODE Pfad}, deren Vertrag neu ist, fehlt oder sich unterscheidet (Plan
   * #1521, E8): direkt unter {@code operationen}, und über die Verweishülle für jedes neue,
   * fehlende oder geänderte Schema unter {@code schemas}.
   */
  private static Set<String> abweichendeAufrufe(JsonNode erwartet, JsonNode ist) {
    Map<String, JsonNode> alt = nachSchluessel(erwartet);
    Map<String, JsonNode> neu = nachSchluessel(ist);
    Set<String> abweichend = new TreeSet<>();
    Set<String> alleOperationen = new TreeSet<>(alt.keySet());
    alleOperationen.addAll(neu.keySet());
    alleOperationen.forEach(
        s -> {
          if (!alt.containsKey(s) || !alt.get(s).equals(neu.get(s))) {
            abweichend.add(s);
          }
        });
    JsonNode altSchemas = erwartet.path("schemas");
    JsonNode neuSchemas = ist.path("schemas");
    Set<String> geaenderteSchemas = new TreeSet<>();
    altSchemas.fieldNames().forEachRemaining(geaenderteSchemas::add);
    neuSchemas.fieldNames().forEachRemaining(geaenderteSchemas::add);
    geaenderteSchemas.removeIf(name -> altSchemas.path(name).equals(neuSchemas.path(name)));
    if (!geaenderteSchemas.isEmpty()) {
      alt.forEach((s, op) -> betroffen(s, op, altSchemas, geaenderteSchemas, abweichend));
      neu.forEach((s, op) -> betroffen(s, op, neuSchemas, geaenderteSchemas, abweichend));
    }
    return abweichend;
  }

  private static void betroffen(
      String schluessel,
      JsonNode operation,
      JsonNode schemas,
      Set<String> geaenderteSchemas,
      Set<String> abweichend) {
    Set<String> start = new TreeSet<>();
    verweise(operation, start);
    if (huelle(start, schemas).stream().anyMatch(geaenderteSchemas::contains)) {
      abweichend.add(schluessel);
    }
  }

  private static Map<String, JsonNode> nachSchluessel(JsonNode vertrag) {
    Map<String, JsonNode> operationen = new TreeMap<>();
    vertrag.path("operationen").forEach(o -> operationen.put(schluessel(o), o));
    return operationen;
  }

  /** Hülle über die Verweise: so lange erweitern, bis kein Schema einen neuen nennt. */
  private static Set<String> huelle(Set<String> start, JsonNode schemas) {
    Set<String> gesehen = new TreeSet<>(start);
    boolean gewachsen = true;
    while (gewachsen) {
      Set<String> weitere = new TreeSet<>();
      gesehen.forEach(name -> verweise(schemas.path(name), weitere));
      gewachsen = gesehen.addAll(weitere);
    }
    return gesehen;
  }

  private static List<String> operationen(JsonNode vertrag) {
    List<String> schluessel = new ArrayList<>();
    vertrag.path("operationen").forEach(o -> schluessel.add(schluessel(o)));
    return schluessel;
  }

  private static String schluessel(JsonNode operation) {
    return operation.path("methode").asText() + " " + operation.path("pfad").asText();
  }

  /**
   * Der Vertrag der verlässlichen Operationen: je Operation Pfad, Methode, Parameter, Rumpf, die
   * Erfolgsantworten und {@code abgekuendigtSeit}, dazu alle Schemas, auf die sie verweisen.
   * Sortiert, damit die Reihenfolge der Erzeugung nicht in den Vergleich eingeht.
   */
  private JsonNode vertrag(JsonNode spec) {
    Map<String, ObjectNode> operationen = new TreeMap<>();
    spec.path("paths")
        .properties()
        .forEach(
            pfad ->
                pfad.getValue()
                    .properties()
                    .forEach(
                        methode -> {
                          JsonNode op = methode.getValue();
                          if (METHODEN.contains(methode.getKey())
                              && "verlaesslich".equals(op.path("x-stabilitaet").asText())) {
                            ObjectNode eintrag =
                                operation(
                                    pfad.getKey(), methode.getKey().toUpperCase(Locale.ROOT), op);
                            operationen.put(schluessel(eintrag), eintrag);
                          }
                        }));
    ArrayNode liste = json.createArrayNode();
    operationen.values().forEach(liste::add);
    ObjectNode schemas = json.createObjectNode();
    JsonNode komponenten = spec.path("components").path("schemas");
    Set<String> offen = new TreeSet<>();
    verweise(liste, offen);
    huelle(offen, komponenten)
        .forEach(name -> schemas.set(name, normalisiert(komponenten.path(name), false)));
    ObjectNode vertrag = json.createObjectNode();
    vertrag.set("operationen", liste);
    vertrag.set("schemas", schemas);
    return vertrag;
  }

  private ObjectNode operation(String pfad, String methode, JsonNode op) {
    ObjectNode eintrag = json.createObjectNode();
    eintrag.put("pfad", pfad);
    eintrag.put("methode", methode);
    ArrayNode parameter = json.createArrayNode();
    Map<String, JsonNode> sortiert = new TreeMap<>();
    op.path("parameters")
        .forEach(p -> sortiert.put(p.path("in").asText() + ":" + p.path("name").asText(), p));
    sortiert.values().forEach(p -> parameter.add(normalisiert(p, false)));
    eintrag.set("parameter", parameter);
    eintrag.set("rumpf", normalisiert(op.path("requestBody"), false));
    ObjectNode antworten = json.createObjectNode();
    op.path("responses")
        .properties()
        .forEach(
            a -> {
              if (a.getKey().startsWith("2")) {
                antworten.set(a.getKey(), normalisiert(a.getValue(), false));
              }
            });
    eintrag.set("erfolg", antworten);
    Matcher abgekuendigt = ABGEKUENDIGT.matcher(op.path("description").asText());
    if (abgekuendigt.find()) {
      eintrag.put("abgekuendigtSeit", abgekuendigt.group(1));
    } else {
      eintrag.putNull("abgekuendigtSeit");
    }
    return eintrag;
  }

  /**
   * Kopie ohne Beschreibungen und Beispiele, Schlüssel sortiert. Unter {@code properties} sind die
   * Schlüssel Feldnamen und bleiben vollständig — auch ein Feld namens {@code description}.
   */
  private JsonNode normalisiert(JsonNode knoten, boolean feldnamen) {
    if (knoten.isObject()) {
      ObjectNode kopie = json.createObjectNode();
      Map<String, JsonNode> sortiert = new TreeMap<>();
      knoten.properties().forEach(e -> sortiert.put(e.getKey(), e.getValue()));
      sortiert.forEach(
          (schluessel, wert) -> {
            if (feldnamen || !KEIN_VERTRAG.contains(schluessel)) {
              kopie.set(
                  schluessel, normalisiert(wert, !feldnamen && "properties".equals(schluessel)));
            }
          });
      return kopie;
    }
    if (knoten.isArray()) {
      ArrayNode kopie = json.createArrayNode();
      knoten.forEach(e -> kopie.add(normalisiert(e, false)));
      return kopie;
    }
    // Ein fehlender Rumpf steht im Schnappschuss als null; so vergleichen die Knoten gleich.
    return knoten.isMissingNode() ? json.nullNode() : knoten.deepCopy();
  }

  private static void verweise(JsonNode knoten, Set<String> namen) {
    if (knoten.isObject()) {
      JsonNode ref = knoten.path("$ref");
      if (ref.isTextual() && ref.asText().startsWith(REF_PRAEFIX)) {
        namen.add(ref.asText().substring(REF_PRAEFIX.length()));
      }
    }
    knoten.forEach(kind -> verweise(kind, namen));
  }
}
