package org.mwolff.manban.ratelimit.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.accesstoken.web.security.PatAuthenticationFilter;
import org.mwolff.manban.ratelimit.application.RejectionRecorder;
import org.mwolff.manban.ratelimit.application.ThroughputLimiter;
import org.mwolff.manban.ratelimit.application.ThroughputLimiter.Admitted;
import org.mwolff.manban.ratelimit.application.ThroughputLimiter.Permit;
import org.mwolff.manban.ratelimit.application.ThroughputLimiter.Rejected;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Der Angriffspunkt der Durchsatzbremse (Issue #1002, Plan #995 E1/E5/E16).
 *
 * <p><strong>Nur der PAT-Pfad:</strong> Der Filter greift ausschließlich, wenn die
 * Authentifizierung die Authority {@code AUTH_PAT} trägt, und zählt je Person ({@code userId}) —
 * über alle ihre Token, Läufe und Sitzungen hinweg. Die Weboberfläche kommt über die
 * Session-Authority und ist damit <em>strukturell</em> außen vor, nicht per Sonderregel.
 *
 * <p><strong>Warum hinter der Authentifizierung:</strong> Anders als die Auth-Bremse ({@link
 * RateLimitFilter}), die vor der Filterkette an der Herkunft zählt, braucht diese Bremse die Person
 * — und die steht erst fest, wenn {@code PatAuthenticationFilter} und {@code
 * DisabledUserGuardFilter} gelaufen sind. Eingehängt wird sie in {@code config.SecurityConfig}.
 *
 * <p><strong>Abweisung:</strong> {@code 429}, {@code Retry-After} in ganzen Sekunden und im
 * Problem-Detail der feste {@code type} {@value #OVERLOAD_TYPE} (E5) — das Werkzeug unterscheidet
 * daran „ausgelastet, bitte wiederholen" von jeder fachlichen Ablehnung und von der Auth-Bremse,
 * die ebenfalls mit {@code 429} antwortet. Der abgewiesene Aufruf erreicht den Fachcode nicht; er
 * führt nichts aus und verbraucht deshalb auch keinen Idempotenz-Schlüssel (E16). Die Antwort
 * schreibt der Filter selbst, wie {@link RateLimitFilter}: {@code @ControllerAdvice} greift vor dem
 * MVC-Stack nicht.
 *
 * <p>Die Freigabe wird per try-with-resources zurückgegeben, also auch bei einer Ausnahme. Ohne das
 * leckte der Gleichzeitigkeitszähler bei jeder Ausnahme, und die Person wäre nach zehn Fehlern
 * dauerhaft gesperrt.
 */
@Component
public class ThroughputFilter extends OncePerRequestFilter {

  /** Maschinenlesbarer Typ der Überlast-Abweisung (E5); ein Vertrag, kein Anzeigetext. */
  public static final String OVERLOAD_TYPE = "urn:manban:overload";

  private static final long MILLIS_PER_SECOND = 1000L;

  private final ThroughputLimiter limiter;
  private final RejectionRecorder recorder;

  public ThroughputFilter(ThroughputLimiter limiter, RejectionRecorder recorder) {
    this.limiter = limiter;
    this.recorder = recorder;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Long person = patPerson(SecurityContextHolder.getContext().getAuthentication());
    if (person == null) {
      filterChain.doFilter(request, response);
      return;
    }
    switch (limiter.tryAcquire(person)) {
      case Admitted(Permit permit) -> {
        try (permit) {
          filterChain.doFilter(request, response);
        }
      }
      case Rejected(Duration retryAfter) -> {
        recorder.record(person);
        rejectAsOverloaded(response, retryAfter);
      }
    }
  }

  /** Die {@code userId} eines PAT-Aufrufs; {@code null} für alles andere. */
  private static @Nullable Long patPerson(@Nullable Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof Long userId)
        || authentication.getAuthorities().stream()
            .noneMatch(a -> PatAuthenticationFilter.AUTHORITY.equals(a.getAuthority()))) {
      return null;
    }
    return userId;
  }

  /**
   * Die Abweisung mit {@code 429}, {@code Retry-After} und Problem-Detail (E5). Von Hand
   * geschrieben aus demselben Grund wie in {@link RateLimitFilter}: Der eingesetzte Text ist eine
   * Konstante mit einer Zahl darin und braucht weder Escaping noch die Jackson-Konfiguration.
   */
  private static void rejectAsOverloaded(HttpServletResponse response, Duration retryAfter)
      throws IOException {
    long seconds = retryAfterSeconds(retryAfter);
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            ("{\"type\":\""
                    + OVERLOAD_TYPE
                    + "\",\"title\":\"Too Many Requests\","
                    + "\"status\":429,\"detail\":\"Zu viele Befehle in kurzer Zeit. Bitte in %s"
                    + " erneut versuchen.\"}")
                .formatted(seconds == 1 ? "1 Sekunde" : seconds + " Sekunden"));
  }

  /**
   * Die Wartezeit in ganzen Sekunden, aufgerundet und mindestens eine. Abgeschnitten lüde {@code
   * Retry-After: 0} — oder eine Sekunde zu wenig — zu einem Versuch ein, der wieder abgewiesen
   * würde.
   */
  private static long retryAfterSeconds(Duration retryAfter) {
    long millis = retryAfter.toMillis();
    return Math.max(1L, (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND);
  }
}
