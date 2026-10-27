package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.web.security.ApiAusprobierFilter;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.mwolff.manban.auth.web.security.VorschauFilter;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Die sieben Szenarien der „Ansicht als XY“ aus der fachlichen Quelle #1910 über die echte
 * Filterkette gegen Postgres (Issue #1922, Plan #1914 E1, E3 bis E9, E12), dazu die Randfälle aus
 * AK 3, 6 und 7: Projekt-Token und zweite Anmeldung des Admins, veraltete Tabs nach dem Ende und
 * das Ausprobieren mit Projekt-Token.
 *
 * <p>Die Vorschau startet wie in der Oberfläche über {@code POST /api/admin/users/{id}/vorschau};
 * jede Anfrage in ihr trägt beide Cookies und die Kopfzeile {@value VorschauFilter#HEADER} mit der
 * Kennung, wie sie {@code api/client.ts} schickt. MockMvc behält keine Cookies: Was der Browser
 * nach einem gelöschten Cookie nicht mehr schickt, lässt der Test weg.
 *
 * <p>Mit stellbarer Uhr ({@link StellbareUhrConfig}) für den Ablauf nach 30 Minuten; denselben
 * Import trägt {@code ApiAusprobierenIT}, beide teilen sich also einen Kontext.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(StellbareUhrConfig.class)
class AdminVorschauIT extends AbstractIntegrationTest {

  private static final String ADMIN = "vorschau-admin@example.com";
  private static final String XY = "vorschau-xy@example.com";
  private static final String OWNER = "vorschau-owner@example.com";
  private static final String TOKEN_HEADER = "X-Kanban-Token";

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;
  @Autowired private ProjectMembershipRepository memberships;

  // --- Szenarien aus #1910 ---------------------------------------------------

  @Test
  @DisplayName("Szenario: Admin prüft die Sicht eines neuen Mitglieds")
  void adminPrueftDieSichtEinesNeuenMitglieds() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long alpha = project(admin, "Alpha");
    project(admin, "Gamma");
    long board = board(admin, alpha, "Alpha-Board");
    card(admin, board, "Erste Karte");
    long xy = mitglied(XY, alpha, ProjectRole.VIEWER);
    Vorschau vorschau = starte(admin, xy);

    JsonNode me = lies(vorschau.in(get("/api/me")));
    assertThat(me.get("userId").asLong()).isEqualTo(xy);
    assertThat(me.get("platformRole").asText()).isEqualTo("USER");
    assertThat(me.get("vorschau").get("kennung").asText()).isEqualTo(vorschau.kennung());
    assertThat(me.get("memberships")).hasSize(1);
    assertThat(me.get("memberships").get(0).get("role").asText()).isEqualTo("VIEWER");

    JsonNode projekte = lies(vorschau.in(get("/api/projects")));
    assertThat(projekte).hasSize(1);
    assertThat(projekte.get(0).get("name").asText()).isEqualTo("Alpha");
    assertThat(projekte.get(0).get("role").asText()).isEqualTo("VIEWER");
    // Dieselben Boards wie für den Admin selbst, samt dem Standard-Board des neuen Projekts.
    String boards = "/api/projects/" + alpha + "/boards";
    assertThat(lies(vorschau.in(get(boards)))).isEqualTo(lies(get(boards).cookie(admin)));
    assertThat(lies(vorschau.in(get("/api/boards/" + board + "/cards")))).hasSize(1);
  }

  @Test
  @DisplayName("Szenario: Veränderungen sind in der Vorschau gesperrt")
  void veraenderungenSindInDerVorschauGesperrt() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long alpha = project(admin, "Alpha");
    JsonNode board = boardMitSpalten(admin, alpha, "Alpha-Board");
    long backlog = board.get("columns").get(0).get("id").asLong();
    long done = board.get("columns").get(4).get("id").asLong();
    long karte = card(admin, board.get("id").asLong(), "Zu verschieben");
    long xy = mitglied(XY, alpha, ProjectRole.MEMBER);
    Vorschau vorschau = starte(admin, xy);

    mvc.perform(
            vorschau.in(
                post("/api/cards/" + karte + "/move")
                    .contentType("application/json")
                    .content("{\"columnId\":%d,\"position\":0}".formatted(done))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("vorschau-nur-lesend"));
    mvc.perform(
            vorschau.in(
                post("/api/cards/" + karte + "/comments")
                    .contentType("application/json")
                    .content("{\"body\":\"Aus der Vorschau\"}")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("vorschau-nur-lesend"));

    assertThat(lies(get("/api/cards/" + karte).cookie(admin)).get("columnId").asLong())
        .isEqualTo(backlog);
    assertThat(lies(get("/api/cards/" + karte + "/comments").cookie(admin))).isEmpty();
  }

  @Test
  @DisplayName("Szenario: Die Einwilligung zum Plattform-Leitstand bleibt gewahrt")
  void dieEinwilligungZumPlattformLeitstandBleibtGewahrt() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    Cookie xySession = session(XY, PlatformRole.USER);
    long beta = project(admin, "Beta", XY);
    jdbc.update("UPDATE project SET dashboard_participation = false WHERE id = ?", beta);
    long xy = anmeldung.konto(XY, PlatformRole.USER).requireId();
    String laufdaten = "/api/projects/" + beta + "/night-run-usage/total";

    mvc.perform(get(laufdaten).cookie(xySession)).andExpect(status().isOk());
    Vorschau vorschau = starte(admin, xy);

    mvc.perform(vorschau.in(get(laufdaten)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("vorschau-verborgen"));
    mvc.perform(vorschau.in(get("/api/projects/" + beta + "/night-runs")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("vorschau-verborgen"));
  }

  @Test
  @DisplayName("Szenario: Persönliches von XY bleibt verborgen")
  void persoenlichesVonXyBleibtVerborgen() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    Vorschau vorschau = starte(admin, konto(XY));

    mvc.perform(vorschau.in(get("/api/access-tokens")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("vorschau-persoenlich"));
    mvc.perform(vorschau.in(get("/api/me/vorschau-protokoll")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("vorschau-persoenlich"));
  }

  @Test
  @DisplayName("Szenario: Die Ansicht endet von selbst")
  void dieAnsichtEndetVonSelbst() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long xy = konto(XY);
    Vorschau vorschau = starte(admin, xy);
    mvc.perform(vorschau.in(get("/api/me"))).andExpect(jsonPath("$.userId").value((int) xy));

    clock.advance(Duration.ofMinutes(31));

    MockHttpServletResponse naechste =
        mvc.perform(vorschau.in(get("/api/projects"))).andReturn().getResponse();
    assertThat(naechste.getStatus()).isEqualTo(409);
    assertThat(json.readTree(naechste.getContentAsString()).get("type").asText())
        .isEqualTo("vorschau-gewechselt");
    assertThat(geloescht(naechste, SessionCookieManager.VORSCHAU_COOKIE_NAME)).isTrue();

    JsonNode me =
        lies(get("/api/me").cookie(admin).header(VorschauFilter.HEADER, VorschauFilter.KEINE));
    assertThat(me.get("email").asText()).isEqualTo(ADMIN);
    assertThat(me.get("vorschau").isNull()).isTrue();

    Map<String, Object> eintrag = eintrag(vorschau);
    assertThat(eintrag.get("endgrund")).isEqualTo("ABLAUF");
    assertThat(eintrag.get("beendet_am")).isEqualTo(eintrag.get("laeuft_ab_am"));
  }

  @Test
  @DisplayName("Szenario: Abmelden beendet beides")
  void abmeldenBeendetBeides() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    Vorschau vorschau = starte(admin, konto(XY));

    MockHttpServletResponse abmelden =
        mvc.perform(vorschau.in(post("/api/auth/logout"))).andReturn().getResponse();

    assertThat(abmelden.getStatus()).isEqualTo(204);
    assertThat(geloescht(abmelden, SessionCookieManager.COOKIE_NAME)).isTrue();
    assertThat(geloescht(abmelden, SessionCookieManager.VORSCHAU_COOKIE_NAME)).isTrue();
    // Der Browser übernimmt beide gelöschten Cookies und schickt danach nur noch diese mit.
    Cookie leereSitzung = new Cookie(SessionCookieManager.COOKIE_NAME, "");
    Cookie leereVorschau = new Cookie(SessionCookieManager.VORSCHAU_COOKIE_NAME, "");
    mvc.perform(get("/api/me").cookie(leereSitzung, leereVorschau))
        .andExpect(status().isUnauthorized());
    // Auch ein zurückbehaltenes Vorschau-Cookie gilt ohne Sitzung nicht (E3).
    mvc.perform(get("/api/me").cookie(vorschau.cookie())).andExpect(status().isUnauthorized());
    assertThat(eintrag(vorschau).get("endgrund")).isEqualTo("ABMELDUNG");
  }

  @Test
  @DisplayName("Szenario: XY erfährt von der Ansicht")
  void xyErfaehrtVonDerAnsicht() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    Cookie xySession = session(XY, PlatformRole.USER);
    long xy = konto(XY);
    Vorschau vorschau = starte(admin, xy);
    mvc.perform(vorschau.in(delete("/api/vorschau"))).andExpect(status().isNoContent());

    JsonNode eigene = lies(get("/api/me/vorschau-protokoll").cookie(xySession));
    assertThat(eigene).hasSize(1);
    assertThat(eigene.get(0).get("adminName").asText()).isEqualTo("Person");
    assertThat(eigene.get(0).get("adminKontoGeloescht").asBoolean()).isFalse();
    assertThat(eigene.get(0).get("gestartetAm").isNull()).isFalse();
    assertThat(eigene.get(0).get("beendetAm").isNull()).isFalse();
    assertThat(eigene.get(0).get("endgrund").asText()).isEqualTo("BEENDET");

    JsonNode beimAdmin = lies(get("/api/admin/users/" + xy + "/vorschau-protokoll").cookie(admin));
    assertThat(beimAdmin).isEqualTo(eigene);
    mvc.perform(get("/api/admin/users/" + xy + "/vorschau-protokoll").cookie(xySession))
        .andExpect(status().isForbidden());
  }

  // --- Zusatzfälle -------------------------------------------------------------

  /** AK 6: Projekt-Token und eine zweite Anmeldung des Admins bleiben unberührt. */
  @Test
  void projektTokenUndZweiteAnmeldungBleibenUnberuehrt() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long alpha = project(admin, "Alpha");
    long board = board(admin, alpha, "Alpha-Board");
    long karte = card(admin, board, "Kommentierbar");
    String token = token(admin, alpha, board);
    Vorschau vorschau = starte(admin, konto(XY));
    Cookie zweite = session(ADMIN, PlatformRole.ADMIN);

    mvc.perform(
            post("/api/kanban/items")
                .cookie(vorschau.cookie())
                .header(TOKEN_HEADER, token)
                .contentType("application/json")
                .content("{\"title\":\"Per Token\",\"direct\":true}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/cards/" + karte + "/comments")
                .cookie(zweite)
                .contentType("application/json")
                .content("{\"body\":\"Aus der zweiten Anmeldung\"}"))
        .andExpect(status().isCreated());
    JsonNode me = lies(get("/api/me").cookie(zweite));
    assertThat(me.get("email").asText()).isEqualTo(ADMIN);
    assertThat(me.get("vorschau").isNull()).isTrue();

    assertThat(lies(vorschau.in(get("/api/me"))).get("vorschau").get("kennung").asText())
        .isEqualTo(vorschau.kennung());
  }

  /**
   * Issue #1940: Nach dem Start lädt die Oberfläche neu, und der frische Tab kennt die Kennung noch
   * nicht. Seine Selbstauskunft ohne Kopfzeile nennt sie ihm, statt mit 409 ein weiteres Neuladen
   * auszulösen.
   */
  @Test
  void frischGeladenerTabErfaehrtDieKennungAusDerSelbstauskunft() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long alpha = project(admin, "Alpha");
    long xy = mitglied(XY, alpha, ProjectRole.VIEWER);
    Vorschau vorschau = starte(admin, xy);

    JsonNode me = lies(get("/api/me").cookie(vorschau.sitzung(), vorschau.cookie()));
    assertThat(me.get("userId").asLong()).isEqualTo(xy);
    String kennung = me.get("vorschau").get("kennung").asText();
    assertThat(kennung).isEqualTo(vorschau.kennung());

    JsonNode projekte =
        lies(
            get("/api/projects")
                .cookie(vorschau.sitzung(), vorschau.cookie())
                .header(VorschauFilter.HEADER, kennung));
    assertThat(projekte).hasSize(1);
    mvc.perform(
            get("/api/projects")
                .cookie(vorschau.sitzung(), vorschau.cookie())
                .header(VorschauFilter.HEADER, VorschauFilter.KEINE))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("vorschau-gewechselt"));
  }

  /**
   * Issue #1940: Noch vor der Selbstauskunft fragt der frisch geladene Tab die Sprache der
   * Installation ab. Ohne Kennung bekommt er sie, statt mit 409 ein weiteres Neuladen auszulösen.
   */
  @Test
  void frischGeladenerTabBekommtDieSpracheOhneKennung() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long alpha = project(admin, "Alpha");
    long xy = mitglied(XY, alpha, ProjectRole.VIEWER);
    Vorschau vorschau = starte(admin, xy);

    mvc.perform(
            get("/api/language")
                .cookie(vorschau.sitzung(), vorschau.cookie())
                .header(VorschauFilter.HEADER, VorschauFilter.KEINE))
        .andExpect(status().isOk());
  }

  /** AK 7: Ein Änderungsaufruf aus einem alten Vorschau-Tab läuft nach dem Ende nie als Admin. */
  @Test
  void aenderungMitVeralteterKopfzeileNachDemEndeErgibt409() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long alpha = project(admin, "Alpha");
    long karte = card(admin, board(admin, alpha, "Alpha-Board"), "Unberührt");
    Vorschau vorschau = starte(admin, konto(XY));
    mvc.perform(vorschau.in(delete("/api/vorschau"))).andExpect(status().isNoContent());

    mvc.perform(
            post("/api/cards/" + karte + "/comments")
                .cookie(admin)
                .header(VorschauFilter.HEADER, vorschau.kennung())
                .contentType("application/json")
                .content("{\"body\":\"Aus dem alten Tab\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("vorschau-gewechselt"));

    assertThat(lies(get("/api/cards/" + karte + "/comments").cookie(admin))).isEmpty();
  }

  /** AK 7, E8: Hochladen und Löschen eines Anhangs aus einem alten Vorschau-Tab ergeben 409. */
  @Test
  void anhangAusAltemVorschauTabNachDemEndeErgibt409() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long alpha = project(admin, "Alpha");
    long karte = card(admin, board(admin, alpha, "Alpha-Board"), "Mit Anhang");
    long anhang = anhang(admin, karte, "vorher.txt");
    Vorschau vorschau = starte(admin, konto(XY));
    clock.advance(Duration.ofMinutes(31));

    mvc.perform(
            multipart("/api/cards/" + karte + "/attachments")
                .file(datei("nachher.txt"))
                .cookie(admin, vorschau.cookie())
                .header(VorschauFilter.HEADER, vorschau.kennung()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("vorschau-gewechselt"));
    mvc.perform(
            delete("/api/attachments/" + anhang)
                .cookie(admin)
                .header(VorschauFilter.HEADER, vorschau.kennung()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.type").value("vorschau-gewechselt"));

    JsonNode anhaenge = lies(get("/api/cards/" + karte + "/attachments").cookie(admin));
    assertThat(anhaenge).hasSize(1);
    assertThat(anhaenge.get(0).get("id").asLong()).isEqualTo(anhang);
  }

  /** AK 3, E7: Ausprobieren ist in der Vorschau auch mit Projekt-Token gesperrt. */
  @Test
  void ausprobierenInDerVorschauMitProjektTokenErgibt403() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long alpha = project(admin, "Alpha");
    long board = board(admin, alpha, "Alpha-Board");
    String token = token(admin, alpha, board);
    Vorschau vorschau = starte(admin, konto(XY));

    mvc.perform(
            vorschau
                .in(get("/api/kanban/items"))
                .header(ApiAusprobierFilter.HEADER, "1")
                .header(TOKEN_HEADER, token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("vorschau-nur-lesend"));
  }

  // --- Fixtures ----------------------------------------------------------------

  /** Eine laufende Vorschau, wie sie ein Tab kennt: Cookie und Kennung für die Kopfzeile. */
  private record Vorschau(Cookie sitzung, Cookie cookie, String kennung) {

    MockHttpServletRequestBuilder in(MockHttpServletRequestBuilder anfrage) {
      return anfrage.cookie(sitzung, cookie).header(VorschauFilter.HEADER, kennung);
    }
  }

  private Vorschau starte(Cookie admin, long zielId) throws Exception {
    MockHttpServletResponse antwort =
        mvc.perform(post("/api/admin/users/" + zielId + "/vorschau").cookie(admin))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    Cookie cookie =
        Objects.requireNonNull(
            antwort.getCookie(SessionCookieManager.VORSCHAU_COOKIE_NAME),
            "Der Start setzt kein Vorschau-Cookie");
    String kennung = cookie.getValue().substring(0, cookie.getValue().indexOf('.'));
    return new Vorschau(admin, cookie, kennung);
  }

  private Map<String, Object> eintrag(Vorschau vorschau) {
    Map<String, Object> zeile =
        jdbc.queryForMap(
            "SELECT endgrund, beendet_am, laeuft_ab_am FROM admin_vorschau WHERE kennung = ?",
            vorschau.kennung());
    zeile.computeIfPresent("beendet_am", (k, v) -> ((Timestamp) v).toInstant());
    zeile.computeIfPresent("laeuft_ab_am", (k, v) -> ((Timestamp) v).toInstant());
    assertThat(zeile.get("beendet_am")).isInstanceOf(Instant.class);
    return zeile;
  }

  private static boolean geloescht(MockHttpServletResponse antwort, String name) {
    Cookie cookie = antwort.getCookie(name);
    return cookie != null && cookie.getMaxAge() == 0;
  }

  private JsonNode lies(MockHttpServletRequestBuilder anfrage) throws Exception {
    return json.readTree(
        mvc.perform(anfrage)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode erzeugt(MockHttpServletRequestBuilder anfrage) throws Exception {
    return json.readTree(
        mvc.perform(anfrage)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private Cookie session(String email, PlatformRole rolle) {
    return anmeldung.anmelden(email, rolle);
  }

  private long konto(String email) {
    return anmeldung.konto(email, PlatformRole.USER).requireId();
  }

  /**
   * XY als Mitglied mit der Rolle, direkt im Speicher: Hier geht es um die Sicht, nicht ums
   * Einladen.
   */
  private long mitglied(String email, long projectId, ProjectRole rolle) {
    long userId = konto(email);
    memberships.save(new ProjectMembership(null, projectId, userId, rolle, Instant.now()));
    return userId;
  }

  private long project(Cookie admin, String name) throws Exception {
    konto(OWNER);
    return project(admin, name, OWNER);
  }

  private long project(Cookie admin, String name, String ownerEmail) throws Exception {
    return erzeugt(
            post("/api/projects")
                .cookie(admin)
                .contentType("application/json")
                .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, ownerEmail)))
        .get("id")
        .asLong();
  }

  private JsonNode boardMitSpalten(Cookie admin, long projectId, String name) throws Exception {
    return erzeugt(
        post("/api/projects/" + projectId + "/boards")
            .cookie(admin)
            .contentType("application/json")
            .content("{\"name\":\"%s\"}".formatted(name)));
  }

  private long board(Cookie admin, long projectId, String name) throws Exception {
    return boardMitSpalten(admin, projectId, name).get("id").asLong();
  }

  /** Eine Karte in der ersten Spalte des Boards. */
  private long card(Cookie admin, long boardId, String title) throws Exception {
    long spalte =
        lies(get("/api/boards/" + boardId).cookie(admin)).get("columns").get(0).get("id").asLong();
    return erzeugt(
            post("/api/boards/" + boardId + "/cards")
                .cookie(admin)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(spalte, title)))
        .get("id")
        .asLong();
  }

  private String token(Cookie admin, long projectId, long boardId) throws Exception {
    return erzeugt(
            post("/api/access-tokens")
                .cookie(admin)
                .contentType("application/json")
                .content(
                    "{\"name\":\"Vorschau-Token\",\"projectId\":%d,\"boardId\":%d}"
                        .formatted(projectId, boardId)))
        .get("plaintext")
        .asText();
  }

  private long anhang(Cookie admin, long cardId, String name) throws Exception {
    return erzeugt(
            multipart("/api/cards/" + cardId + "/attachments").file(datei(name)).cookie(admin))
        .get("id")
        .asLong();
  }

  private static MockMultipartFile datei(String name) {
    return new MockMultipartFile(
        "file", name, "text/plain", "Inhalt".getBytes(StandardCharsets.UTF_8));
  }
}
