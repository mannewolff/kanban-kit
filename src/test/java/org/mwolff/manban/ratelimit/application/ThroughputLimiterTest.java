package org.mwolff.manban.ratelimit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.ratelimit.MutableClock;
import org.mwolff.manban.ratelimit.application.ThroughputLimiter.Admitted;
import org.mwolff.manban.ratelimit.application.ThroughputLimiter.Outcome;
import org.mwolff.manban.ratelimit.application.ThroughputLimiter.Permit;
import org.mwolff.manban.ratelimit.application.ThroughputLimiter.Rejected;
import org.mwolff.manban.ratelimit.infrastructure.InMemoryPersonBudgetStore;
import org.slf4j.LoggerFactory;

/**
 * Verhalten der Durchsatzbremse gegen eine stellbare Uhr (Issue #999, Plan #995 E3/E17).
 *
 * <p>Der Speicher ist der echte, kein Mock: Er trägt die Atomarität, und ein Mock würde genau das
 * Zusammenspiel von Aufnahme und Freigabe wegstubben, das hier geprüft wird.
 */
class ThroughputLimiterTest {

  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final long PERSON = 7L;
  private static final long OTHER_PERSON = 8L;

  private final MutableClock clock = new MutableClock(NOW);
  private final ListAppender<ILoggingEvent> logWatcher = new ListAppender<>();

  @BeforeEach
  void watchLog() {
    logWatcher.start();
    ((Logger) LoggerFactory.getLogger(ThroughputLimiter.class)).addAppender(logWatcher);
  }

  /** Der Logger ist global — ohne Abmelden sammelte der Appender über Testklassen hinweg weiter. */
  @AfterEach
  void stopWatchingLog() {
    ((Logger) LoggerFactory.getLogger(ThroughputLimiter.class)).detachAppender(logWatcher);
  }

  private ThroughputLimiter limiter(ThroughputProperties properties) {
    return new ThroughputLimiter(
        new InMemoryPersonBudgetStore(properties, clock), properties, clock);
  }

  private ThroughputLimiter limiter() {
    return limiter(new ThroughputProperties(true, 60, 10, 100));
  }

  @Test
  void isEnabled_reflectsTheConfiguration() {
    assertThat(limiter().isEnabled()).isTrue();
    assertThat(limiter(new ThroughputProperties(false, 60, 10, 100)).isEnabled()).isFalse();
  }

  @Test
  void disabled_admitsEverything_andCountsNothing() {
    ThroughputLimiter limiter = limiter(new ThroughputProperties(false, 1, 1, 100));

    for (int i = 0; i < 100; i++) {
      Outcome outcome = limiter.tryAcquire(PERSON);
      assertThat(outcome).isInstanceOf(Admitted.class);
      ((Admitted) outcome).permit().close();
    }
    assertThat(logWatcher.list).isEmpty();
  }

  @Test
  void bucketSize_sixtyAtOnce_andTheSixtyFirstWaitsOneSecond() {
    ThroughputLimiter limiter = limiter();

    for (int i = 0; i < 60; i++) {
      admit(limiter, PERSON).close();
    }

    assertThat(limiter.tryAcquire(PERSON)).isEqualTo(new Rejected(Duration.ofSeconds(1)));
  }

  @Test
  void refillRate_oneCallPerSecond() {
    ThroughputLimiter limiter = limiter();
    for (int i = 0; i < 60; i++) {
      admit(limiter, PERSON).close();
    }

    clock.advance(Duration.ofMillis(999));
    assertThat(limiter.tryAcquire(PERSON)).isEqualTo(new Rejected(Duration.ofMillis(1)));

    clock.advance(Duration.ofMillis(1));
    admit(limiter, PERSON).close();
    assertThat(limiter.tryAcquire(PERSON)).isInstanceOf(Rejected.class);
  }

  @Test
  void concurrencyCap_tenRunning_andTheEleventhIsRejected_untilOneEnds() {
    ThroughputLimiter limiter = limiter();
    List<Permit> running = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      running.add(admit(limiter, PERSON));
    }

    assertThat(limiter.tryAcquire(PERSON)).isEqualTo(new Rejected(Duration.ofSeconds(1)));

