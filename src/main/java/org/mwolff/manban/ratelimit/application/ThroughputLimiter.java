package org.mwolff.manban.ratelimit.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.mwolff.manban.ratelimit.domain.PersonBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fassade der Durchsatzbremse: höchstens so viele Befehle je Person und Minute, davon so viele
 * gleichzeitig (Issue #999, Plan #995 E3/E4/E17).
 *
 * <p>Der zweite Mechanismus im Modul {@code ratelimit}, neben der Auth-Bremse {@link RateLimiter}
 * und ohne sie anzufassen: Jene sperrt eine Herkunft nach N Fehlversuchen für eine feste Dauer,
 * diese füllt kontinuierlich nach und weist nur den Überschuss ab.
 *
 * <p>{@link #tryAcquire} liefert entweder eine {@link Permit Freigabe}, die beim Schließen den
 * Gleichzeitigkeitszähler senkt, oder die Wartedauer bis zum nächsten möglichen Versuch. Die
 * Freigabe gehört in ein {@code try}-with-resources oder ein {@code finally} — sonst leckt der
 * Zähler bei jeder Ausnahme, und die Person bliebe nach zehn gescheiterten Befehlen dauerhaft
 * gesperrt.
 */
@Service
public class ThroughputLimiter {

  private static final Logger log = LoggerFactory.getLogger(ThroughputLimiter.class);

  /** Die Freigabe, wenn die Bremse abgeschaltet ist: Es ist nichts zu senken. */
  private static final Permit NOTHING_TO_RELEASE = () -> {};

  private final PersonBudgetStore store;
  private final ThroughputProperties properties;
  private final Clock clock;

  public ThroughputLimiter(PersonBudgetStore store, ThroughputProperties properties, Clock clock) {
    this.store = store;
    this.properties = properties;
    this.clock = clock;
  }

  /** Ergebnis eines Versuchs: durchgelassen oder abgewiesen. */
  public sealed interface Outcome permits Admitted, Rejected {}

  /**
   * Der Befehl darf laufen.
   *
   * @param permit die Freigabe, die nach dem Befehl zu schließen ist
   */
  public record Admitted(Permit permit) implements Outcome {}

  /**
   * Der Befehl ist abgewiesen und hat das Kontingent nicht belastet (E17).
   *
   * @param retryAfter frühester sinnvoller Zeitpunkt für den nächsten Versuch, ab jetzt
   */
  public record Rejected(Duration retryAfter) implements Outcome {}

  /** Freigabe eines laufenden Befehls. Mehrfaches Schließen gibt nur einmal frei. */
  @FunctionalInterface
  public interface Permit extends AutoCloseable {
    @Override
    void close();
  }

  /** Ob die Bremse greift. Abgeschaltet wird weder gezählt noch abgewiesen. */
  public boolean isEnabled() {
    return properties.enabled();
  }

  /**
   * Versucht, einen Befehl der Person zuzulassen.
   *
   * <p>Ein abgewiesener Versuch ändert das Kontingent nicht (E17): Zählte er mit, verlängerte jeder
   * Wiederholversuch des Werkzeugs die eigene Wartezeit. Bei anhaltender Überschreitung entsteht
   * genau eine {@code WARN}-Zeile je Person und Minute — eine Zeile je Abweisung wäre derselbe
   * Flutungsvektor, gegen den die Bremse antritt.
   */
  public Outcome tryAcquire(long userId) {
    if (!isEnabled()) {
      return new Admitted(NOTHING_TO_RELEASE);
    }
    Instant now = clock.instant();
    PersonBudget.Limits limits = properties.limits();
    AtomicReference<Duration> wait = new AtomicReference<>(Duration.ZERO);
    AtomicBoolean warn = new AtomicBoolean();
    store.update(
        userId,
        current -> {
          PersonBudget known = current == null ? PersonBudget.fresh(now, limits) : current;
          Duration waitTime = known.waitTime(now, limits);
          wait.set(waitTime);
          if (waitTime.isZero()) {
            return known.admit(now, limits);
          }
          if (known.shouldWarn(now)) {
            warn.set(true);
            return known.warned(now);
          }
          return known;
        });
    if (wait.get().isZero()) {
      return new Admitted(new ReleasingPermit(userId));
    }
    if (warn.get()) {
      log.warn(
          "Durchsatzbremse: Person {} über der Grenze ({} je Minute, {} gleichzeitig), "
              + "Wartehinweis {}",
          userId,
          properties.perMinute(),
          properties.concurrent(),
          wait.get());
    }
    return new Rejected(wait.get());
  }

  /** Senkt beim ersten Schließen den Gleichzeitigkeitszähler der Person. */
  private final class ReleasingPermit implements Permit {

    private final long userId;
    private final AtomicBoolean released = new AtomicBoolean();

    ReleasingPermit(long userId) {
      this.userId = userId;
    }

    @Override
    public void close() {
      if (released.compareAndSet(false, true)) {
        store.update(userId, current -> current == null ? null : current.release());
      }
    }
  }
}
