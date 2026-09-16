package org.mwolff.manban.ratelimit.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.ratelimit.MutableClock;
import org.mwolff.manban.ratelimit.application.AttemptStore;
import org.mwolff.manban.ratelimit.application.RateLimitProperties;
import org.mwolff.manban.ratelimit.application.RateLimitProperties.Limits;
import org.mwolff.manban.ratelimit.application.RateLimitedOperation;
import org.mwolff.manban.ratelimit.domain.OriginAttempts;

/**
 * Obergrenze und Räumung des Zählspeichers (Issue #897, Plan #892 E6/E7).
 *
 * <p><strong>Welcher</strong> Eintrag bei voller Tabelle verdrängt wird, ist bewusst nicht
 * Prüfgegenstand: Zugesagt ist die Obergrenze, nicht die Reihenfolge — eine Reihenfolge wäre über
 * eine {@code ConcurrentHashMap} nur mit zusätzlicher Buchführung oder einem Scan über
 * hunderttausend Einträge zu haben, und beides wäre im Flutungsfall selbst der Lastvektor, gegen
 * den die Bremse antritt.
 */
class InMemoryAttemptStoreTest {

  private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");
  private static final Duration WINDOW = Duration.ofMinutes(15);
  private static final Duration BLOCK = Duration.ofMinutes(15);

  private final MutableClock clock = new MutableClock(NOW);

  private InMemoryAttemptStore store(int maxTrackedOrigins) {
    Limits limits = new Limits(10, WINDOW, BLOCK);
    return new InMemoryAttemptStore(
        new RateLimitProperties(true, 1, maxTrackedOrigins, limits, limits, limits), clock);
  }

  private static AttemptStore.Key key(int index) {
    return new AttemptStore.Key("203.0.113." + index, RateLimitedOperation.LOGIN);
  }

  @Test
  void get_returnsWhatWasPut() {
    // Given
    InMemoryAttemptStore store = store(10);

    // When
    store.put(key(1), new OriginAttempts(3, NOW, null));

    // Then
    assertThat(store.get(key(1))).isEqualTo(new OriginAttempts(3, NOW, null));
    assertThat(store.get(key(2))).isNull();
  }

  @Test
  void remove_dropsTheEntry() {
    // Given
    InMemoryAttemptStore store = store(10);
    store.put(key(1), OriginAttempts.none(NOW));

    // When
    store.remove(key(1));

    // Then
    assertThat(store.get(key(1))).isNull();
  }

  @Test
  void put_beyondTheLimit_doesNotGrowTheMap() {
    // Given — zehn Plätze, hundert Herkünfte (Akzeptanzkriterium aus #897).
    InMemoryAttemptStore store = store(10);

    // When
    IntStream.range(0, 100).forEach(i -> store.put(key(i), new OriginAttempts(1, NOW, null)));

    // Then
    long tracked = IntStream.range(0, 100).filter(i -> store.get(key(i)) != null).count();
    assertThat(tracked).isLessThanOrEqualTo(10);
    assertThat(store.get(key(99))).as("der zuletzt geschriebene Eintrag steht drin").isNotNull();
  }

  @Test
  void put_removesExpiredEntriesBeforeEvictingLiveOnes() {
    // Given — zwei Plätze; der erste Eintrag zählt in einem Fenster, das gleich abläuft.
    InMemoryAttemptStore store = store(2);
    store.put(key(1), new OriginAttempts(1, NOW, null));
    clock.advance(WINDOW);
    store.put(key(2), new OriginAttempts(1, clock.instant(), null));

    // When — der dritte Schreibzugriff trifft auf die volle Tabelle.
    store.put(key(3), new OriginAttempts(1, clock.instant(), null));

    // Then — geräumt wird der abgelaufene Eintrag, die beiden lebenden bleiben.
    assertThat(store.get(key(1))).isNull();
    assertThat(store.get(key(2))).isNotNull();
    assertThat(store.get(key(3))).isNotNull();
  }

  @Test
  void put_keepsRunningBlocksAndDropsElapsedOnes() {
    // Given — zwei Einträge mit Sperre: einer läuft noch, einer ist abgelaufen.
    InMemoryAttemptStore store = store(2);
    store.put(key(1), new OriginAttempts(10, NOW, NOW.plus(BLOCK)));
    store.put(key(2), new OriginAttempts(10, NOW, NOW.plus(Duration.ofMinutes(30))));

    // When
    clock.advance(Duration.ofMinutes(20));
    store.put(key(3), new OriginAttempts(1, clock.instant(), null));

    // Then — die abgelaufene Sperre ist Ballast, die laufende ist der Zweck des Speichers.
    assertThat(store.get(key(1))).isNull();
    assertThat(store.get(key(2))).isNotNull();
    assertThat(store.get(key(3))).isNotNull();
  }

  @Test
  void put_onKnownKeyReplacesTheEntryWithoutEviction() {
    // Given — die Tabelle ist voll, der Schlüssel aber bereits bekannt.
    InMemoryAttemptStore store = store(2);
    store.put(key(1), new OriginAttempts(1, NOW, null));
    store.put(key(2), new OriginAttempts(1, NOW, null));

    // When
    store.put(key(1), new OriginAttempts(2, NOW, null));

    // Then — ein Zähler-Update verdrängt niemanden.
    assertThat(store.get(key(1))).isEqualTo(new OriginAttempts(2, NOW, null));
    assertThat(store.get(key(2))).isNotNull();
  }

  @Test
  void concurrentWrites_loseNoCounter() {
    // Given — reichlich Platz, damit ausschließlich die Nebenläufigkeit geprüft wird.
    InMemoryAttemptStore store = store(1000);
    List<Integer> origins = IntStream.range(0, 500).boxed().toList();

    // When
    origins.parallelStream().forEach(i -> store.put(key(i), new OriginAttempts(i, NOW, null)));

    // Then
    assertThat(origins).allSatisfy(i -> assertThat(store.get(key(i)).attempts()).isEqualTo(i));
  }
}
