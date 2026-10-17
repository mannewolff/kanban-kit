package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Objects;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.SessionTokens;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.web.security.SessionCookieManager;
import org.mwolff.manban.common.PayloadFields;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Meldet ein Testkonto ohne Anmeldeablauf an (Plan #1801, A15): legt das Konto an, falls es fehlt,
 * und stellt das Sitzungs-Cookie direkt über {@link SessionTokens} aus. Den echten Code-Ablauf
 * prüfen allein die Auth-Integrationstests; alle übrigen Tests brauchen nur eine gültige Sitzung.
 * Für jene durchläuft {@link #perCode} den echten Code-Ablauf über HTTP (Issue #1813).
 *
 * <p>Bewusst eine Bean, die {@link AbstractIntegrationTest} für alle Subklassen importiert: Alle
 * Kontexte bekommen denselben Import, das Context-Caching bleibt unberührt (Issue #900).
 */
// PMD.TestClassWithoutTestCases: Namensmuster-Fehlalarm — die Klasse ist eine Testhilfe ohne
// eigene Testfälle; den Namen legt Plan #1801 fest, geprüft wird sie in TestAnmeldungIT.
@SuppressWarnings("PMD.TestClassWithoutTestCases")
@TestComponent
public class TestAnmeldung {

  /** Typ der Outbox-Einträge mit Anmeldecode; Nutzlast: Adresse, Code, Link. */
  private static final String LOGIN_CODE_MAIL = "mail.login-code";

  private final AppUserRepository users;
  private final SessionTokens sessionTokens;
  private final JdbcTemplate jdbc;

  TestAnmeldung(AppUserRepository users, SessionTokens sessionTokens, JdbcTemplate jdbc) {
    this.users = users;
    this.sessionTokens = sessionTokens;
    this.jdbc = jdbc;
  }

  /**
   * Legt das Konto bestätigt und freigegeben mit der Plattform-Rolle an, falls es zur Adresse noch
   * keines gibt, und liefert es. Ein bestehendes Konto bleibt unverändert.
   */
  public AppUser konto(String email, PlatformRole rolle) {
    return users
        .findByEmail(email)
        .orElseGet(() -> users.save(new AppUser(null, email, "Person", true, rolle)));
  }

  /** Wie {@link #konto}, liefert dazu das Cookie einer gültigen Sitzung dieses Kontos. */
  public Cookie anmelden(String email, PlatformRole rolle) {
    return new Cookie(
        SessionCookieManager.COOKIE_NAME, sessionTokens.issue(konto(email, rolle).requireId()));
  }

  /**
   * Der echte Code-Ablauf für ein bestehendes Konto: fordert einen Code an, liest ihn aus der
   * Outbox-Nutzlast {@code mail.login-code} und löst ihn über {@code POST /api/auth/code/verify}
   * ein. Liefert die Antwort des Einlösens — auch eine Ablehnung (gesperrt, wartend).
   */
  public MockHttpServletResponse codeEinloesen(MockMvc mvc, String email) throws Exception {
    MockHttpServletResponse anforderung =
        mvc.perform(json("/api/auth/code/request", "{\"email\":\"%s\"}".formatted(email)))
            .andReturn()
            .getResponse();
    assertThat(anforderung.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
    return mvc.perform(
            json(
                "/api/auth/code/verify",
                "{\"email\":\"%s\",\"code\":\"%s\"}".formatted(email, letzterCode(email))))
        .andReturn()
        .getResponse();
  }

  /** Wie {@link #codeEinloesen}, verlangt eine erfolgreiche Anmeldung und liefert ihr Cookie. */
  public Cookie perCode(MockMvc mvc, String email) throws Exception {
    MockHttpServletResponse antwort = codeEinloesen(mvc, email);
    assertThat(antwort.getStatus()).isEqualTo(HttpStatus.OK.value());
    Cookie cookie = antwort.getCookie(SessionCookieManager.COOKIE_NAME);
    return Objects.requireNonNull(cookie, "Die Anmeldung setzt kein Sitzungs-Cookie");
  }

  private String letzterCode(String email) {
    List<String> codes =
        jdbc
            .queryForList(
                "SELECT payload FROM outbox_entry WHERE event_type = ? ORDER BY id",
                String.class,
                LOGIN_CODE_MAIL)
            .stream()
            .map(payload -> PayloadFields.split(payload, 3))
            .filter(felder -> felder.getFirst().equals(email))
            .map(felder -> felder.get(1))
            .toList();
    assertThat(codes).isNotEmpty();
    return codes.getLast();
  }

  private static MockHttpServletRequestBuilder json(String path, String body) {
    return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
  }
}