    running.getFirst().close();
    assertThat(limiter.tryAcquire(PERSON)).isInstanceOf(Admitted.class);
  }

  @Test
  void limits_arePerPerson() {
    ThroughputLimiter limiter = limiter();
    for (int i = 0; i < 10; i++) {
      admit(limiter, PERSON);
    }

    assertThat(limiter.tryAcquire(OTHER_PERSON)).isInstanceOf(Admitted.class);
  }

  @Test
  void permit_releasesEvenWhenTheProtectedSectionThrows() {
    // Given: zehn Befehle, die alle mit einer Ausnahme enden.
    ThroughputLimiter limiter = limiter();
    for (int i = 0; i < 10; i++) {
      assertThatThrownBy(() -> runFailingCommand(limiter))
          .isInstanceOf(IllegalStateException.class);
    }

    // When / Then: Der Zähler ist nicht geleckt — der elfte darf laufen.
    assertThat(limiter.tryAcquire(PERSON)).isInstanceOf(Admitted.class);
  }

  @Test
  void permit_releasesOnlyOnce_evenWhenClosedTwice() {
    // Given: neun laufende Befehle und einer, der doppelt geschlossen wird.
    ThroughputLimiter limiter = limiter();
    for (int i = 0; i < 9; i++) {
      admit(limiter, PERSON);
    }
    closeTwice(admit(limiter, PERSON));

    // When / Then: Das zweite Schließen gab keinen fremden Platz frei — neun laufen, einer passt.
    admit(limiter, PERSON);
    assertThat(limiter.tryAcquire(PERSON)).isInstanceOf(Rejected.class);
  }

  @Test
  void permit_ofAnEvictedPerson_releasesQuietly_andRevivesNothing() {
    // Given: Platz für eine Person. Die erste hat einen laufenden Befehl, die zweite verdrängt sie.
    InMemoryPersonBudgetStore store =
        new InMemoryPersonBudgetStore(new ThroughputProperties(true, 60, 10, 1), clock);
    ThroughputLimiter limiter =
        new ThroughputLimiter(store, new ThroughputProperties(true, 60, 10, 1), clock);
    // When: Der Befehl der verdrängten Person endet — mit dem Ende des try-Blocks.
    try (Permit ofFirst = admit(limiter, PERSON)) {
      assertThat(ofFirst).isNotNull();
      admit(limiter, OTHER_PERSON);
    }

    // Then: kein Fehler, und die Person steht nicht als Geisterzeile wieder in der Tabelle.
    assertThat(store.update(PERSON, current -> current)).isNull();
  }

  @Test
  void rejectedCalls_doNotCount_andDoNotProlongTheWait() {
    // Given: ein leerer Eimer.
    ThroughputLimiter limiter = limiter();
    for (int i = 0; i < 60; i++) {
      admit(limiter, PERSON).close();
    }
    Rejected first = (Rejected) limiter.tryAcquire(PERSON);

    // When: hundert weitere Versuche im selben Augenblick.
    for (int i = 0; i < 100; i++) {
      limiter.tryAcquire(PERSON);
    }

    // Then: Die Wartezeit ist nicht länger geworden, und nach ihr läuft der nächste Befehl.
    assertThat(limiter.tryAcquire(PERSON)).isEqualTo(first);
    clock.advance(first.retryAfter());
    assertThat(limiter.tryAcquire(PERSON)).isInstanceOf(Admitted.class);
  }

  @Test
  void sustainedOverrun_writesExactlyOneWarnLinePerPersonAndWindow() {
    // Given: eine Person, die zwei Minuten lang zehnmal je Sekunde anklopft.
    ThroughputLimiter limiter = limiter();
    for (int second = 0; second < 120; second++) {
      for (int i = 0; i < 10; i++) {
        Outcome outcome = limiter.tryAcquire(PERSON);
        if (outcome instanceof Admitted admitted) {
          admitted.permit().close();
        }
      }
      clock.advance(Duration.ofSeconds(1));
    }

    // Then: genau eine Zeile je Minutenfenster, und sie nennt die Person, nicht mehr.
    List<ILoggingEvent> warnings =
        logWatcher.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
    assertThat(warnings).hasSize(2);
    assertThat(warnings.getFirst().getFormattedMessage())
        .contains("Person 7")
        .contains("60 je Minute")
        .contains("10 gleichzeitig");
  }

  @Test
  void warnLines_areCountedPerPerson() {
    ThroughputLimiter limiter = limiter(new ThroughputProperties(true, 60, 1, 100));
    admit(limiter, PERSON);
    admit(limiter, OTHER_PERSON);

    limiter.tryAcquire(PERSON);
    limiter.tryAcquire(OTHER_PERSON);
    limiter.tryAcquire(PERSON);

    assertThat(logWatcher.list).hasSize(2);
  }

  @Test
  void sustainedLoadOfSixtyPerMinute_runsThroughSeveralWindowsWithoutRejection() {
    // Given: die zugesagte Dauerlast — Stapel von zehn gleichzeitigen Befehlen alle zehn Sekunden,
    // also 60 je Minute, fünf Minuten lang.
    ThroughputLimiter limiter = limiter();

    for (int batch = 0; batch < 30; batch++) {
      List<Permit> running = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        running.add(admit(limiter, PERSON));
      }
      running.forEach(Permit::close);
      clock.advance(Duration.ofSeconds(10));
    }

    assertThat(logWatcher.list).isEmpty();
  }

  private static Permit admit(ThroughputLimiter limiter, long userId) {
    Outcome outcome = limiter.tryAcquire(userId);
    assertThat(outcome).isInstanceOf(Admitted.class);
    return ((Admitted) outcome).permit();
  }

  private static void closeTwice(Permit permit) {
    permit.close();
    permit.close();
  }

  private static void runFailingCommand(ThroughputLimiter limiter) {
    try (Permit permit = admit(limiter, PERSON)) {
      throw new IllegalStateException("Befehl gescheitert: " + permit);
    }
  }
}
