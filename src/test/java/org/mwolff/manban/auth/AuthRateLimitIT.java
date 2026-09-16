package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.ratelimit.MutableClock;
import org.mwolff.manban.ratelimit.application.AttemptStore;
import org.mwolff.manban.ratelimit.application.RateLimitedOperation;
import org.mwolff.manban.ratelimit.application.RateLimiter;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Die sieben Akzeptanzkriterien der Zählbremse am laufenden Stack (Issue #900, fachlich #840).
 *
 * <p>Die Unit-Tests der Vorgängerpakete prüfen je einen Baustein gegen {@code
 * MockHttpServletRequest}. Zwei Dinge kann diese Bauform strukturell nicht zeigen, und genau dafür
 * ist dieser Test da: dass die erzwungene Filterreihenfolge gegenüber Springs {@link
 * ForwardedHeaderFilter} in der echten Kette hält (E2) — liefe der Weiterleitungsfilter zuerst,
 * wären die {@code X-Forwarded-*}-Header verschwunden und die Herkunft käme aus dem vom Client frei
 * wählbaren ersten Eintrag —, und dass die Zähler der drei Vorgänge tatsächlich getrennt laufen.
 *
 * <p>Die Bremse ist für die gesamte IT-Suite abgeschaltet ({@code AbstractIntegrationTest}, Issue
 * #899): 45 IT-Klassen melden sich von derselben Herkunft an. Hier wird sie per eigenem
 * {@code @TestPropertySource} wieder eingeschaltet — das ergibt einen eigenen Spring-Kontext,
 * dessen Zählstand im Arbeitsspeicher liegt und den {@code TRUNCATE} der Basisklasse überlebt.
 * Deshalb räumt {@link #seedUserAndResetTheBrake()} ihn vor jeder Testmethode selbst ab.
 *
 * <p>Die Zeit steuert eine stellbare {@link MutableClock} anstelle der {@code Clock}-Bean: Ob die
 * Sperre <em>abläuft</em> statt neu zu beginnen, ist mit einer laufenden Uhr nicht prüfbar, und
 * echte Wartezeiten (15 Minuten Sperrdauer) sind in einem Test keine Option.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = "manban.ratelimit.enabled=true")
// Die Adressen sind der Gegenstand der Pruefung: Ohne verschiedene Herkuenfte laesst sich nicht
// zeigen, dass je Herkunft gezaehlt wird (AK 3). Sie stammen aus den fuer Dokumentation
// reservierten Bereichen nach RFC 5737 — dieselbe Unterdrueckung wie in ClientOriginResolverTest.
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class AuthRateLimitIT extends AbstractIntegrationTest {

  private static final String LOGIN_PATH = "/api/auth/login";
  private static final String REGISTER_PATH = "/api/auth/register";
  private static final String FORGOT_PATH = "/api/auth/forgot";
  private static final String JSON = "application/json";
  private static final String FORWARDED_FOR = "X-Forwarded-For";

  private static final String PASSWORD = "sup3r-secret";
  private static final String WRONG_PASSWORD = "falsches-kennwort";
  private static final String KNOWN_EMAIL = "bremse@example.com";
  private static final String UNKNOWN_EMAIL = "gibt-es-nicht@example.com";

  /** Was der Proxy anhängt — die echte Herkunft, auf die die Bremse zählen muss. */
  private static final String ORIGIN_A = "198.51.100.10";

  private static final String ORIGIN_B = "198.51.100.20";

  /** Was der Client selbst mitschickt — der Wert, der die Herkunft gerade <em>nicht</em> wählt. */
  private static final String SPOOFED = "203.0.113.9";

  private static final String OTHER_SPOOFED = "192.0.2.77";

  private static final List<String> ORIGINS =
      List.of(ORIGIN_A, ORIGIN_B, SPOOFED, OTHER_SPOOFED, "127.0.0.1");

  private static final int MAX_ATTEMPTS = 10;
  private static final int NARROW_MAX_ATTEMPTS = 2;
  private static final Duration BLOCK = Duration.ofMinutes(15);
  private static final Duration ONE_MINUTE = Duration.ofMinutes(1);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Stellt die Uhr, die Fassade und Zählspeicher der Bremse gemeinsam lesen.
   *
   * <p>Sie startet bei {@code Instant.now()} und nicht bei einem festen Zeitpunkt: Dieselbe Uhr
   * datiert auch Verifikations- und Reset-Token, die in dieser Klasse entstehen.
   */
  @TestConfiguration
  static class StellbareUhrConfig {

    @Bean
    @Primary
    MutableClock stellbareUhr() {
      return new MutableClock(Instant.now());
    }
  }

  @Autowired private MockMvc mvc;

  @Autowired private AppUserRepository users;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private AttemptStore attempts;

  @Autowired private MutableClock clock;

  @Autowired private ApplicationContext context;

  private final ListAppender<ILoggingEvent> logWatcher = new ListAppender<>();

  @BeforeEach
  void seedUserAndResetTheBrake() {
    users.save(
        new AppUser(
            null,
            KNOWN_EMAIL,
            passwordEncoder.encode(PASSWORD),
            "Bremse",
            true,
            PlatformRole.USER));
    clearAttempts(attempts);
  }

  @BeforeEach
  void watchRateLimiterLog() {
    logWatcher.start();
    ((Logger) LoggerFactory.getLogger(RateLimiter.class)).addAppender(logWatcher);
  }

  /** Der Logger ist global — ohne Abmelden sammelte der Appender über Testklassen hinweg weiter. */
  @AfterEach
  void unwatchRateLimiterLog() {
    ((Logger) LoggerFactory.getLogger(RateLimiter.class)).detachAppender(logWatcher);
  }

  /**
   * Räumt den Zählstand aller in dieser Klasse benutzten Herkünfte ab.
   *
   * <p>Er hängt am gecachten Spring-Kontext und liefe sonst zwischen den Testmethoden über.
   */
  private static void clearAttempts(AttemptStore store) {
    for (String origin : ORIGINS) {
      for (RateLimitedOperation operation : RateLimitedOperation.values()) {
        store.remove(new AttemptStore.Key(origin, operation));
      }
    }
  }

  /**
   * Ein Anmeldeversuch mit vollständig gesetztem Weiterleitungsheader.
   *
   * @param forwardedFor der komplette {@code X-Forwarded-For}-Wert, gespoofter Anteil inklusive
   */
  private static MockHttpServletResponse loginVia(
      MockMvc mockMvc, String forwardedFor, String email, String password) throws Exception {
    return mockMvc
        .perform(
            post(LOGIN_PATH)
                .header(FORWARDED_FOR, forwardedFor)
                .contentType(JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
        .andReturn()
        .getResponse();
  }

  /** Ein Anmeldeversuch aus {@code origin}, den ein vertrauenswürdiger Proxy angehängt hat. */
  private MockHttpServletResponse login(String origin, String email, String password)
      throws Exception {
    return loginVia(mvc, SPOOFED + ", " + origin, email, password);
  }

  private MockHttpServletResponse register(String origin, String email) throws Exception {
    return mvc.perform(
            post(REGISTER_PATH)
                .header(FORWARDED_FOR, SPOOFED + ", " + origin)
                .contentType(JSON)
                .content(
                    "{\"email\":\"%s\",\"password\":\"%s\",\"displayName\":\"Neu\"}"
                        .formatted(email, PASSWORD)))
        .andReturn()
        .getResponse();
  }

  private MockHttpServletResponse forgot(String origin, String email) throws Exception {
    return mvc.perform(
            post(FORGOT_PATH)
                .header(FORWARDED_FOR, SPOOFED + ", " + origin)
                .contentType(JSON)
                .content("{\"email\":\"%s\"}".formatted(email)))
        .andReturn()
        .getResponse();
  }

  /** Schöpft die Anmeldegrenze der Herkunft aus; jeder dieser Versuche wird regulär beantwortet. */
  private void exhaustLogin(String origin) throws Exception {
    for (int versuch = 1; versuch <= MAX_ATTEMPTS; versuch++) {
      assertThat(login(origin, KNOWN_EMAIL, WRONG_PASSWORD).getStatus())
          .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
  }

  private static long retryAfterOf(MockHttpServletResponse response) {
    String retryAfter = response.getHeader(HttpHeaders.RETRY_AFTER);
    assertThat(retryAfter).as(HttpHeaders.RETRY_AFTER).isNotNull();
    return Long.parseLong(String.valueOf(retryAfter));
  }

  /** Status und {@code detail} der Antwort — die beiden Angaben, die der Absender sieht. */
  private static String statusAndDetail(MockHttpServletResponse response) throws Exception {
    return response.getStatus()
        + " "
        + MAPPER.readTree(response.getContentAsByteArray()).path("detail").asText();
  }

  @Test
  void ak1UndAk2_zehnVersucheBleibenOffen_derElfteWirdMitRetryAfterAbgewiesen() throws Exception {
    // Given — die Grenze ist ausgeschöpft, jeder Versuch wurde regulär mit 401 beantwortet.
    exhaustLogin(ORIGIN_A);

    // When
    MockHttpServletResponse ersteAbweisung = login(ORIGIN_A, KNOWN_EMAIL, WRONG_PASSWORD);

    // Then
    assertThat(ersteAbweisung.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(retryAfterOf(ersteAbweisung)).isEqualTo(BLOCK.toSeconds());

    // When — ein zwölfter Versuch, eine Minute später.
    clock.advance(ONE_MINUTE);
    MockHttpServletResponse zweiteAbweisung = login(ORIGIN_A, KNOWN_EMAIL, WRONG_PASSWORD);

    // Then — die Sperre läuft ab, sie beginnt nicht neu (E10): eine Minute weniger, nicht wieder
    // die volle Dauer.
    assertThat(zweiteAbweisung.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(retryAfterOf(zweiteAbweisung)).isEqualTo(BLOCK.minus(ONE_MINUTE).toSeconds());
  }

  @Test
  void ak3_eineZweiteHerkunftBleibtOffen_undDerMitgeschickteWertWaehltSieNicht() throws Exception {
    // Given — Springs Weiterleitungsfilter läuft in dieser Kette mit; nur hinter ihm ist die
    // Reihenfolge aus E2 überhaupt eine Aussage.
    assertThat(context.getBeansOfType(FilterRegistrationBean.class).values())
        .anySatisfy(
            registration ->
                assertThat(registration.getFilter()).isInstanceOf(ForwardedHeaderFilter.class));
    exhaustLogin(ORIGIN_A);
    assertThat(login(ORIGIN_A, KNOWN_EMAIL, WRONG_PASSWORD).getStatus())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // When / Then — dieselbe E-Mail, zweite echte Herkunft, gleicher gespoofter erster Eintrag:
    // keine kontoweite Sperre, und die Herkünfte fallen nicht zu einer zusammen.
    assertThat(loginVia(mvc, SPOOFED + ", " + ORIGIN_B, KNOWN_EMAIL, PASSWORD).getStatus())
        .isEqualTo(HttpStatus.OK.value());

    // Then — ein anderer gespoofter erster Eintrag befreit die gesperrte echte Herkunft nicht.
    assertThat(loginVia(mvc, OTHER_SPOOFED + ", " + ORIGIN_A, KNOWN_EMAIL, PASSWORD).getStatus())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  @Test
  void ak4_ausgeschoepfteAnmeldungSperrtDieAnderenVorgaengeNicht() throws Exception {
    // Given — die Anmeldung dieser Herkunft ist gesperrt.
    exhaustLogin(ORIGIN_A);
    assertThat(login(ORIGIN_A, KNOWN_EMAIL, WRONG_PASSWORD).getStatus())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // When / Then — Reset-Anforderung und Registrierung führen eigene Zähler.
    assertThat(forgot(ORIGIN_A, KNOWN_EMAIL).getStatus()).isEqualTo(HttpStatus.OK.value());
    assertThat(register(ORIGIN_A, "frisch@example.com").getStatus())
        .isEqualTo(HttpStatus.CREATED.value());
  }

  @Test
  void ak4_resetAnforderungUndRegistrierungSperrenAuchBeiLauterErfolgen() throws Exception {
    // Given / When — zehn gelungene Reset-Anforderungen: dort entsteht der Schaden gerade durch
    // die erfolgreichen Aufrufe (E5).
    for (int aufruf = 1; aufruf <= MAX_ATTEMPTS; aufruf++) {
      assertThat(forgot(ORIGIN_A, KNOWN_EMAIL).getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    // Then
    assertThat(forgot(ORIGIN_A, KNOWN_EMAIL).getStatus())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    // Given / When — dasselbe für die Registrierung, mit eigenem Zähler derselben Herkunft.
    for (int aufruf = 1; aufruf <= MAX_ATTEMPTS; aufruf++) {
      assertThat(register(ORIGIN_A, "neu" + aufruf + "@example.com").getStatus())
          .isEqualTo(HttpStatus.CREATED.value());
    }

    // Then
    assertThat(register(ORIGIN_A, "neu-zu-viel@example.com").getStatus())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  @Test
  void ak5_dieAbweisungVerraetNichtObDasKontoExistiert() throws Exception {
    // Given / When — zwei gleich lange Läufe von je eigener Herkunft, damit beide Zähler bei null
    // beginnen.
    List<String> mitBekannter = elevenAttempts(ORIGIN_A, KNOWN_EMAIL);
    List<String> mitUnbekannter = elevenAttempts(ORIGIN_B, UNKNOWN_EMAIL);

    // Then — kein Unterschied in Status oder detail; die Bremse ist kein Orakel über den Bestand.
    assertThat(mitBekannter)
        .hasSize(MAX_ATTEMPTS + 1)
        .last()
        .asString()
        .startsWith(String.valueOf(HttpStatus.TOO_MANY_REQUESTS.value()));
    assertThat(mitUnbekannter).isEqualTo(mitBekannter);
  }

  /** Elf Anmeldeversuche einer Herkunft, als Liste aus Status und {@code detail}. */
  private List<String> elevenAttempts(String origin, String email) throws Exception {
    List<String> antworten = new ArrayList<>();
    for (int versuch = 1; versuch <= MAX_ATTEMPTS + 1; versuch++) {
      antworten.add(statusAndDetail(login(origin, email, WRONG_PASSWORD)));
    }
    return antworten;
  }

  @Test
  void ak7_derSperreintrittErzeugtGenauEineWarnzeileOhneGeheimnisse() throws Exception {
    // Given
    exhaustLogin(ORIGIN_A);

    // When — der Sperreintritt und ein weiterer abgewiesener Versuch im selben Sperrfenster.
    login(ORIGIN_A, KNOWN_EMAIL, WRONG_PASSWORD);
    clock.advance(ONE_MINUTE);
    login(ORIGIN_A, KNOWN_EMAIL, WRONG_PASSWORD);

    // Then — eine Zeile je Herkunft, Vorgang und Sperrfenster (E11): Eine Zeile je abgewiesenem
    // Aufruf wäre derselbe Flutungsvektor, gegen den die Bremse antritt.
    List<String> warnungen =
        logWatcher.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    assertThat(warnungen).hasSize(1);
    assertThat(warnungen.get(0))
        .contains(ORIGIN_A, RateLimitedOperation.LOGIN.name())
        .doesNotContain(KNOWN_EMAIL, WRONG_PASSWORD);
  }

  /**
   * AK 6: die Grenzwerte sind konfigurierbar und nicht fest verdrahtet.
   *
   * <p>Eigenes {@code @TestPropertySource} und damit ein zweiter Spring-Kontext: Die Grenze steckt
   * in einer {@code @ConfigurationProperties}-Bean, die beim Kontextstart entsteht — zur Laufzeit
   * lässt sie sich nicht umstellen.
   *
   * <p>Die Klasse holt sich {@code MockMvc} und den Zählspeicher selbst, statt die Felder der
   * umschließenden Klasse zu benutzen: Die gehören deren Kontext, nicht diesem.
   */
  @Nested
  @TestPropertySource(properties = "manban.ratelimit.login.max-attempts=2")
  class MitEngererGrenze {

    @Autowired private MockMvc engerMvc;

    @Autowired private AttemptStore engeVersuche;

    @BeforeEach
    void resetTheBrake() {
      clearAttempts(engeVersuche);
    }

    @Test
    void ak6_dieGrenzeDerAnmeldungIstKonfigurierbar() throws Exception {
      // Given — zwei statt zehn Versuche sind offen.
      for (int versuch = 1; versuch <= NARROW_MAX_ATTEMPTS; versuch++) {
        assertThat(
                loginVia(engerMvc, SPOOFED + ", " + ORIGIN_A, KNOWN_EMAIL, WRONG_PASSWORD)
                    .getStatus())
            .isEqualTo(HttpStatus.UNAUTHORIZED.value());
      }

      // When / Then — der dritte ist abgewiesen, obwohl die Vorgabe zehn wäre.
      assertThat(
              loginVia(engerMvc, SPOOFED + ", " + ORIGIN_A, KNOWN_EMAIL, WRONG_PASSWORD)
                  .getStatus())
          .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }
  }
}
