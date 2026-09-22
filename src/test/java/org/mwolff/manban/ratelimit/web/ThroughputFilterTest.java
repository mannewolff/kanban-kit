package org.mwolff.manban.ratelimit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.web.security.PatAuthenticationFilter;
import org.mwolff.manban.ratelimit.MutableClock;
import org.mwolff.manban.ratelimit.application.RejectionRecorder;
import org.mwolff.manban.ratelimit.application.ThroughputLimiter;
import org.mwolff.manban.ratelimit.application.ThroughputProperties;
import org.mwolff.manban.ratelimit.infrastructure.InMemoryPersonBudgetStore;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Der Filter der Durchsatzbremse (Issue #1002, Plan #995 E1/E5/E16): nur PAT-Aufrufe, Abweisung mit
 * {@code 429}, {@code Retry-After} und {@code urn:manban:overload}, Freigabe im {@code finally}.
 *
 * <p>Die Bremse dahinter ist die echte, mit stellbarer Uhr — geprüft wird das Zusammenspiel von
 * Filter und Zählzustand, nicht ein gestubbtes Ergebnis.
 */
class ThroughputFilterTest {

  private static final long PERSON = 7L;
  private static final String SESSION_AUTHORITY = "AUTH_SESSION";

  private final MutableClock clock = new MutableClock(Instant.parse("2026-09-22T10:00:00Z"));
  private final RejectionRecorder recorder = mock(RejectionRecorder.class);
  private final AtomicInteger reachedTheCommand = new AtomicInteger();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private ThroughputFilter filter(int perMinute, int concurrent) {
    ThroughputProperties properties = new ThroughputProperties(true, perMinute, concurrent, 100);
    return new ThroughputFilter(
        new ThroughputLimiter(new InMemoryPersonBudgetStore(properties, clock), properties, clock),
        recorder);
  }

  private static void authenticateAs(String authority) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                PERSON, null, List.of(new SimpleGrantedAuthority(authority))));
  }

  private final FilterChain command =
      (ServletRequest request, ServletResponse response) -> reachedTheCommand.incrementAndGet();

  private MockHttpServletResponse call(ThroughputFilter filter) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(new MockHttpServletRequest("POST", "/api/kanban/items"), response, command);
    return response;
  }

  @Test
  void patCalls_areCounted() throws Exception {
    ThroughputFilter filter = filter(1, 10);
    authenticateAs(PatAuthenticationFilter.AUTHORITY);

    assertThat(call(filter).getStatus()).isEqualTo(200);
    assertThat(call(filter).getStatus()).isEqualTo(429);
    assertThat(reachedTheCommand).hasValue(1);
  }

  @Test
  void sessionCalls_areNeverCounted() throws Exception {
    ThroughputFilter filter = filter(1, 1);
    authenticateAs(SESSION_AUTHORITY);

    for (int i = 0; i < 20; i++) {
      assertThat(call(filter).getStatus()).isEqualTo(200);
    }
    assertThat(reachedTheCommand).hasValue(20);
    verifyNoInteractions(recorder);
  }

  @Test
  void patAuthorityWithoutPersonId_passesUncounted() throws Exception {
    // Given: eine PAT-Authority an einem Principal, der keine userId ist — das liefert der
    // PatAuthenticationFilter nie. Gezählt wird nur, was sich einer Person zuordnen lässt.
    ThroughputFilter filter = filter(1, 1);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "jemand",
                null,
                List.of(new SimpleGrantedAuthority(PatAuthenticationFilter.AUTHORITY))));

    for (int i = 0; i < 5; i++) {
      assertThat(call(filter).getStatus()).isEqualTo(200);
    }
    verifyNoInteractions(recorder);
  }

  @Test
  void unauthenticatedCalls_passUntouched() throws Exception {
    ThroughputFilter filter = filter(1, 1);

    for (int i = 0; i < 5; i++) {
      assertThat(call(filter).getStatus()).isEqualTo(200);
    }
    assertThat(reachedTheCommand).hasValue(5);
  }

  @Test
  void rejection_carries429_retryAfterInSeconds_andTheOverloadType() throws Exception {
    ThroughputFilter filter = filter(60, 10);
    authenticateAs(PatAuthenticationFilter.AUTHORITY);
    for (int i = 0; i < 60; i++) {
      call(filter);
    }

    MockHttpServletResponse rejected = call(filter);

    assertThat(rejected.getStatus()).isEqualTo(429);
    assertThat(rejected.getHeader("Retry-After")).isEqualTo("1");
    assertThat(rejected.getContentType()).startsWith("application/problem+json");
    assertThat(rejected.getContentAsString())
        .contains("\"type\":\"urn:manban:overload\"")
        .contains("\"status\":429")
        .contains("in 1 Sekunde erneut");
    // Ausdrücklich im Header: Tomcat schriebe einen Problem-Body ohne Charset sonst als ISO-8859-1.
    assertThat(rejected.getHeader("Content-Type"))
        .isEqualTo("application/problem+json;charset=UTF-8");
  }

  @Test
  void rejectedCall_neverReachesTheCommand() throws Exception {
    // E16: Ein abgewiesener Aufruf führt nichts aus — und verbraucht damit auch keinen
    // Idempotenz-Schlüssel, der erst im Fachcode beansprucht würde.
    ThroughputFilter filter = filter(1, 10);
    authenticateAs(PatAuthenticationFilter.AUTHORITY);
    call(filter);

    for (int i = 0; i < 5; i++) {
      call(filter);
    }

    assertThat(reachedTheCommand).hasValue(1);
  }

  @Test
  void retryAfter_isRoundedUp_toWholeSeconds() throws Exception {
    // Given: 7 je Minute — ein Befehl kostet 8,57 Sekunden.
    ThroughputFilter filter = filter(7, 10);
    authenticateAs(PatAuthenticationFilter.AUTHORITY);
    for (int i = 0; i < 7; i++) {
      call(filter);
    }

    // Then: abgeschnitten wäre es 8 — und der Versuch nach 8 Sekunden würde wieder abgewiesen.
    assertThat(call(filter).getHeader("Retry-After")).isEqualTo("9");
  }

  @Test
  void retryAfter_isAtLeastOneSecond_andSaysSecondsInThePlural() throws Exception {
    ThroughputFilter filter = filter(60, 10);
    authenticateAs(PatAuthenticationFilter.AUTHORITY);
    for (int i = 0; i < 60; i++) {
      call(filter);
    }
    clock.advance(Duration.ofMillis(999));

    assertThat(call(filter).getHeader("Retry-After")).isEqualTo("1");

    ThroughputFilter slow = filter(20, 10);
    for (int i = 0; i < 20; i++) {
      call(slow);
    }
    assertThat(call(slow).getContentAsString()).contains("3 Sekunden");
  }

  @Test
  void everyRejection_reachesTheRecorderExactlyOnce() throws Exception {
    ThroughputFilter filter = filter(1, 10);
    authenticateAs(PatAuthenticationFilter.AUTHORITY);
    call(filter);
    verify(recorder, never()).record(PERSON);

    call(filter);
    call(filter);
    call(filter);

    verify(recorder, times(3)).record(PERSON);
  }

  @Test
  void failingCommand_stillReleasesThePermit() throws Exception {
    // Given: höchstens ein laufender Befehl, und der erste scheitert mit einer Ausnahme.
    ThroughputFilter filter = filter(60, 1);
    authenticateAs(PatAuthenticationFilter.AUTHORITY);
    FilterChain failing =
        (request, response) -> {
          throw new ServletException("Fachcode gescheitert");
        };
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    new MockHttpServletRequest("POST", "/api/kanban/items"),
                    new MockHttpServletResponse(),
                    failing))
        .isInstanceOf(ServletException.class);

    // When / Then: Der Zähler steht wieder auf null — der nächste Befehl läuft.
    assertThat(call(filter).getStatus()).isEqualTo(200);
  }
}
