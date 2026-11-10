package org.mwolff.manban.accesstoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
import org.mwolff.manban.TransactionRace;
import org.mwolff.manban.accesstoken.application.AccessTokenService;
import org.mwolff.manban.accesstoken.application.AppZugangBestehtException;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.mwolff.manban.auth.web.security.VorschauFilter;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Verwaltung des Zugangs der App über die echten Filter (Issue #2103, Plan #2100 E5 bis E8, E12):
 * Anlegen, Erneuern, Widerrufen und Liste unter {@code /api/access-tokens/app}. Dazu die beiden
 * Freigaben des Zugangs und ihr Eintrag im Verlauf (Issue #2106, E2, E3, E9).
 */
// PMD.ExcessiveImports, PMD.CouplingBetweenObjects, PMD.TooManyMethods: Issue #2104 verlangt die
// Verlustfälle hier, neben den Szenarien der Verwaltung. Sie laufen über dieselben echten Filter
// und Fixtures; ein zweites IT trennte die Szenarien eines Zugangs voneinander.
// PMD.GodClass: Issue #2106 verlangt auch die Szenarien der beiden Freigaben hier; die Klasse ist
// eine Reihe unabhängiger Szenarien mit Request-Helfern, kein Baustein mit vermischter
// Zuständigkeit.
@SuppressWarnings({
  "PMD.ExcessiveImports",
  "PMD.CouplingBetweenObjects",
  "PMD.TooManyMethods",
  "PMD.GodClass"
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AppZugangIT extends AbstractIntegrationTest {

  private static final String PFAD = "/api/access-tokens/app";
  private static final String JSON = "application/json";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String KARTEN_PFAD = "/api/kanban/items";
  private static final String INHABER_NAME = "Erika Inhaber";
  private static final String PLAINTEXT = "plaintext";
  private static final String ADMIN_EMAIL = "admin@example.com";
  private static final Duration KURZER_POOL_TIMEOUT = Duration.ofSeconds(2);
  private static final String MITGLIED_NAME = "Mia Mitglied";
  private static final String APP_NAME = "Desktop-App";
  private static final String LABELS = "/labels";
  private static final String DETAIL = "$.detail";
  private static final String NUR_EIN_MENSCH = "nur ein Mensch";
  private static final String NIGHT = "kit:night";
  private static final String NIGHTRUN = "kit:nightrun";
  private static final String KLAEREN = "kit:klaeren";
  private static final String CLARIFY = "kit:clarify";
  private static final String GESCHUETZT = "kit:geschuetzt";

  /** Ein neues Projekt bringt schon Boards mit; die Zeile wird darum über die Board-ID gesucht. */
  private static final String ZEILE_DES_BOARDS = "$[?(@.boardId == %d)]";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private AccessTokenService accessTokens;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  /**
   * Ein Projekt mit einem Board und je einer Person jeder Rolle, die die Verwaltung unterscheidet.
   */
  private record Projekt(long id, long board, Cookie inhaber, Cookie admin, Cookie mitglied) {}

  private Cookie person(String email, String name) {
    AppUser konto =
        users
            .findByEmail(email)
            .orElseGet(() -> users.save(new AppUser(null, email, name, true, PlatformRole.USER)));
    return anmeldung.anmelden(konto.email(), PlatformRole.USER);
  }

  private Cookie plattformAdmin() {
    return anmeldung.anmelden("plattform-admin@example.com", PlatformRole.ADMIN);
  }

  private void mitglied(long projektId, String email, ProjectRole rolle) {
    long userId = users.findByEmail(email).orElseThrow().requireId();
    memberships.save(new ProjectMembership(null, projektId, userId, rolle, Instant.now()));
  }

  private long idAus(ResultActions antwort) throws Exception {
    return json.readTree(antwort.andReturn().getResponse().getContentAsString()).get("id").asLong();
  }

  private long board(Cookie session, long projektId, String name) throws Exception {
    return idAus(
        mvc.perform(
                post("/api/projects/" + projektId + "/boards")
                    .cookie(session)
                    .contentType(JSON)
                    .content("{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated()));
  }

  /** Die Inhaberin des Zugangs ist Owner des Projekts, daneben gibt es Admin und Mitglied. */
  private Projekt projekt() throws Exception {
    String owner = "inhaber@example.com";
    Cookie inhaber = person(owner, INHABER_NAME);
    long id =
        idAus(
            mvc.perform(
                    post("/api/projects")
                        .cookie(plattformAdmin())
                        .contentType(JSON)
                        .content("{\"name\":\"Alpha\",\"ownerEmail\":\"%s\"}".formatted(owner)))
                .andExpect(status().isCreated()));
    Cookie admin = person("admin@example.com", "Adam Admin");
    mitglied(id, "admin@example.com", ProjectRole.ADMIN);
    Cookie mitglied = person("mitglied@example.com", MITGLIED_NAME);
    mitglied(id, "mitglied@example.com", ProjectRole.MEMBER);
    return new Projekt(id, board(inhaber, id, "Entwicklung"), inhaber, admin, mitglied);
  }

  private ResultActions anlegen(Cookie session, Projekt projekt) throws Exception {
    return mvc.perform(
        post(PFAD)
            .cookie(session)
            .contentType(JSON)
            .content("{\"projectId\":%d,\"boardId\":%d}".formatted(projekt.id(), projekt.board())));
  }

  private JsonNode angelegt(Cookie session, Projekt projekt) throws Exception {
    String body =
        anlegen(session, projekt)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private ResultActions kartenAbrufen(String klartext) throws Exception {
    return mvc.perform(get(KARTEN_PFAD).header(TOKEN_HEADER, klartext));
  }

  private ResultActions erneuern(Cookie session, long tokenId) throws Exception {
    return mvc.perform(post(PFAD + "/" + tokenId + "/erneuern").cookie(session));
  }

  private ResultActions widerrufen(Cookie session, long tokenId) throws Exception {
    return mvc.perform(delete(PFAD + "/" + tokenId).cookie(session));
  }

  private int wirksameZugaenge(long boardId) {
    return Objects.requireNonNull(
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT count(*) FROM kanban_access_token"
                    + " WHERE board_id = ? AND art = 'APP' AND revoked = false",
                Integer.class,
                boardId));
  }

  private long userId(String email) {
    return users.findByEmail(email).orElseThrow().requireId();
  }

  private String mitgliedPfad(Projekt projekt, String email) {
    return "/api/projects/" + projekt.id() + "/members/" + userId(email);
  }

  private void rolleSetzen(Projekt projekt, String email, ProjectRole rolle) throws Exception {
    mvc.perform(
            patch(mitgliedPfad(projekt, email))
                .cookie(projekt.inhaber())
                .contentType(JSON)
                .content("{\"role\":\"%s\"}".formatted(rolle)))
        .andExpect(status().isOk());
  }

  /** Ein weiteres Projekt derselben Owner-Person, mit eigenem Board. */
  private Projekt zweitesProjektDesInhabers(Projekt projekt) throws Exception {
    long id =
        idAus(
            mvc.perform(
                    post("/api/projects")
                        .cookie(plattformAdmin())
                        .contentType(JSON)
                        .content("{\"name\":\"Beta\",\"ownerEmail\":\"inhaber@example.com\"}"))
                .andExpect(status().isCreated()));
    return new Projekt(
        id,
        board(projekt.inhaber(), id, "Zweites"),
        projekt.inhaber(),
        projekt.admin(),
        projekt.mitglied());
  }

  /**
   * Belegt alle Verbindungen des Pools bis auf eine und ruft dann auf. Wie in {@code
   * AccessTokenLastUsedThrottleIT}: Hielte die Auflösung ihre Verbindung über die Rollenprüfung
   * hinweg, liefe die zweite Anforderung in den kurzen Timeout (Issue #997).
   */
  private <T> T mitEinerFreienVerbindung(Supplier<T> aufruf) throws SQLException {
    HikariConfigMXBean pool = dataSource.unwrap(HikariDataSource.class).getHikariConfigMXBean();
    long bisherigerTimeout = pool.getConnectionTimeout();
    pool.setConnectionTimeout(KURZER_POOL_TIMEOUT.toMillis());
    try (BelegteVerbindungen belegt = new BelegteVerbindungen()) {
      for (int i = 0; i < pool.getMaximumPoolSize() - 1; i++) {
        belegt.verbindungen.add(dataSource.getConnection());
      }
      return aufruf.get();
    } finally {
      pool.setConnectionTimeout(bisherigerTimeout);
    }
  }

  /** Die belegten Verbindungen als eine Ressource, damit try-with-resources sie freigibt. */
  private static final class BelegteVerbindungen implements AutoCloseable {

    private final List<Connection> verbindungen = new ArrayList<>();

    @Override
    // PMD.CloseResource: Fehlalarm — die Schleifenvariable ist die Verbindung, die hier schließt.
    @SuppressWarnings("PMD.CloseResource")
    public void close() throws SQLException {
      for (Connection verbindung : verbindungen) {
        verbindung.close();
      }
    }
  }

  // --- Szenarien ------------------------------------------------------------

  @Test
  @DisplayName("Szenario: Am Board besteht schon ein Zugang")
  void amBoardBestehtSchonEinZugang() throws Exception {
    // Given
    Projekt projekt = projekt();
    angelegt(projekt.inhaber(), projekt);

    // When / Then
    anlegen(projekt.admin(), projekt)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", containsString(INHABER_NAME)));
    assertThat(wirksameZugaenge(projekt.board())).isEqualTo(1);
  }

  @Test
  @DisplayName("Szenario: Der Zugang wird erneuert")
  void derZugangWirdErneuert() throws Exception {
    // Given
    Projekt projekt = projekt();
    JsonNode alt = angelegt(projekt.inhaber(), projekt);

    // When
    String body =
        erneuern(projekt.inhaber(), alt.get("id").asLong())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.plaintext").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then
    String neuerKlartext = json.readTree(body).get(PLAINTEXT).asText();
    assertThat(neuerKlartext).isNotEqualTo(alt.get(PLAINTEXT).asText());
    kartenAbrufen(neuerKlartext).andExpect(status().isOk());
    kartenAbrufen(alt.get(PLAINTEXT).asText()).andExpect(status().isUnauthorized());
    assertThat(wirksameZugaenge(projekt.board())).isEqualTo(1);
  }

  @Test
  @DisplayName("Szenario: Ein anderer Admin widerruft den Zugang")
  void einAndererAdminWiderruftDenZugang() throws Exception {
    // Given
    Projekt projekt = projekt();
    JsonNode zugang = angelegt(projekt.inhaber(), projekt);
    long tokenId = zugang.get("id").asLong();

    // When
    widerrufen(projekt.admin(), tokenId).andExpect(status().isNoContent());

    // Then: unwirksam, nicht wieder zu aktivieren, und das Board ist sofort wieder frei.
    kartenAbrufen(zugang.get(PLAINTEXT).asText()).andExpect(status().isUnauthorized());
    erneuern(projekt.inhaber(), tokenId).andExpect(status().isNotFound());
    kartenAbrufen(zugang.get(PLAINTEXT).asText()).andExpect(status().isUnauthorized());
    JsonNode neu = angelegt(projekt.admin(), projekt);
    kartenAbrufen(neu.get(PLAINTEXT).asText()).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Szenario: Der Inhaber verlässt das Projekt")
  void derInhaberVerlaesstDasProjekt() throws Exception {
    // Given
    Projekt projekt = projekt();
    String klartext = angelegt(projekt.admin(), projekt).get(PLAINTEXT).asText();

    // When
    mvc.perform(delete(mitgliedPfad(projekt, ADMIN_EMAIL)).cookie(projekt.inhaber()))
        .andExpect(status().isNoContent());

    // Then: schon ohne einen Aufruf widerrufen, und jeder Aufruf wird abgewiesen.
    assertThat(wirksameZugaenge(projekt.board())).isZero();
    kartenAbrufen(klartext).andExpect(status().isUnauthorized());
    JsonNode neu = angelegt(projekt.inhaber(), projekt);
    kartenAbrufen(neu.get(PLAINTEXT).asText()).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Szenario: Der Inhaber wird herabgestuft und bekommt seine Rolle zurück")
  void derInhaberWirdHerabgestuftUndBekommtSeineRolleZurueck() throws Exception {
    // Given
    Projekt projekt = projekt();
    String klartext = angelegt(projekt.admin(), projekt).get(PLAINTEXT).asText();

    // When: zwischen Verlust und Rückgabe der Rolle kommt kein Aufruf.
    rolleSetzen(projekt, ADMIN_EMAIL, ProjectRole.MEMBER);
    rolleSetzen(projekt, ADMIN_EMAIL, ProjectRole.ADMIN);

    // Then
    assertThat(wirksameZugaenge(projekt.board())).isZero();
    kartenAbrufen(klartext).andExpect(status().isUnauthorized());
    JsonNode neu = angelegt(projekt.admin(), projekt);
    kartenAbrufen(neu.get(PLAINTEXT).asText()).andExpect(status().isOk());
  }

  // --- Verlust von Rolle oder Konto -----------------------------------------

  @Test
  void einGesperrtesUndWiederEntsperrtesKontoBleibtOhneZugang() throws Exception {
    // Given
    Projekt projekt = projekt();
    String klartext = angelegt(projekt.admin(), projekt).get(PLAINTEXT).asText();
    long admin = userId(ADMIN_EMAIL);
    Cookie plattform = plattformAdmin();

    // When
    mvc.perform(post("/api/admin/users/" + admin + "/disable").cookie(plattform))
        .andExpect(status().isOk());
    mvc.perform(post("/api/admin/users/" + admin + "/enable").cookie(plattform))
        .andExpect(status().isOk());

    // Then
    assertThat(wirksameZugaenge(projekt.board())).isZero();
    kartenAbrufen(klartext).andExpect(status().isUnauthorized());
  }

  @Test
  void beimUebergebenDerEigentuemerschaftBleibtDerZugangWirksam() throws Exception {
    // Given
    Projekt projekt = projekt();
    String klartext = angelegt(projekt.inhaber(), projekt).get(PLAINTEXT).asText();

    // When: Die bisherige Owner-Person bleibt danach Admin.
    mvc.perform(
            post("/api/projects/" + projekt.id() + "/owner")
                .cookie(projekt.inhaber())
                .contentType(JSON)
                .content("{\"newOwnerUserId\":%d}".formatted(userId(ADMIN_EMAIL))))
        .andExpect(status().is2xxSuccessful());

    // Then
    assertThat(wirksameZugaenge(projekt.board())).isEqualTo(1);
    kartenAbrufen(klartext).andExpect(status().isOk());
  }

  @Test
  void dasHerabstufenInEinemProjektLaesstDenZugangEinesAnderenProjektsStehen() throws Exception {
    // Given: Dieselbe Person ist Admin in zwei Projekten, ein Zugang je Projekt.
    Projekt projekt = projekt();
    Projekt anderes = zweitesProjektDesInhabers(projekt);
    String klartext = angelegt(projekt.admin(), projekt).get(PLAINTEXT).asText();
    mitglied(anderes.id(), ADMIN_EMAIL, ProjectRole.ADMIN);
    String andererKlartext = angelegt(projekt.admin(), anderes).get(PLAINTEXT).asText();

    // When
    rolleSetzen(projekt, ADMIN_EMAIL, ProjectRole.VIEWER);

    // Then
    kartenAbrufen(klartext).andExpect(status().isUnauthorized());
    kartenAbrufen(andererKlartext).andExpect(status().isOk());
  }

  @Test
  void einRollenverlustAmDienstVorbeiWirdBeimAufrufEndgueltig() throws Exception {
    // Given: Die Rolle ändert sich ohne Ereignis, wie bei einem künftig vergessenen Schreibweg.
    Projekt projekt = projekt();
    String klartext = angelegt(projekt.admin(), projekt).get(PLAINTEXT).asText();
    ProjectMembership vorher =
        memberships.findByProjectIdAndUserId(projekt.id(), userId(ADMIN_EMAIL)).orElseThrow();
    memberships.save(vorher.withRole(ProjectRole.MEMBER));

    // When
    kartenAbrufen(klartext).andExpect(status().isUnauthorized());
    memberships.save(vorher);

    // Then
    assertThat(wirksameZugaenge(projekt.board())).isZero();
    kartenAbrufen(klartext).andExpect(status().isUnauthorized());
  }

  @Test
  void dieAufloesungEinesZugangsKommtMitEinerFreienVerbindungAus() throws Exception {
    // Given: Die Kennung ist neu, damit die Drossel im Speicher den Stempel nicht unterdrückt.
    Projekt projekt = projekt();
    new JdbcTemplate(dataSource)
        .queryForObject(
            "SELECT setval(pg_get_serial_sequence('kanban_access_token', 'id'), 1000000)",
            Long.class);
    JsonNode zugang = angelegt(projekt.inhaber(), projekt);

    // When
    Optional<KanbanPrincipal> aufgeloest =
        mitEinerFreienVerbindung(() -> accessTokens.resolveBinding(zugang.get(PLAINTEXT).asText()));

    // Then: Rolle gelesen und Nutzung gestempelt, nacheinander auf derselben Verbindung.
    assertThat(aufgeloest).isPresent();
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForObject(
                    "SELECT last_used_at IS NOT NULL FROM kanban_access_token WHERE id = ?",
                    Boolean.class,
                    zugang.get("id").asLong()))
        .isTrue();
  }

  @Test
  void derWiderrufBeimAufrufKommtMitEinerFreienVerbindungAus() throws Exception {
    // Given
    Projekt projekt = projekt();
    String klartext = angelegt(projekt.admin(), projekt).get(PLAINTEXT).asText();
    long admin = userId(ADMIN_EMAIL);
    memberships.deleteById(
        memberships.findByProjectIdAndUserId(projekt.id(), admin).orElseThrow().requireId());

    // When
    Optional<KanbanPrincipal> aufgeloest =
        mitEinerFreienVerbindung(() -> accessTokens.resolveBinding(klartext));

    // Then
    assertThat(aufgeloest).isEmpty();
    assertThat(wirksameZugaenge(projekt.board())).isZero();
  }

  // --- Rechtegrenzen --------------------------------------------------------

  @Test
  void anlegen_weistEinMitgliedUnterhalbVonAdminAb() throws Exception {
    Projekt projekt = projekt();

    anlegen(projekt.mitglied(), projekt).andExpect(status().isForbidden());
    assertThat(wirksameZugaenge(projekt.board())).isZero();
  }

  @Test
  void anlegen_weistEinenPlattformAdminOhneProjektrolleAb() throws Exception {
    Projekt projekt = projekt();

    anlegen(plattformAdmin(), projekt).andExpect(status().isNotFound());
    assertThat(wirksameZugaenge(projekt.board())).isZero();
  }

  @Test
  void anlegen_gibtDenFestenNamenUndDenInhaberAlsAufrufer() throws Exception {
    Projekt projekt = projekt();

    anlegen(projekt.admin(), projekt)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value(APP_NAME));

    mvc.perform(get(PFAD).cookie(projekt.admin()))
        .andExpect(jsonPath(ZEILE_DES_BOARDS + ".inhaberName", projekt.board()).value("Adam Admin"))
        .andExpect(jsonPath(ZEILE_DES_BOARDS + ".eigener", projekt.board()).value(true));
  }

  @Test
  void anlegen_weistEinBoardEinesAnderenProjektsAb() throws Exception {
    Projekt projekt = projekt();
    long fremdesProjekt =
        idAus(
            mvc.perform(
                    post("/api/projects")
                        .cookie(plattformAdmin())
                        .contentType(JSON)
                        .content("{\"name\":\"Beta\",\"ownerEmail\":\"inhaber@example.com\"}"))
                .andExpect(status().isCreated()));
    long fremdesBoard = board(projekt.inhaber(), fremdesProjekt, "Fremd");

    anlegen(
            projekt.inhaber(),
            new Projekt(
                projekt.id(), fremdesBoard, projekt.inhaber(), projekt.admin(), projekt.mitglied()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void erneuern_weistEinenAnderenAdminAb() throws Exception {
    Projekt projekt = projekt();
    JsonNode zugang = angelegt(projekt.inhaber(), projekt);

    erneuern(projekt.admin(), zugang.get("id").asLong()).andExpect(status().isForbidden());
    kartenAbrufen(zugang.get(PLAINTEXT).asText()).andExpect(status().isOk());
  }

  @Test
  void widerrufen_weistEinMitgliedUnterhalbVonAdminAb() throws Exception {
    Projekt projekt = projekt();
    JsonNode zugang = angelegt(projekt.inhaber(), projekt);

    widerrufen(projekt.mitglied(), zugang.get("id").asLong()).andExpect(status().isForbidden());
    kartenAbrufen(zugang.get(PLAINTEXT).asText()).andExpect(status().isOk());
  }

  @Test
  void aufrufMitTokenStattSitzungWirdAbgewiesen() throws Exception {
    Projekt projekt = projekt();
    String klartext = angelegt(projekt.inhaber(), projekt).get(PLAINTEXT).asText();

    mvc.perform(get(PFAD).header(TOKEN_HEADER, klartext)).andExpect(status().isForbidden());
    mvc.perform(
            post(PFAD)
                .header(TOKEN_HEADER, klartext)
                .contentType(JSON)
                .content(
                    "{\"projectId\":%d,\"boardId\":%d}".formatted(projekt.id(), projekt.board())))
        .andExpect(status().isForbidden());
  }

  @Test
  void listeInDerVorschauIstPersoenlich() throws Exception {
    // Given
    Cookie admin = plattformAdmin();
    person("xy@example.com", "Xaver Y");
    long xy = users.findByEmail("xy@example.com").orElseThrow().requireId();
    var start =
        mvc.perform(post("/api/admin/users/" + xy + "/vorschau").cookie(admin))
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse();
    Cookie vorschau =
        Objects.requireNonNull(start.getCookie(SessionCookieManager.VORSCHAU_COOKIE_NAME));
    String kennung = vorschau.getValue().substring(0, vorschau.getValue().indexOf('.'));

    // When / Then
    mvc.perform(get(PFAD).cookie(admin, vorschau).header(VorschauFilter.HEADER, kennung))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("vorschau-persoenlich"));
  }

  // --- Liste ----------------------------------------------------------------

  @Test
  void liste_nenntZugangUndLeeresBoardOhneKlartext() throws Exception {
    // Given
    Projekt projekt = projekt();
    long leeresBoard = board(projekt.inhaber(), projekt.id(), "Ohne Zugang");
    JsonNode zugang = angelegt(projekt.inhaber(), projekt);

    // When
    String body =
        mvc.perform(get(PFAD).cookie(projekt.admin()))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(ZEILE_DES_BOARDS + ".tokenId", projekt.board())
                    .value(zugang.get("id").asInt()))
            .andExpect(jsonPath(ZEILE_DES_BOARDS + ".projectName", projekt.board()).value("Alpha"))
            .andExpect(
                jsonPath(ZEILE_DES_BOARDS + ".boardName", projekt.board()).value("Entwicklung"))
            .andExpect(
                jsonPath(ZEILE_DES_BOARDS + ".inhaberName", projekt.board()).value(INHABER_NAME))
            .andExpect(jsonPath(ZEILE_DES_BOARDS + ".eigener", projekt.board()).value(false))
            .andExpect(jsonPath(ZEILE_DES_BOARDS + ".createdAt", projekt.board()).exists())
            .andExpect(
                jsonPath(ZEILE_DES_BOARDS + ".tokenId", leeresBoard).value(contains(nullValue())))
            .andExpect(
                jsonPath(ZEILE_DES_BOARDS + ".inhaberName", leeresBoard)
                    .value(contains(nullValue())))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then
    assertThat(body).doesNotContain(zugang.get(PLAINTEXT).asText()).doesNotContain(PLAINTEXT);
  }

  @Test
  void liste_istOhneAdminOderOwnerRolleLeer() throws Exception {
    Projekt projekt = projekt();
    angelegt(projekt.inhaber(), projekt);

    mvc.perform(get(PFAD).cookie(projekt.mitglied()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get(PFAD).cookie(person("fremd@example.com", "Fremd")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void liste_zeigtDemPlattformAdminOhneProjektrolleNichts() throws Exception {
    Projekt projekt = projekt();
    angelegt(projekt.inhaber(), projekt);

    mvc.perform(get(PFAD).cookie(plattformAdmin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void persoenlicheListeZeigtDenZugangNicht() throws Exception {
    Projekt projekt = projekt();
    angelegt(projekt.inhaber(), projekt);

    mvc.perform(get("/api/access-tokens").cookie(projekt.inhaber()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  // --- Die beiden Freigaben (Issue #2106, Plan #2100 E2, E3, E9) ------------

  private String zugang(Projekt projekt) throws Exception {
    return angelegt(projekt.inhaber(), projekt).get(PLAINTEXT).asText();
  }

  private String gewoehnlicherZugang(Cookie session, long projektId, long boardId)
      throws Exception {
    String body =
        mvc.perform(
                post("/api/access-tokens")
                    .cookie(session)
                    .contentType(JSON)
                    .content(
                        "{\"name\":\"Nachtlauf\",\"projectId\":%d,\"boardId\":%d}"
                            .formatted(projektId, boardId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get(PLAINTEXT).asText();
  }

  private long label(Cookie session, long boardId, String name) throws Exception {
    return idAus(
        mvc.perform(
                post("/api/boards/" + boardId + "/labels")
                    .cookie(session)
                    .contentType(JSON)
                    .content("{\"name\":\"%s\",\"color\":\"#ff0000\"}".formatted(name)))
            .andExpect(status().isCreated()));
  }

  private long karte(String klartext, String titel) throws Exception {
    return idAus(
        mvc.perform(
                post(KARTEN_PFAD)
                    .header(TOKEN_HEADER, klartext)
                    .contentType(JSON)
                    .content("{\"title\":\"%s\",\"direct\":true}".formatted(titel)))
            .andExpect(status().isCreated()));
  }

  private void labelsZuordnen(Cookie session, long karte, long... labelIds) throws Exception {
    mvc.perform(
            put("/api/cards/" + karte + "/labels")
                .cookie(session)
                .contentType(JSON)
                .content(json.writeValueAsString(Map.of("labels", labelIds))))
        .andExpect(status().isOk());
  }

  private ResultActions labelSetzen(String klartext, long karte, String name) throws Exception {
    return mvc.perform(
        post(KARTEN_PFAD + "/" + karte + LABELS)
            .header(TOKEN_HEADER, klartext)
            .contentType(JSON)
            .content("{\"name\":\"%s\"}".formatted(name)));
  }

  private ResultActions labelAbnehmen(String klartext, long karte, String name) throws Exception {
    return mvc.perform(
        delete(KARTEN_PFAD + "/" + karte + LABELS)
            .header(TOKEN_HEADER, klartext)
            .param("name", name));
  }

  private void kommentieren(String klartext, long karte, String text) throws Exception {
    mvc.perform(
            post(KARTEN_PFAD + "/" + karte + "/comments")
                .header(TOKEN_HEADER, klartext)
                .contentType(JSON)
                .content(json.writeValueAsString(Map.of("body", text))))
        .andExpect(status().is2xxSuccessful());
  }

  private JsonNode lesen(String klartext, long karte, String anhang) throws Exception {
    String body =
        mvc.perform(get(KARTEN_PFAD + "/" + karte + anhang).header(TOKEN_HEADER, klartext))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body);
  }

  private JsonNode verlauf(String klartext, long karte) throws Exception {
    return lesen(klartext, karte, "/activity");
  }

  private JsonNode kommentare(String klartext, long karte) throws Exception {
    return lesen(klartext, karte, "/comments");
  }

  private static JsonNode eintragMitDetail(JsonNode eintraege, String detail) {
    for (JsonNode eintrag : eintraege) {
      if (detail.equals(eintrag.get("detail").asText())) {
        return eintrag;
      }
    }
    throw new AssertionError("Kein Verlaufseintrag: " + detail + " in " + eintraege);
  }

  private List<String> labelsDerKarte(long karte) {
    return new JdbcTemplate(dataSource)
        .queryForList(
            "SELECT l.name FROM card_label cl JOIN label l ON l.id = cl.label_id"
                + " WHERE cl.card_id = ? ORDER BY l.name",
            String.class,
            karte);
  }

  private int labelEintraegeImVerlauf(long karte) {
    return Objects.requireNonNull(
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT count(*) FROM card_activity WHERE card_id = ? AND detail LIKE 'Label %'",
                Integer.class, karte));
  }

  private long ersteSpalte(Cookie session, long boardId) throws Exception {
    String body =
        mvc.perform(get("/api/boards/" + boardId).cookie(session))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("columns").get(0).get("id").asLong();
  }

  private static void pruefeHerkunftDerApp(JsonNode eintrag) {
    assertThat(eintrag.get("origin").asText()).isEqualTo("TOKEN");
    assertThat(eintrag.get("tokenName").asText()).isEqualTo(APP_NAME);
    assertThat(eintrag.get("actorName").asText()).isEqualTo(INHABER_NAME);
  }

  @Test
  @DisplayName("Szenario: Eine Frage mit dem Zugang der App beantworten")
  void eineFrageMitDemZugangDerAppBeantworten() throws Exception {
    // Given
    Projekt projekt = projekt();
    String app = zugang(projekt);
    long klaeren = label(projekt.inhaber(), projekt.board(), KLAEREN);
    long karte = karte(app, "Frage");
    labelsZuordnen(projekt.inhaber(), karte, klaeren);

    // When
    kommentieren(app, karte, "Die Antwort");
    labelAbnehmen(app, karte, KLAEREN).andExpect(status().isNoContent());

    // Then
    assertThat(labelsDerKarte(karte)).isEmpty();
    assertThat(kommentare(app, karte).get(0).get("author").asText()).isEqualTo(INHABER_NAME);
    pruefeHerkunftDerApp(eintragMitDetail(verlauf(app, karte), "Label kit:klaeren abgenommen"));
  }

  @Test
  @DisplayName("Szenario: Einen Durchlauf mit dem Zugang der App fortsetzen")
  void einenDurchlaufMitDemZugangDerAppFortsetzen() throws Exception {
    // Given
    Projekt projekt = projekt();
    String app = zugang(projekt);
    label(projekt.inhaber(), projekt.board(), NIGHT);
    long karte = karte(app, "Beantwortet");

    // When
    labelSetzen(app, karte, NIGHT).andExpect(status().isNoContent());

    // Then
    assertThat(labelsDerKarte(karte)).containsExactly(NIGHT);
    pruefeHerkunftDerApp(eintragMitDetail(verlauf(app, karte), "Label kit:night gesetzt"));
  }

  @Test
  @DisplayName("Szenario: Eine andere Person benutzt den Zugang mit")
  void eineAnderePersonBenutztDenZugangMit() throws Exception {
    // Given: Der Zugang gehört der Inhaberin; Mia Mitglied kennt seinen Klartext.
    Projekt projekt = projekt();
    String app = zugang(projekt);
    long karte = karte(app, "Geteilt");

    // When
    kommentieren(app, karte, "Von jemand anderem");

    // Then
    JsonNode kommentare = kommentare(app, karte);
    assertThat(kommentare.get(0).get("author").asText()).isEqualTo(INHABER_NAME);
    assertThat(kommentare.toString()).doesNotContain(MITGLIED_NAME);
    assertThat(verlauf(app, karte).toString()).doesNotContain(MITGLIED_NAME);
  }

  @Test
  @DisplayName("Szenario: Eine KI-Sitzung versucht sich selbst freizugeben")
  void eineKiSitzungVersuchtSichSelbstFreizugeben() throws Exception {
    // Given
    Projekt projekt = projekt();
    String gewoehnlich = gewoehnlicherZugang(projekt.inhaber(), projekt.id(), projekt.board());
    long klaeren = label(projekt.inhaber(), projekt.board(), KLAEREN);
    label(projekt.inhaber(), projekt.board(), NIGHT);
    long karte = karte(gewoehnlich, "Selbstfreigabe");
    labelsZuordnen(projekt.inhaber(), karte, klaeren);

    // When / Then
    labelAbnehmen(gewoehnlich, karte, KLAEREN)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath(DETAIL, containsString(NUR_EIN_MENSCH)));
    labelSetzen(gewoehnlich, karte, NIGHT)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath(DETAIL, containsString(NUR_EIN_MENSCH)));
    assertThat(labelsDerKarte(karte)).containsExactly(KLAEREN);
    assertThat(labelEintraegeImVerlauf(karte)).isZero();
  }

  @Test
  @DisplayName("Szenario: Eine andere Freigabe mit dem Zugang der App")
  void eineAndereFreigabeMitDemZugangDerApp() throws Exception {
    // Given
    Projekt projekt = projekt();
    String app = zugang(projekt);
    label(projekt.inhaber(), projekt.board(), NIGHTRUN);
    long geschuetzt = label(projekt.inhaber(), projekt.board(), GESCHUETZT);
    long karte = karte(app, "Andere Freigabe");
    labelsZuordnen(projekt.inhaber(), karte, geschuetzt);

    // When / Then
    labelSetzen(app, karte, NIGHTRUN).andExpect(status().isForbidden());
    labelAbnehmen(app, karte, GESCHUETZT).andExpect(status().isForbidden());
    assertThat(labelsDerKarte(karte)).containsExactly(GESCHUETZT);
    assertThat(labelEintraegeImVerlauf(karte)).isZero();
  }

  @Test
  @DisplayName("Szenario: Ein anderer Schreibweg mit dem Zugang der App")
  void einAndererSchreibwegMitDemZugangDerApp() throws Exception {
    // Given
    Projekt projekt = projekt();
    String app = zugang(projekt);
    long night = label(projekt.inhaber(), projekt.board(), NIGHT);
    long karte = karte(app, "Anderer Weg");

    // When / Then: Massenaktion, Ersetzen und Anlegen.
    mvc.perform(
            post("/api/cards/bulk-labels")
                .header(TOKEN_HEADER, app)
                .contentType(JSON)
                .content(
                    "{\"cardIds\":[%d],\"labelId\":%d,\"action\":\"ADD\"}".formatted(karte, night)))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/cards/" + karte + "/labels")
                .header(TOKEN_HEADER, app)
                .contentType(JSON)
                .content(json.writeValueAsString(Map.of("labels", List.of(night)))))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/boards/" + projekt.board() + "/cards")
                .header(TOKEN_HEADER, app)
                .contentType(JSON)
                .content(
                    "{\"title\":\"Gleich mit Label\",\"columnId\":%d,\"labelIds\":[%d]}"
                        .formatted(ersteSpalte(projekt.inhaber(), projekt.board()), night)))
        .andExpect(status().isForbidden());
    assertThat(labelsDerKarte(karte)).isEmpty();
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForObject(
                    "SELECT count(*) FROM card WHERE board_id = ? AND title = 'Gleich mit Label'",
                    Integer.class,
                    projekt.board()))
        .isZero();
  }

  @Test
  void dieEnglischeSchreibweiseVonKlaerungNoetigLaesstSichEbensoAbnehmen() throws Exception {
    // Given
    Projekt projekt = projekt();
    String app = zugang(projekt);
    long clarify = label(projekt.inhaber(), projekt.board(), CLARIFY);
    long karte = karte(app, "Englisch");
    labelsZuordnen(projekt.inhaber(), karte, clarify);

    // When
    labelAbnehmen(app, karte, CLARIFY).andExpect(status().isNoContent());

    // Then
    assertThat(labelsDerKarte(karte)).isEmpty();
    pruefeHerkunftDerApp(eintragMitDetail(verlauf(app, karte), "Label kit:clarify abgenommen"));
  }

  @Test
  void kartenFremderBoardsWerdenAbgewiesen() throws Exception {
    // Given: Ein zweites Board desselben Projekts, dessen Karte beide Labels kennt.
    Projekt projekt = projekt();
    String app = zugang(projekt);
    long fremdesBoard = board(projekt.inhaber(), projekt.id(), "Fremdes Board");
    long klaeren = label(projekt.inhaber(), fremdesBoard, KLAEREN);
    label(projekt.inhaber(), fremdesBoard, NIGHT);
    long fremdeKarte =
        karte(gewoehnlicherZugang(projekt.inhaber(), projekt.id(), fremdesBoard), "Fremd");
    labelsZuordnen(projekt.inhaber(), fremdeKarte, klaeren);

    // When / Then
    labelAbnehmen(app, fremdeKarte, KLAEREN).andExpect(status().isNotFound());
    labelSetzen(app, fremdeKarte, NIGHT).andExpect(status().isNotFound());
    assertThat(labelsDerKarte(fremdeKarte)).containsExactly(KLAEREN);
    assertThat(labelEintraegeImVerlauf(fremdeKarte)).isZero();
  }

  @Test
  void loeschenUndUmbenennenDerBeidenLabelBleibenGesperrt() throws Exception {
    // Given
    Projekt projekt = projekt();
    String app = zugang(projekt);
    long night = label(projekt.inhaber(), projekt.board(), NIGHT);
    long klaeren = label(projekt.inhaber(), projekt.board(), KLAEREN);

    // When / Then
    for (long labelId : List.of(night, klaeren)) {
      mvc.perform(delete("/api/labels/" + labelId).header(TOKEN_HEADER, app))
          .andExpect(status().isForbidden());
      mvc.perform(
              patch("/api/labels/" + labelId)
                  .header(TOKEN_HEADER, app)
                  .contentType(JSON)
                  .content("{\"name\":\"anders\",\"color\":\"#ff0000\"}"))
          .andExpect(status().isForbidden());
    }
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForList(
                    "SELECT name FROM label WHERE board_id = ? ORDER BY name",
                    String.class,
                    projekt.board()))
        .containsExactly(KLAEREN, NIGHT);
  }

  @Test
  void eineWirkungsloseAenderungSchreibtKeinenVerlaufseintrag() throws Exception {
    // Given: kit:klaeren ist nicht gesetzt, kit:night schon.
    Projekt projekt = projekt();
    String app = zugang(projekt);
    label(projekt.inhaber(), projekt.board(), KLAEREN);
    long night = label(projekt.inhaber(), projekt.board(), NIGHT);
    long karte = karte(app, "Wirkungslos");
    labelsZuordnen(projekt.inhaber(), karte, night);

    // When
    labelAbnehmen(app, karte, KLAEREN).andExpect(status().isNoContent());
    labelSetzen(app, karte, NIGHT).andExpect(status().isNoContent());

    // Then
    assertThat(labelEintraegeImVerlauf(karte)).isZero();
    assertThat(labelsDerKarte(karte)).containsExactly(NIGHT);
  }

  @Test
  void gewoehnlichesLabelMitDemZugangDerAppErscheintImVerlauf() throws Exception {
    // Given
    Projekt projekt = projekt();
    String app = zugang(projekt);
    label(projekt.inhaber(), projekt.board(), "Bug");
    long karte = karte(app, "Gewöhnlich");

    // When
    labelSetzen(app, karte, "Bug").andExpect(status().isNoContent());

    // Then
    pruefeHerkunftDerApp(eintragMitDetail(verlauf(app, karte), "Label Bug gesetzt"));
  }

  @Test
  void einGewoehnlicherZugangSchreibtKeinenLabelEintragInDenVerlauf() throws Exception {
    // Given
    Projekt projekt = projekt();
    String gewoehnlich = gewoehnlicherZugang(projekt.inhaber(), projekt.id(), projekt.board());
    label(projekt.inhaber(), projekt.board(), "Bug");
    long karte = karte(gewoehnlich, "Ohne Eintrag");

    // When
    labelSetzen(gewoehnlich, karte, "Bug").andExpect(status().isNoContent());
    labelAbnehmen(gewoehnlich, karte, "Bug").andExpect(status().isNoContent());

    // Then
    assertThat(labelEintraegeImVerlauf(karte)).isZero();
  }

  // --- Nebenläufigkeit ------------------------------------------------------

  /**
   * Der zweite Aufruf sieht bei der Prüfung noch keinen Zugang und läuft in den Index. Er läuft
   * ohne die Transaktion von {@link TransactionRace}, weil die Meldung den Inhaber erst nach dem
   * Rollback nachschlagen kann: Eine PostgreSQL-Transaktion ist nach der Verletzung unbrauchbar.
   */
  @Test
  void gleichzeitigesAnlegenErzeugtGenauEinenZugang() throws Exception {
    // Given
    Projekt projekt = projekt();
    long inhaber = users.findByEmail("inhaber@example.com").orElseThrow().requireId();
    long admin = users.findByEmail("admin@example.com").orElseThrow().requireId();
    TransactionTemplate ohneTransaktion = new TransactionTemplate(transactionManager);
    ohneTransaktion.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);

    // When
    TransactionRace.Result race =
        new TransactionRace(transactionManager, dataSource)
            .run(
                () -> accessTokens.appZugangAnlegen(inhaber, projekt.id(), projekt.board()),
                () ->
                    ohneTransaktion.executeWithoutResult(
                        status ->
                            accessTokens.appZugangAnlegen(admin, projekt.id(), projekt.board())));

    // Then
    assertThat(race.firstFailure()).isNull();
    assertThat(race.secondFailure())
        .isInstanceOf(AppZugangBestehtException.class)
        .hasMessageContaining(INHABER_NAME);
    assertThat(wirksameZugaenge(projekt.board())).isEqualTo(1);
  }
}
