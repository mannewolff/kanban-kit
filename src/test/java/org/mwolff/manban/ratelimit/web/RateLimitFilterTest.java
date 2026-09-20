package org.mwolff.manban.ratelimit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.ratelimit.application.RateLimitProperties;
import org.mwolff.manban.ratelimit.application.RateLimitedOperation;
import org.mwolff.manban.ratelimit.application.RateLimiter;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Der Filter der Zählbremse (Issue #899, Plan #892 E4/E5/E8/E13).
 *
 * <p>Geprüft wird ausschließlich das Verhalten des Filters: welcher Aufruf gezählt wird, welcher
 * abgewiesen und welcher unberührt bleibt. Die Zeitregeln der Sperre hat die Domäne (#896), die
 * Fassade die Zählung (#897) — beide sind hier gemockt, damit ein Fehlschlag eindeutig dem Filter
 * gehört.
 *
 * <p>Die Herkunftsauflösung läuft dagegen echt: {@code trusted-proxy-count: 0} nimmt die
 * Peer-Adresse, und genau die muss als Schlüssel bei der Fassade ankommen.
 *
 * <p>{@code PMD.AvoidUsingHardCodedIP}: Die Adresse ist der Testgegenstand und stammt aus dem für
 * Dokumentation reservierten Bereich {@code 203.0.113.0/24} (RFC 5737).
 */
@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
class RateLimitFilterTest {

  private static final String ORIGIN = "203.0.113.9";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String UTF_8 = "UTF-8";

  private RateLimiter rateLimiter;
  private RateLimitFilter filter;

  /** Eine Kette, die genau das tut, was der begrenzte Endpunkt täte: einen Status setzen. */
  private static final class StatusServlet extends HttpServlet {

    private final int status;

    StatusServlet(int status) {
      this.status = status;
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) {
      response.setStatus(status);
    }
  }

  @BeforeEach
  void setUp() {
    rateLimiter = mock(RateLimiter.class);
    filter =
        new RateLimitFilter(
            rateLimiter,
            new ClientOriginResolver(new RateLimitProperties(true, 0, null, null, null, null)));
  }

  private static MockFilterChain chainAnswering(int status) {
    return new MockFilterChain(new StatusServlet(status));
  }

  private static MockHttpServletRequest request(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setRemoteAddr(ORIGIN);
    return request;
  }

  /** Der Normalfall: Die Bremse läuft, die Herkunft ist nicht gesperrt. */
  private void limiterRunningAndOriginFree() {
    when(rateLimiter.isEnabled()).thenReturn(true);
    when(rateLimiter.checkBlocked(anyString(), any())).thenReturn(Optional.empty());
  }

  private MockHttpServletResponse runFilter(MockHttpServletRequest request, MockFilterChain chain)
      throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    return response;
  }

  @Test
  void anmeldungZaehltDenFehlversuch() throws Exception {
    // Given — der Fehlversuch ist bei der Anmeldung das Angriffsmuster (E4).
    limiterRunningAndOriginFree();

    // When
    runFilter(request("POST", "/api/auth/login"), chainAnswering(401));

    // Then
    verify(rateLimiter).recordAttempt(ORIGIN, RateLimitedOperation.LOGIN);
  }

  @Test
  void anmeldungZaehltDieAbweisungDurchDenServer() throws Exception {
    // Given — 403 ist ebenfalls ein abgewiesener Anmeldeversuch.
    limiterRunningAndOriginFree();

    // When
    runFilter(request("POST", "/api/auth/login"), chainAnswering(403));

    // Then
    verify(rateLimiter).recordAttempt(ORIGIN, RateLimitedOperation.LOGIN);
  }

  @Test
  void anmeldungZaehltDenFormfehlerNicht() throws Exception {
    // Given — ein Formfehler ist kein Passwortversuch (E4).
    limiterRunningAndOriginFree();

    // When
    runFilter(request("POST", "/api/auth/login"), chainAnswering(400));

    // Then
    verify(rateLimiter, never()).recordAttempt(anyString(), any());
  }

  @Test
  void anmeldungZaehltDenErfolgNicht() throws Exception {
    // Given
    limiterRunningAndOriginFree();

    // When
    runFilter(request("POST", "/api/auth/login"), chainAnswering(200));

    // Then
    verify(rateLimiter, never()).recordAttempt(anyString(), any());
  }

  @Test
  void registrierungZaehltAuchDenErfolg() throws Exception {
    // Given — bei der Registrierung entsteht der Schaden gerade durch die gelungenen Aufrufe (E5).
    limiterRunningAndOriginFree();

    // When
    runFilter(request("POST", "/api/auth/register"), chainAnswering(201));

    // Then
    verify(rateLimiter).recordAttempt(ORIGIN, RateLimitedOperation.REGISTER);
  }

  @Test
  void resetAnforderungZaehltAuchDenErfolg() throws Exception {
    // Given — dasselbe für die Reset-Anforderung: jede versandte Mail zählt (E5).
    limiterRunningAndOriginFree();

    // When
    runFilter(request("POST", "/api/auth/forgot"), chainAnswering(200));

    // Then
    verify(rateLimiter).recordAttempt(ORIGIN, RateLimitedOperation.FORGOT);
  }

  @Test
  void gesperrteHerkunftErreichtDieKetteNicht() throws Exception {
    // Given — die Sperre läuft noch 90 Sekunden.
    when(rateLimiter.isEnabled()).thenReturn(true);
    when(rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN))
        .thenReturn(Optional.of(Duration.ofSeconds(90)));
    MockFilterChain chain = chainAnswering(200);

    // When
    MockHttpServletResponse response = runFilter(request("POST", "/api/auth/login"), chain);

    // Then — der Endpunkt sieht den Aufruf gar nicht erst (AK 5 aus #840).
    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getContentType()).contains(PROBLEM_JSON);
    assertThat(response.getHeader("Retry-After")).isEqualTo("90");
    assertThat(response.getContentAsString())
        .contains("\"status\":429")
        .contains("Bitte in 2 Minuten erneut versuchen");
    // Die Kodierung ist zugesichert: Der Filter setzt sie selbst, denn der MVC-Stack, der sie
    // sonst mitgäbe, wird hier gerade übersprungen.
    assertThat(response.getCharacterEncoding()).isEqualTo(UTF_8);
    assertThat(response.getContentType()).contains("charset=" + UTF_8);
    // Der abgewiesene Aufruf zählt nicht mit — sonst verlängerte jeder Anklopfer seine Sperre.
    verify(rateLimiter, never()).recordAttempt(anyString(), any());
  }

  @Test
  void problemBodyIstZeichenGenau() throws Exception {
    // Given — dieselbe Lage wie oben, hier aber auf den ganzen Body gesehen.
    when(rateLimiter.isEnabled()).thenReturn(true);
    when(rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN))
        .thenReturn(Optional.of(Duration.ofSeconds(90)));

    // When
    MockHttpServletResponse response =
        runFilter(request("POST", "/api/auth/login"), chainAnswering(200));

    // Then — kein Feld, kein Komma, kein Leerzeichen darf sich verschieben: Der Body entsteht von
    // Hand aus einer Vorlage, und ein umgebauter Literal-Aufbau (Issue #1050) fiele sonst nur über
    // einen Aufrufer auf, der ihn liest.
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Zu viele Versuche. Bitte in 2 Minuten erneut versuchen.\"}");
  }

  @Test
  void restdauerUnterEinerMinuteWirdAlsEineMinuteGenannt() throws Exception {
    // Given — 30 Sekunden Restdauer; „in 0 Minuten" wäre eine falsche Auskunft.
    when(rateLimiter.isEnabled()).thenReturn(true);
    when(rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.FORGOT))
        .thenReturn(Optional.of(Duration.ofSeconds(30)));

    // When
    MockHttpServletResponse response =
        runFilter(request("POST", "/api/auth/forgot"), chainAnswering(200));

    // Then
    assertThat(response.getHeader("Retry-After")).isEqualTo("30");
    assertThat(response.getContentAsString()).contains("Bitte in einer Minute erneut versuchen");
  }

  @Test
  void restdauerVonGenauEinerMinuteWirdAlsEineMinuteGenannt() throws Exception {
    // Given — 60 Sekunden liegen genau auf der Grenze: Wer hier aufrundet, nennt zwei Minuten und
    // schickt den Absender eine Minute länger weg als nötig.
    when(rateLimiter.isEnabled()).thenReturn(true);
    when(rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN))
        .thenReturn(Optional.of(Duration.ofSeconds(60)));

    // When
    MockHttpServletResponse response =
        runFilter(request("POST", "/api/auth/login"), chainAnswering(200));

    // Then
    assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    assertThat(response.getContentAsString()).contains("Bitte in einer Minute erneut versuchen");
  }

  @Test
  void restdauerVonGenauZweiMinutenWirdAlsZweiMinutenGenannt() throws Exception {
    // Given — dieselbe Grenze eine Minute weiter: 120 Sekunden sind zwei Minuten, nicht drei.
    when(rateLimiter.isEnabled()).thenReturn(true);
    when(rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN))
        .thenReturn(Optional.of(Duration.ofMinutes(2)));

    // When
    MockHttpServletResponse response =
        runFilter(request("POST", "/api/auth/login"), chainAnswering(200));

    // Then
    assertThat(response.getHeader("Retry-After")).isEqualTo("120");
    assertThat(response.getContentAsString()).contains("Bitte in 2 Minuten erneut versuchen");
  }

  @Test
  void angebrocheneSekundeWirdAufgerundet() throws Exception {
    // Given — 500 Millisekunden Restdauer; ein abgeschnittenes `Retry-After: 0` lüde zum
    // sofortigen nächsten Versuch ein, der wieder abgewiesen würde.
    when(rateLimiter.isEnabled()).thenReturn(true);
    when(rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN))
        .thenReturn(Optional.of(Duration.ofMillis(500)));

    // When
    MockHttpServletResponse response =
        runFilter(request("POST", "/api/auth/login"), chainAnswering(200));

    // Then
    assertThat(response.getHeader("Retry-After")).isEqualTo("1");
  }

  @Test
  void abgeschalteteBremseReichtDurchUndZaehltNicht() throws Exception {
    // Given
    when(rateLimiter.isEnabled()).thenReturn(false);
    MockFilterChain chain = chainAnswering(401);

    // When
    MockHttpServletResponse response = runFilter(request("POST", "/api/auth/login"), chain);

    // Then
    assertThat(chain.getRequest()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(401);
    verify(rateLimiter, never()).checkBlocked(anyString(), any());
    verify(rateLimiter, never()).recordAttempt(anyString(), any());
  }

  @Test
  void fremdePfadeBleibenUnberuehrt() throws Exception {
    // Given — nur die drei Vorgänge aus RateLimitedOperation sind begrenzt.
    MockFilterChain chain = chainAnswering(201);

    // When
    MockHttpServletResponse response = runFilter(request("POST", "/api/projects"), chain);

    // Then — die Bremse wird nicht einmal gefragt.
    assertThat(chain.getRequest()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    verifyNoInteractions(rateLimiter);
  }

  @Test
  void andereMethodenBleibenUnberuehrt() throws Exception {
    // Given — derselbe Pfad, aber kein Anmeldeversuch.
    MockFilterChain chain = chainAnswering(200);

    // When
    runFilter(request("GET", "/api/auth/login"), chain);

    // Then
    assertThat(chain.getRequest()).isNotNull();
    verifyNoInteractions(rateLimiter);
  }

  @Test
  void derContextPathZaehltNichtZumPfad() throws Exception {
    // Given — unter einem Context-Path lautet die URI /app/api/auth/login.
    limiterRunningAndOriginFree();
    MockHttpServletRequest request = request("POST", "/app/api/auth/login");
    request.setContextPath("/app");

    // When
    runFilter(request, chainAnswering(401));

    // Then — der Vorgang wird trotzdem erkannt.
    verify(rateLimiter).recordAttempt(ORIGIN, RateLimitedOperation.LOGIN);
  }
}
