package org.mwolff.manban.ratelimit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.ratelimit.MutableClock;
import org.mwolff.manban.ratelimit.application.RateLimitProperties.Limits;
import org.mwolff.manban.ratelimit.domain.OriginAttempts;
import org.slf4j.LoggerFactory;

/**
 * Fassade der Zählbremse (Issue #897, Plan #892 E10/E11/E16).
 *
 * <p>Die Grenzwerte sind bewusst klein und die Sperrdauer kürzer als das Zählfenster — nur so lässt
 * sich prüfen, dass das Ende der Sperre freigibt und nicht bloß das Fenster abgelaufen ist.
 */
class RateLimiterTest {

  private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");
  private static final Duration WINDOW = Duration.ofMinutes(10);
  private static final Duration BLOCK = Duration.ofMinutes(5);

  /**
   * Die Herkunft ist für die Fassade eine undurchsichtige Zeichenkette — Adressen zu lesen, zu
   * normalisieren und Proxies abzuziehen ist Sache des vorgelagerten Auflösers. Deshalb stehen hier
   * bewusst keine Adressen: Ein IP-Literal legte eine Zuständigkeit nahe, die die Fassade nicht
   * hat.
   */
  private static final String ORIGIN = "herkunft-eins";

  private static final String OTHER_ORIGIN = "herkunft-zwei";

  private final MutableClock clock = new MutableClock(NOW);
  private final RecordingAttemptStore store = new RecordingAttemptStore();
  private final ListAppender<ILoggingEvent> logWatcher = new ListAppender<>();

  private final RateLimiter rateLimiter = new RateLimiter(store, properties(true), clock);

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

  private static RateLimitProperties properties(boolean enabled) {
    Limits limits = new Limits(3, WINDOW, BLOCK);
    return new RateLimitProperties(enabled, 1, 1000, limits, limits, limits);
  }

  /** Zählt {@code count} Versuche der Anmeldung von {@link #ORIGIN}, alle zur aktuellen Uhrzeit. */
  private void recordLoginAttempts(int count) {
    for (int i = 0; i < count; i++) {
      rateLimiter.recordAttempt(ORIGIN, RateLimitedOperation.LOGIN);
    }
  }

  private List<String> warnings() {
    return logWatcher.list.stream()
        .filter(event -> event.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  @Test
  void isEnabled_mirrorsTheConfiguration() {
    // Given / When / Then
    assertThat(rateLimiter.isEnabled()).isTrue();
    assertThat(new RateLimiter(store, properties(false), clock).isEnabled()).isFalse();
  }

  @Test
  void checkBlocked_withoutAnyAttempt_isNotBlocked() {
    // Given / When / Then — für eine unbekannte Herkunft steht nichts im Speicher.
    assertThat(rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN)).isEmpty();
  }

  @Test
  void checkBlocked_belowTheLimit_isNotBlocked() {
    // Given
    recordLoginAttempts(2);

    // When / Then
    assertThat(rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN)).isEmpty();
    assertThat(warnings()).isEmpty();
  }

  @Test
  void checkBlocked_whenTheLimitIsExhausted_blocksAndReportsTheRemainingTime() {
    // Given
    recordLoginAttempts(3);

    // When
    clock.advance(Duration.ofMinutes(1));

    // Then
    assertThat(rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN)).contains(BLOCK);
    assertThat(store.get(new AttemptStore.Key(ORIGIN, RateLimitedOperation.LOGIN)))
        .isNotNull()
        .extracting(OriginAttempts::blockedUntil)
        .isEqualTo(NOW.plusSeconds(60).plus(BLOCK));
  }

  @Test
  void checkBlocked_withinTheSameBlock_doesNotExtendIt() {
    // Given — der erste abgewiesene Versuch setzt die Sperruhr.
    recordLoginAttempts(3);
    rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN);
    int writesAfterTheBlock = store.writtenKeys.size();

    // When
    clock.advance(Duration.ofMinutes(1));

    // Then — vier statt fünf Minuten: die Sperre läuft weiter, sie beginnt nicht neu.
    assertThat(rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN))
        .contains(Duration.ofMinutes(4));
    assertThat(store.writtenKeys).hasSize(writesAfterTheBlock);
  }

  @Test
  void checkBlocked_writesExactlyOneWarningPerBlock() {
    // Given
    recordLoginAttempts(3);

    // When
    rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN);
    clock.advance(Duration.ofMinutes(1));
    rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN);

    // Then — Herkunft, Vorgang und Dauer stehen drin, die E-Mail-Adresse kennt die Fassade nicht.
    assertThat(warnings()).hasSize(1);
    assertThat(warnings().get(0)).contains(ORIGIN, "LOGIN", BLOCK.toString());
  }

  @Test
  void checkBlocked_afterTheBlockHasElapsed_releasesTheOrigin() {
    // Given
    recordLoginAttempts(3);
    rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN);

    // When
    clock.advance(BLOCK);

    // Then
    assertThat(rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.LOGIN)).isEmpty();
  }

  @Test
  void recordAttempt_countsUp() {
    // Given / When
    recordLoginAttempts(2);

    // Then
    assertThat(store.get(new AttemptStore.Key(ORIGIN, RateLimitedOperation.LOGIN)))
        .isNotNull()
        .extracting(OriginAttempts::attempts)
        .isEqualTo(2);
  }

  @Test
  void recordAttempt_countsPerOriginAndOperation() {
    // Given
    recordLoginAttempts(3);

    // When / Then — weder eine andere Herkunft noch ein anderer Vorgang erbt die Sperre.
    assertThat(rateLimiter.checkBlocked(OTHER_ORIGIN, RateLimitedOperation.LOGIN)).isEmpty();
    assertThat(rateLimiter.checkBlocked(ORIGIN, RateLimitedOperation.REGISTER)).isEmpty();
    assertThat(store.writtenKeys)
        .extracting(AttemptStore.Key::origin, AttemptStore.Key::operation)
        .containsOnly(tuple(ORIGIN, RateLimitedOperation.LOGIN));
  }

  /** Fake statt Mock: Der Speicher ist hier Zustand, kein Kollaborateur mit prüfbarem Protokoll. */
  private static final class RecordingAttemptStore implements AttemptStore {

    private final Map<Key, OriginAttempts> entries = new HashMap<>();
    private final List<Key> writtenKeys = new ArrayList<>();

    @Override
    public @Nullable OriginAttempts get(Key key) {
      return entries.get(key);
    }

    @Override
    public void put(Key key, OriginAttempts attempts) {
      entries.put(key, attempts);
      writtenKeys.add(key);
    }

    @Override
    public void remove(Key key) {
      entries.remove(key);
    }
  }
}
