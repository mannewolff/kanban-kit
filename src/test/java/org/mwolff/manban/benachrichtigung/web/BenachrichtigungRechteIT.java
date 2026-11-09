package org.mwolff.manban.benachrichtigung.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.TestAnmeldung;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Rechte an Schaltern und Ruhezeit über die echte Filterkette gegen Postgres (Issue #2076, Plan
 * #2070 E12): echte Rolle am Projektschalter, Vorschau-Sperre, kein Token, nur die eigene Person.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class BenachrichtigungRechteIT extends AbstractIntegrationTest {

  private static final String ADMIN = "benachrichtigung-admin@example.com";
  private static final String OWNER = "benachrichtigung-owner@example.com";
  private static final String TOKEN_HEADER = "X-Kanban-Token";
  private static final String MEINE = "/api/me/benachrichtigungen";
  private static final String RUHEZEIT = MEINE + "/ruhezeit";
  private static final String NUR_LESEND = "vorschau-nur-lesend";
  private static final String PERSOENLICH = "vorschau-persoenlich";
  private static final String AN = "{\"an\":true}";
  private static final String BERLIN =
      "{\"beginn\":\"22:00\",\"ende\":\"07:00\",\"zeitzone\":\"Europe/Berlin\"}";

  @Autowired private MockMvc mvc;
  @Autowired private TestAnmeldung anmeldung;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ProjectMembershipRepository memberships;

  // --- Projektschalter ---------------------------------------------------------------------

  @Test
  void viewerUndMemberBekommenAmProjektschalter403() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long projekt = projekt(admin, "Rollen 403");

    for (ProjectRole rolle : new ProjectRole[] {ProjectRole.VIEWER, ProjectRole.MEMBER}) {
      Cookie sitzung = mitglied("benachrichtigung-" + rolle + "@example.com", projekt, rolle);

      mvc.perform(get(projektschalter(projekt)).cookie(sitzung)).andExpect(status().isForbidden());
      schreibe(put(projektschalter(projekt)).cookie(sitzung), AN).andExpect(status().isForbidden());
    }
    assertThat(zeilenProjekt(projekt)).isZero();
  }

  @Test
  void ownerUndProjektAdminBekommenAmProjektschalter200() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long projekt = projekt(admin, "Rollen 200");
    Cookie owner = session(OWNER, PlatformRole.USER);
    Cookie projektAdmin =
        mitglied("benachrichtigung-projektadmin@example.com", projekt, ProjectRole.ADMIN);

    mvc.perform(get(projektschalter(projekt)).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.an").value(false));
    schreibe(put(projektschalter(projekt)).cookie(owner), AN)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.an").value(true));
    mvc.perform(get(projektschalter(projekt)).cookie(projektAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.an").value(true));
    schreibe(put(projektschalter(projekt)).cookie(projektAdmin), "{\"an\":false}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.an").value(false));
    assertThat(zeilenProjekt(projekt)).isZero();
  }

  @Test
  void erneutesEinschaltenLaesstDenZeitpunktUnveraendert() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long projekt = projekt(admin, "Zeitpunkt");
    Cookie owner = session(OWNER, PlatformRole.USER);
    long ownerId = anmeldung.konto(OWNER, PlatformRole.USER).requireId();

    schreibe(put(projektschalter(projekt)).cookie(owner), AN).andExpect(status().isOk());
    schreibe(put(personenschalter(projekt)).cookie(owner), AN).andExpect(status().isNoContent());
    Instant vorher = Instant.parse("2026-01-02T03:04:05Z");
    jdbc.update(
        "UPDATE benachrichtigung_projekt SET eingeschaltet_seit = ? WHERE project_id = ?",
        OffsetDateTime.parse("2026-01-02T03:04:05Z"),
        projekt);
    jdbc.update(
        "UPDATE benachrichtigung_person SET eingeschaltet_seit = ? WHERE project_id = ?",
        OffsetDateTime.parse("2026-01-02T03:04:05Z"),
        projekt);

    schreibe(put(projektschalter(projekt)).cookie(owner), AN).andExpect(status().isOk());
    schreibe(put(personenschalter(projekt)).cookie(owner), AN).andExpect(status().isNoContent());

    assertThat(
            jdbc.queryForObject(
                    "SELECT eingeschaltet_seit FROM benachrichtigung_projekt WHERE project_id = ?",
                    OffsetDateTime.class,
                    projekt)
                .toInstant())
        .isEqualTo(vorher);
    assertThat(
            jdbc.queryForObject(
                    "SELECT eingeschaltet_seit FROM benachrichtigung_person"
                        + " WHERE user_id = ? AND project_id = ?",
                    OffsetDateTime.class,
                    ownerId,
                    projekt)
                .toInstant())
        .isEqualTo(vorher);
  }

  /** E12 verlangt die echte Rolle: Der Plattform-Admin ohne Mitgliedschaft ist hier niemand. */
  @Test
  void plattformAdminOhneMitgliedschaftUndNichtmitgliedBekommen404() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long projekt = projekt(admin, "Ohne Bypass");
    Cookie fremd = session("benachrichtigung-fremd@example.com", PlatformRole.USER);

    mvc.perform(get(projektschalter(projekt)).cookie(admin)).andExpect(status().isNotFound());
    schreibe(put(projektschalter(projekt)).cookie(admin), AN).andExpect(status().isNotFound());
    mvc.perform(get(projektschalter(projekt)).cookie(fremd)).andExpect(status().isNotFound());
  }

  // --- Vorschau ----------------------------------------------------------------------------

  @Test
  void inDerVorschauEndetEinSchreibversuchAnBeidenEndpunktenMit403() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long projekt = projekt(admin, "Vorschau schreiben");
    long ownerId = anmeldung.konto(OWNER, PlatformRole.USER).requireId();
    Vorschau vorschau = starte(admin, ownerId);

    schreibe(vorschau.in(put(projektschalter(projekt))), AN)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(NUR_LESEND));
    schreibe(vorschau.in(put(personenschalter(projekt))), AN)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(NUR_LESEND));
    schreibe(vorschau.in(put(RUHEZEIT)), BERLIN)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(NUR_LESEND));
    mvc.perform(vorschau.in(delete(RUHEZEIT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(NUR_LESEND));

    assertThat(zeilenProjekt(projekt)).isZero();
    assertThat(zeilenPerson(ownerId, projekt)).isZero();
  }

  @Test
  void einGetAufDieEigenenEinstellungenEndetInDerVorschauMitVorschauPersoenlich() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    projekt(admin, "Vorschau lesen");
    long ownerId = anmeldung.konto(OWNER, PlatformRole.USER).requireId();
    Vorschau vorschau = starte(admin, ownerId);

    mvc.perform(vorschau.in(get(MEINE)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value(PERSOENLICH));
  }

  // --- Token -------------------------------------------------------------------------------

  @Test
  void einUngebundenesTokenWirdAnBeidenEndpunktenAbgewiesen() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long projekt = projekt(admin, "Token");
    Cookie owner = session(OWNER, PlatformRole.USER);
    String token = ungebundenesToken(owner);

    mvc.perform(get(projektschalter(projekt)).header(TOKEN_HEADER, token))
        .andExpect(status().isForbidden());
    schreibe(put(projektschalter(projekt)).header(TOKEN_HEADER, token), AN)
        .andExpect(status().isForbidden());
    mvc.perform(get(MEINE).header(TOKEN_HEADER, token)).andExpect(status().isForbidden());
    schreibe(put(personenschalter(projekt)).header(TOKEN_HEADER, token), AN)
        .andExpect(status().isForbidden());
    schreibe(put(RUHEZEIT).header(TOKEN_HEADER, token), BERLIN).andExpect(status().isForbidden());
    mvc.perform(delete(RUHEZEIT).header(TOKEN_HEADER, token)).andExpect(status().isForbidden());

    assertThat(zeilenProjekt(projekt)).isZero();
  }

  // --- Personenschalter --------------------------------------------------------------------

  @Test
  void personOhneMitgliedschaftUndOhneNachtlaufZugriffKannNichtEinschalten() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long projekt = projekt(admin, "Fremdes Projekt");
    String email = "benachrichtigung-aussen@example.com";
    Cookie aussen = session(email, PlatformRole.USER);

    schreibe(put(personenschalter(projekt)).cookie(aussen), AN).andExpect(status().isNotFound());

    assertThat(zeilenPerson(anmeldung.konto(email, PlatformRole.USER).requireId(), projekt))
        .isZero();
  }

  @Test
  void mitgliedSchaltetSichEinUndAusUndSiehtNurDieEigenenSchalter() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long projekt = projekt(admin, "Eigene Schalter");
    String email = "benachrichtigung-mitglied@example.com";
    Cookie mitglied = mitglied(email, projekt, ProjectRole.VIEWER);
    Cookie owner = session(OWNER, PlatformRole.USER);
    String eintrag = "$.projekte[?(@.projectId == %d)]".formatted(projekt);

    schreibe(put(personenschalter(projekt)).cookie(mitglied), AN).andExpect(status().isNoContent());

    mvc.perform(get(MEINE).cookie(mitglied))
        .andExpect(status().isOk())
        .andExpect(jsonPath(eintrag + ".projectName").value("Eigene Schalter"))
        .andExpect(jsonPath(eintrag + ".personAn").value(true))
        .andExpect(jsonPath(eintrag + ".projektAn").value(false));
    mvc.perform(get(MEINE).cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath(eintrag + ".personAn").value(false));

    schreibe(put(personenschalter(projekt)).cookie(mitglied), "{\"an\":false}")
        .andExpect(status().isNoContent());

    assertThat(zeilenPerson(anmeldung.konto(email, PlatformRole.USER).requireId(), projekt))
        .isZero();
  }

  /** Läufe sehen darf der Plattform-Admin nur bei teilnehmendem Projekt (hatNachtlaufZugriff). */
  @Test
  void plattformAdminSchaltetSichNurBeiTeilnehmendemProjektEin() throws Exception {
    Cookie admin = session(ADMIN, PlatformRole.ADMIN);
    long projekt = projekt(admin, "Teilnahme");
    jdbc.update("UPDATE project SET dashboard_participation = true WHERE id = ?", projekt);

    schreibe(put(personenschalter(projekt)).cookie(admin), AN).andExpect(status().isNoContent());

    jdbc.update("UPDATE project SET dashboard_participation = false WHERE id = ?", projekt);
    schreibe(put(personenschalter(projekt)).cookie(admin), AN).andExpect(status().isNotFound());
    mvc.perform(get(MEINE).cookie(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projekte[?(@.projectId == %d)]".formatted(projekt)).isEmpty());
  }

  // --- Ruhezeit ----------------------------------------------------------------------------

  @Test
  void ruhezeitLaesstSichSetzenLesenUndLoeschen() throws Exception {
    Cookie sitzung = session("benachrichtigung-ruhe@example.com", PlatformRole.USER);

    mvc.perform(get(MEINE).cookie(sitzung))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ruhezeit").isEmpty());

    schreibe(put(RUHEZEIT).cookie(sitzung), BERLIN).andExpect(status().isNoContent());
    mvc.perform(get(MEINE).cookie(sitzung))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ruhezeit.beginn").value("22:00"))
        .andExpect(jsonPath("$.ruhezeit.ende").value("07:00"))
        .andExpect(jsonPath("$.ruhezeit.zeitzone").value("Europe/Berlin"));

    schreibe(
            put(RUHEZEIT).cookie(sitzung),
            "{\"beginn\":\"23:30\",\"ende\":\"06:15\",\"zeitzone\":\"Asia/Tokyo\"}")
        .andExpect(status().isNoContent());
    mvc.perform(get(MEINE).cookie(sitzung))
        .andExpect(jsonPath("$.ruhezeit.beginn").value("23:30"))
        .andExpect(jsonPath("$.ruhezeit.zeitzone").value("Asia/Tokyo"));

    mvc.perform(delete(RUHEZEIT).cookie(sitzung)).andExpect(status().isNoContent());
    mvc.perform(get(MEINE).cookie(sitzung)).andExpect(jsonPath("$.ruhezeit").isEmpty());
  }

  @Test
  void ungueltigeRuhezeitEndetMit400() throws Exception {
    Cookie sitzung = session("benachrichtigung-ungueltig@example.com", PlatformRole.USER);

    schreibe(
            put(RUHEZEIT).cookie(sitzung),
            "{\"beginn\":\"07:00\",\"ende\":\"07:00\",\"zeitzone\":\"Europe/Berlin\"}")
        .andExpect(status().isBadRequest());
    schreibe(
            put(RUHEZEIT).cookie(sitzung),
            "{\"beginn\":\"22:00\",\"ende\":\"07:00\",\"zeitzone\":\"Mond/Basis\"}")
        .andExpect(status().isBadRequest());
    schreibe(
            put(RUHEZEIT).cookie(sitzung),
            "{\"beginn\":\"25:00\",\"ende\":\"07:00\",\"zeitzone\":\"Europe/Berlin\"}")
        .andExpect(status().isBadRequest());
    schreibe(put(RUHEZEIT).cookie(sitzung), "{\"zeitzone\":\"Europe/Berlin\"}")
        .andExpect(status().isBadRequest());
  }

  // --- Hilfen ------------------------------------------------------------------------------

  private static String projektschalter(long projekt) {
    return "/api/projects/" + projekt + "/benachrichtigung";
  }

  private static String personenschalter(long projekt) {
    return MEINE + "/projekte/" + projekt;
  }

  private ResultActions schreibe(MockHttpServletRequestBuilder anfrage, String body)
      throws Exception {
    return mvc.perform(anfrage.contentType("application/json").content(body));
  }

  private Cookie session(String email, PlatformRole rolle) {
    return anmeldung.anmelden(email, rolle);
  }

  private Cookie mitglied(String email, long projekt, ProjectRole rolle) {
    Cookie sitzung = session(email, PlatformRole.USER);
    long userId = anmeldung.konto(email, PlatformRole.USER).requireId();
    memberships.save(new ProjectMembership(null, projekt, userId, rolle, Instant.now()));
    return sitzung;
  }

  private long projekt(Cookie admin, String name) throws Exception {
    anmeldung.konto(OWNER, PlatformRole.USER);
    return json.readTree(
            mvc.perform(
                    post("/api/projects")
                        .cookie(admin)
                        .contentType("application/json")
                        .content("{\"name\":\"%s\",\"ownerEmail\":\"%s\"}".formatted(name, OWNER)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private String ungebundenesToken(Cookie sitzung) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/access-tokens")
                        .cookie(sitzung)
                        .contentType("application/json")
                        .content("{\"name\":\"Freies Token\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("plaintext")
        .asText();
  }

  private int zeilenProjekt(long projekt) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM benachrichtigung_projekt WHERE project_id = ?",
        Integer.class,
        projekt);
  }

  private int zeilenPerson(long userId, long projekt) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM benachrichtigung_person WHERE user_id = ? AND project_id = ?",
        Integer.class,
        userId,
        projekt);
  }

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
}
