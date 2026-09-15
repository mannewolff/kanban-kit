package org.mwolff.manban.ratelimit.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.mwolff.manban.ratelimit.domain.OriginAttempts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fassade der Zählbremse und einziger Einstiegspunkt des Moduls neben {@link RateLimitedOperation}
 * (Plan #892, E16).
 *
 * <p>Die Methode heißt {@link #recordAttempt} und bewusst nicht „Fehlversuch melden": Bei
 * Registrierung und Reset-Anforderung zählt jeder Aufruf, auch der erfolgreiche (E5). Welcher
 * Aufruf gezählt wird, entscheidet der Aufrufer anhand von {@link
 * RateLimitedOperation#countsOnlyFailures()} — die Fassade zählt, was ihr gemeldet wird.
 *
 * <p>Die Fassade kennt weder Request noch Request-Body und damit auch keine E-Mail-Adresse und kein
 * Passwort. Dass diese Angaben strukturell unerreichbar sind, ist stärker als die Zusage, sie nicht
 * zu protokollieren (E11).
 */
@Service
public class RateLimiter {

  private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

  private final AttemptStore store;
  private final RateLimitProperties properties;
  private final Clock clock;

  public RateLimiter(AttemptStore store, RateLimitProperties properties, Clock clock) {
    this.store = store;
    this.properties = properties;
    this.clock = clock;
  }

  /** Ob die Bremse greift. Abgeschaltet wird weder gezählt noch abgewiesen. */
  public boolean isEnabled() {
    return properties.enabled();
  }

  /**
   * Prüft, ob die Herkunft für diesen Vorgang abzuweisen ist, und liefert die Restdauer der Sperre.
   *
   * <p>Der Aufruf ist nicht nur eine Frage: Er ist die Meldung des abgewiesenen Versuchs. Ist die
   * Grenze ausgeschöpft, setzt <em>dieser</em> Aufruf die Sperruhr auf {@code jetzt +
   * block-duration} — einmal, beim ersten tatsächlich abgewiesenen Versuch (E10, AK 2 aus #840).
   * Weitere Aufrufe im selben Sperrfenster liefern die schrumpfende Restdauer, ohne die Sperre zu
   * verlängern; ein gleitendes Fenster wäre die Waffe, vor der das Ziel warnt.
   *
   * <p>Beim Eintritt der Sperre entsteht genau eine {@code WARN}-Zeile je Herkunft, Vorgang und
   * Sperrfenster (E11). Eine Zeile je abgewiesenem Aufruf wäre derselbe Flutungsvektor, gegen den
   * die Bremse antritt — eine gesperrte Herkunft kann beliebig weiter senden.
   *
   * @return die Restdauer der Sperre, oder leer, wenn der Aufruf durchgelassen wird
   */
  public Optional<Duration> checkBlocked(String origin, RateLimitedOperation operation) {
    Instant now = clock.instant();
    AttemptStore.Key key = new AttemptStore.Key(origin, operation);
    OriginAttempts current = store.get(key);
    if (current == null) {
      return Optional.empty();
    }
    boolean alreadyBlocked = current.isBlocked(now);
    OriginAttempts blocked = current.block(now, properties.limitsFor(operation));
    if (!blocked.isBlocked(now)) {
      return Optional.empty();
    }
    Duration retryAfter = blocked.retryAfter(now);
    if (!alreadyBlocked) {
      store.put(key, blocked);
      log.warn(
          "Zählbremse: Herkunft {} für Vorgang {} gesperrt, Dauer {}",
          origin,
          operation,
          retryAfter);
    }
    return Optional.of(retryAfter);
  }

  /** Zählt einen Versuch der Herkunft für diesen Vorgang. */
  public void recordAttempt(String origin, RateLimitedOperation operation) {
    Instant now = clock.instant();
    AttemptStore.Key key = new AttemptStore.Key(origin, operation);
    OriginAttempts current = store.get(key);
    OriginAttempts known = current == null ? OriginAttempts.none(now) : current;
    store.put(key, known.recordAttempt(now, properties.limitsFor(operation)));
  }
}
