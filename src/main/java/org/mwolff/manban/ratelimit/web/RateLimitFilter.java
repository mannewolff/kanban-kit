package org.mwolff.manban.ratelimit.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.ratelimit.application.RateLimitedOperation;
import org.mwolff.manban.ratelimit.application.RateLimiter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Der Angriffspunkt der Zählbremse: ein Servlet-Filter vor der Fachlogik (Issue #899, Plan #892
 * E1/E4/E5/E8/E13).
 *
 * <p>Ein Filter und kein Interceptor und keine Prüfung in den drei Services: Nur hier ist der
 * Aufruf, bevor Bean-Validation, Fachlogik oder Datenbank ihn anfassen (AK 5 aus Issue #840). Eine
 * Bremse, die erst nach dem Datenbankzugriff greift, erzeugt genau die Last, gegen die sie antritt.
 *
 * <p><strong>Ohne {@code @Component}</strong> (E13): Ein gefundener Filter-Bean würde von Spring
 * Boot <em>zusätzlich</em> automatisch registriert — mit unbestimmter Reihenfolge. Verdrahtet wird
 * er ausschließlich in {@code config.RateLimitFilterConfig}, die die Reihenfolge gegenüber Springs
 * {@code ForwardedHeaderFilter} erzwingt (E2).
 *
 * <p><strong>Was gezählt wird</strong>, entscheidet der Vorgang: Bei der Anmeldung nur der
 * abgewiesene Versuch ({@code 401}/{@code 403}) — ein Formfehler ist kein Passwortversuch (E4). Bei
 * Registrierung und Reset-Anforderung jeder Aufruf, denn dort entsteht der Schaden gerade durch die
 * gelungenen (E5). Der wegen einer Sperre abgewiesene Aufruf zählt nie mit; sonst verlängerte jeder
 * Anklopfer seine eigene Sperre.
 *
 * <p>Gelesen werden nur Methode, Pfad und die Herkunft aus {@link ClientOriginResolver} —
 * <strong>nie</strong> der Request-Body. Die Bremse kennt weder E-Mail-Adresse noch Passwort (E11).
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final String DETAIL_ONE_MINUTE =
      "Zu viele Versuche. Bitte in einer Minute erneut versuchen.";

  private static final long MILLIS_PER_SECOND = 1000L;
  private static final long SECONDS_PER_MINUTE = 60L;

  private final RateLimiter rateLimiter;
  private final ClientOriginResolver originResolver;

  public RateLimitFilter(RateLimiter rateLimiter, ClientOriginResolver originResolver) {
    this.rateLimiter = rateLimiter;
    this.originResolver = originResolver;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    @Nullable RateLimitedOperation operation = limitedOperation(request);
    if (operation == null || !rateLimiter.isEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }
    String origin = originResolver.resolve(request);
    Optional<Duration> blocked = rateLimiter.checkBlocked(origin, operation);
    if (blocked.isPresent()) {
      rejectAsTooManyRequests(response, blocked.get());
      return;
    }
    filterChain.doFilter(request, response);
    if (countsTowardsTheLimit(operation, response.getStatus())) {
      rateLimiter.recordAttempt(origin, operation);
    }
  }

  /**
   * Der begrenzte Vorgang dieses Aufrufs.
   *
   * <p>Der Pfad kommt aus {@link RateLimitedOperation} und nicht aus einer zweiten Liste im Filter
   * — getrennt gepflegt wäre eine davon irgendwann falsch. Vom {@code requestURI} wird der
   * Context-Path abgezogen: Unter {@code /app} lautet die URI {@code /app/api/auth/login}.
   *
   * @return {@code null}, wenn Methode oder Pfad nicht begrenzt sind
   */
  private static @Nullable RateLimitedOperation limitedOperation(HttpServletRequest request) {
    if (!HttpMethod.POST.matches(request.getMethod())) {
      return null;
    }
    String path = request.getRequestURI().substring(request.getContextPath().length());
    for (RateLimitedOperation operation : RateLimitedOperation.values()) {
      if (operation.path().equals(path)) {
        return operation;
      }
    }
    return null;
  }

  /** Ob dieser beantwortete Aufruf auf das Kontingent der Herkunft geht (E4/E5). */
  private static boolean countsTowardsTheLimit(RateLimitedOperation operation, int status) {
    return !operation.countsOnlyFailures()
        || status == HttpStatus.UNAUTHORIZED.value()
        || status == HttpStatus.FORBIDDEN.value();
  }

  /**
   * Die Abweisung mit {@code 429}, Problem-Detail und {@code Retry-After} (E8).
   *
   * <p>Der Problem-Body entsteht von Hand: Der {@code GlobalExceptionHandler} greift hier nicht, er
   * ist Teil des MVC-Stacks, den dieser Filter gerade überspringt. Ein {@code ObjectMapper} als
   * dritte Abhängigkeit hinge das Format dieser Antwort außerdem an die Jackson-Konfiguration — der
   * eingesetzte Text ist eine Konstante mit einer Zahl darin und braucht kein Escaping.
   */
  private static void rejectAsTooManyRequests(HttpServletResponse response, Duration retryAfter)
      throws IOException {
    long seconds = retryAfterSeconds(retryAfter);
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    // Gewoehnliches Literal statt Text Block: Einzeilig waere der Block ein Block ohne Grund
    // (Sonar java:S5663), und eingerueckt risse die maskierte Zeile die Zeilengrenze. Am Ort statt
    // als Konstante, weil Error Prone (InlineFormatString) eine einmal benutzte Formatvorlage
    // nicht als Konstante duldet.
    response
        .getWriter()
        .write(
            ("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\","
                    + "\"status\":429,\"detail\":\"%s\"}")
                .formatted(detailFor(seconds)));
  }

  /**
   * Die Restdauer in ganzen Sekunden, aufgerundet und mindestens eine.
   *
   * <p>Abgeschnitten wäre {@code Retry-After: 0} eine Einladung zum sofortigen nächsten Versuch —
   * der wieder abgewiesen würde.
   */
  private static long retryAfterSeconds(Duration retryAfter) {
    long millis = retryAfter.toMillis();
    return Math.max(1L, (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND);
  }

  /** Die Auskunft an den Absender: Restdauer in vollen Minuten, aufgerundet. */
  private static String detailFor(long seconds) {
    long minutes = Math.max(1L, (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE);
    return minutes == 1
        ? DETAIL_ONE_MINUTE
        : "Zu viele Versuche. Bitte in %d Minuten erneut versuchen.".formatted(minutes);
  }
}
