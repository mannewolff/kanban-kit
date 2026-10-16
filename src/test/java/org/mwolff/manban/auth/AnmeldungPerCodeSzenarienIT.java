package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Die Szenarien der fachlichen Quelle #1781, die an den Endpunkten unter {@code /api/auth/code}
 * hängen (Issue #1806, Plan #1801 A4, A5, A6, A9, E6): durchgehend über HTTP, mit Code und Link aus
 * der Outbox-Nutzlast vom Typ {@code mail.login-code}. Jedes Gerät ist ein eigenes Cookie-Glas
 * ({@link Geraet}); was ein Gerät nicht selbst als Cookie erhalten hat, schickt es nicht mit.
 *
 * <p>Ein Plattform-Admin besteht in jedem Fall: Ohne ihn entfiele die Freigabe-Prüfung (Ausnahme
 * für den ersten Admin), und das Szenario „Konto wartet auf Freigabe“ wäre nicht herstellbar.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(StellbareUhrConfig.class)
class AnmeldungPerCodeSzenarienIT extends AbstractIntegrationTest {

  private static final String MITGLIED = "mitglied@example.com";
  private static final String ADMIN = "admin@example.com";
  private static final String ZWEITER_ADMIN = "admin2@example.com";
  private static final String UNBEKANNT = "niemand@example.com";
  private static final String LOGIN_CODE = "mail.login-code";
  private static final String ADMIN_MAIL = "mail.user-approval";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectService projects;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock uhr;

  private long admin;

  @BeforeEach
  void seed() {
    admin = konto(new AppUser(null, ADMIN, "unbenutzt", "Admin", true, PlatformRole.ADMIN));
  }

  @Test
  @DisplayName("Szenario: Anmeldung am selben Gerät")
  void anmeldungAmSelbenGeraet() throws Exception {
    mitgliedAnlegen();
    long projekt = projects.create(admin, "Projekt des Mitglieds", MITGLIED).id();
    Geraet rechner = new Geraet();

    anfordern(rechner, MITGLIED);
    rechner.senden(link(letzteMail(MITGLIED).linkToken()));

    MockHttpServletResponse ich = rechner.senden(get("/api/me"));
    assertThat(ich.getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(ich.getContentAsString())
        .contains("\"email\":\"" + MITGLIED + "\"")
        .contains("\"projectId\":" + projekt);
  }

  @Test
  @DisplayName("Szenario: Anmeldung mit Code von einem anderen Gerät")
  void anmeldungMitCodeVonEinemAnderenGeraet() throws Exception {
    mitgliedAnlegen();
    Geraet rechner = new Geraet();

    anfordern(rechner, MITGLIED);
    // Die Mail liest das Handy; am Rechner kommt nur der abgetippte Code an.
    String code = letzteMail(MITGLIED).code();
    assertThat(code).matches("\\d{6}");
    MockHttpServletResponse eingabe = rechner.senden(code(MITGLIED, code));

    assertThat(eingabe.getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(rechner.angemeldet()).isTrue();
    assertThat(rechner.senden(get("/api/me")).getContentAsString()).contains(MITGLIED);
  }

  @Test
  @DisplayName("Szenario: Link auf dem Handy angeklickt")
  void linkAufDemHandyAngeklickt() throws Exception {
    mitgliedAnlegen();
    Geraet rechner = new Geraet();
    Geraet handy = new Geraet();

    anfordern(rechner, MITGLIED);
    MockHttpServletResponse klick = handy.senden(link(letzteMail(MITGLIED).linkToken()));

    assertThat(klick.getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(handy.senden(get("/api/me")).getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(rechner.angemeldet()).isFalse();
    assertThat(rechner.senden(get("/api/me")).getStatus())
        .isEqualTo(HttpStatus.UNAUTHORIZED.value());
  }

  @Test
  @DisplayName("Szenario: Abgelaufener Code")
  void abgelaufenerCode() throws Exception {
    mitgliedAnlegen();
    Geraet rechner = new Geraet();
    anfordern(rechner, MITGLIED);
    String code = letzteMail(MITGLIED).code();

    uhr.advance(Duration.ofMinutes(11));
    MockHttpServletResponse eingabe = rechner.senden(code(MITGLIED, code));

    assertThat(eingabe.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(rechner.angemeldet()).isFalse();
    assertThat(anfordern(rechner, MITGLIED).getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
    String neuerCode = letzteMail(MITGLIED).code();
    assertThat(rechner.senden(code(MITGLIED, neuerCode)).getStatus())
        .isEqualTo(HttpStatus.OK.value());
  }

  @Test
  @DisplayName("Szenario: Raten wird gestoppt")
  void ratenWirdGestoppt() throws Exception {
    mitgliedAnlegen();
    Geraet angreifer = new Geraet();
    anfordern(angreifer, MITGLIED);
    String richtig = letzteMail(MITGLIED).code();
    String falsch = "000000".equals(richtig) ? "111111" : "000000";

    for (int versuch = 1; versuch <= 5; versuch++) {
      assertThat(angreifer.senden(code(MITGLIED, falsch)).getStatus())
          .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
    MockHttpServletResponse sechster = angreifer.senden(code(MITGLIED, richtig));

    assertThat(sechster.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(angreifer.angemeldet()).isFalse();
  }

  @Test
  @DisplayName("Szenario: Unbekannte Adresse")
  void unbekannteAdresse() throws Exception {
    mitgliedAnlegen();
    Geraet geraet = new Geraet();

    MockHttpServletResponse bekannt = anfordern(geraet, MITGLIED);
    MockHttpServletResponse unbekannt = anfordern(geraet, UNBEKANNT);

    assertThat(unbekannt.getStatus()).isEqualTo(bekannt.getStatus());
    assertThat(unbekannt.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
    assertThat(unbekannt.getContentAsString()).isEqualTo(bekannt.getContentAsString());
    assertThat(mails(UNBEKANNT)).isEmpty();
    assertThat(mails(MITGLIED)).hasSize(1);
  }

  @Test
  @DisplayName("Szenario: Grenze verrät kein Konto")
  void grenzeVerraetKeinKonto() throws Exception {
    mitgliedAnlegen();
    Geraet geraet = new Geraet();

    for (int anforderung = 1; anforderung <= 5; anforderung++) {
      MockHttpServletResponse bekannt = anfordern(geraet, MITGLIED);
      MockHttpServletResponse unbekannt = anfordern(geraet, UNBEKANNT);
      assertThat(bekannt.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
      assertThat(unbekannt.getStatus()).isEqualTo(bekannt.getStatus());
      assertThat(unbekannt.getContentAsString()).isEqualTo(bekannt.getContentAsString());
      uhr.advance(Duration.ofMinutes(5));
    }
    MockHttpServletResponse bekannt = anfordern(geraet, MITGLIED);
    MockHttpServletResponse unbekannt = anfordern(geraet, UNBEKANNT);

    assertThat(bekannt.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(unbekannt.getStatus()).isEqualTo(bekannt.getStatus());
    assertThat(bekannt.getContentAsString()).isNotBlank();
    assertThat(unbekannt.getContentAsString()).isEqualTo(bekannt.getContentAsString());
    assertThat(unbekannt.getContentType()).isEqualTo(bekannt.getContentType());
  }

  @Test
  @DisplayName("Szenario: Gesperrtes Konto")
  void gesperrtesKonto() throws Exception {
    mitgliedAnlegen();
    konto(
        new AppUser(
            null,
            "gesperrt@example.com",
            "unbenutzt",
            "Gesperrt",
            true,
            PlatformRole.USER,
            uhr.instant(),
            admin,
            uhr.instant()));
    Geraet geraet = new Geraet();

    MockHttpServletResponse anforderung = anfordern(geraet, "gesperrt@example.com");
    MockHttpServletResponse vergleich = anfordern(new Geraet(), MITGLIED);
    MockHttpServletResponse eingabe =
        geraet.senden(code("gesperrt@example.com", letzteMail("gesperrt@example.com").code()));

    assertThat(anforderung.getStatus()).isEqualTo(vergleich.getStatus());
    assertThat(anforderung.getContentAsString()).isEqualTo(vergleich.getContentAsString());
    assertThat(eingabe.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(eingabe.getContentAsString()).contains("Konto gesperrt");
    assertThat(geraet.angemeldet()).isFalse();
  }

  @Test
  @DisplayName("Szenario: Konto wartet auf Freigabe")
  void kontoWartetAufFreigabe() throws Exception {
    konto(new AppUser(null, ZWEITER_ADMIN, "unbenutzt", "Admin 2", true, PlatformRole.ADMIN));
    long wartend =
        konto(
            new AppUser(
                null,
                "wartend@example.com",
                "unbenutzt",
                "Wartend",
                false,
                PlatformRole.USER,
                null,
                null));
    Geraet geraet = new Geraet();

    anfordern(geraet, "wartend@example.com");
    MockHttpServletResponse eingabe =
        geraet.senden(code("wartend@example.com", letzteMail("wartend@example.com").code()));

    assertThat(eingabe.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(eingabe.getContentAsString()).contains("Das Konto wartet auf die Freigabe");
    assertThat(geraet.angemeldet()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT email_verified FROM app_user WHERE id = ?", Boolean.class, wartend))
        .isTrue();
    assertThat(empfaengerDerAdminMails()).containsExactlyInAnyOrder(ADMIN, ZWEITER_ADMIN);
  }

  // --- Hilfen ---

  private void mitgliedAnlegen() {
    konto(new AppUser(null, MITGLIED, "unbenutzt", "Mitglied", true, PlatformRole.USER));
  }

  private long konto(AppUser user) {
    return users.save(user).requireId();
  }

  private static MockHttpServletRequestBuilder anforderung(String email) {
    return json("/api/auth/code/request", "{\"email\":\"" + email + "\"}");
  }

  private static MockHttpServletResponse anfordern(Geraet geraet, String email) throws Exception {
    return geraet.senden(anforderung(email));
  }

  private static MockHttpServletRequestBuilder code(String email, String code) {
    return json("/api/auth/code/verify", "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}");
  }

  private static MockHttpServletRequestBuilder link(String token) {
    return json("/api/auth/code/link", "{\"token\":\"" + token + "\"}");
  }

  private static MockHttpServletRequestBuilder json(String path, String body) {
    return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  /** Die Mails mit Anmeldecode an die Adresse, älteste zuerst. */
  private List<Mail> mails(String email) {
    return jdbc
        .queryForList(
            "SELECT payload FROM outbox_entry WHERE event_type = ? ORDER BY id",
            String.class,
            LOGIN_CODE)
        .stream()
        .map(payload -> PayloadFields.split(payload, 3))
        .filter(felder -> felder.get(0).equals(email))
        .map(felder -> new Mail(felder.get(1), felder.get(2)))
        .toList();
  }

  private Mail letzteMail(String email) {
    List<Mail> mails = mails(email);
    assertThat(mails).isNotEmpty();
    return mails.getLast();
  }

  private List<String> empfaengerDerAdminMails() {
    return jdbc
        .queryForList(
            "SELECT payload FROM outbox_entry WHERE event_type = ? ORDER BY id",
            String.class,
            ADMIN_MAIL)
        .stream()
        .map(payload -> PayloadFields.split(payload, 3))
        .filter(felder -> "wartend@example.com".equals(felder.get(1)))
        .map(List::getFirst)
        .toList();
  }

  /** Code und Link einer Mail; das Token steht im Parameter {@code token} der Link-URL. */
  private record Mail(String code, String linkUrl) {
    String linkToken() {
      String token =
          UriComponentsBuilder.fromUri(URI.create(linkUrl))
              .build()
              .getQueryParams()
              .getFirst("token");
      assertThat(token).isNotBlank();
      return token;
    }
  }

  /**
   * Ein Gerät mit eigenem Cookie-Glas: Es schickt nur das Session-Cookie mit, das es selbst per
   * {@code Set-Cookie} erhalten hat.
   */
  private final class Geraet {

    private @Nullable Cookie sitzung;

    MockHttpServletResponse senden(MockHttpServletRequestBuilder anfrage) throws Exception {
      if (sitzung != null) {
        anfrage.cookie(sitzung);
      }
      MockHttpServletResponse antwort = mvc.perform(anfrage).andReturn().getResponse();
      Cookie neu = antwort.getCookie(SessionCookieManager.COOKIE_NAME);
      if (neu != null) {
        sitzung = neu.getMaxAge() == 0 ? null : neu;
      }
      return antwort;
    }

    boolean angemeldet() {
      return sitzung != null;
    }
  }
}
