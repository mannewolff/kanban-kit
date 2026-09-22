package org.mwolff.manban.accesstoken.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.ratelimit.MutableClock;

/**
 * Obergrenze, Verdrängung und Minutengrenze des Stempel-Zwischenspeichers (Issue #997).
 *
 * <p><strong>Welcher</strong> Eintrag bei voller Tabelle verdrängt wird, ist bewusst nicht
 * Prüfgegenstand — wie bei {@code InMemoryAttemptStore} ist die Obergrenze zugesagt, nicht die
 * Reihenfolge. Ein verdrängter Eintrag kostet höchstens einen zusätzlichen Stempel-Schreibvorgang;
 * fachlich bleibt „zuletzt benutzt" richtig.
 */
class InMemoryLastUsedStampThrottleTest {

  private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
  private static final Duration MINUTE = Duration.ofMinutes(1);

  private final MutableClock clock = new MutableClock(NOW);

  private InMemoryLastUsedStampThrottle throttle(int maxTrackedTokens) {
    return new InMemoryLastUsedStampThrottle(maxTrackedTokens);
  }

  @Test
  void claimStamp_allowsTheFirstStampOfEachToken() {
    // Given
    InMemoryLastUsedStampThrottle throttle = throttle(10);

    // When / Then: ohne bekannten Stempel gibt es nichts zu drosseln.
    assertThat(throttle.claimStamp(1L, NOW)).isTrue();
  }

  @Test
  void claimStamp_refusesTheSecondStampWithinTheSameMinute() {
    // Given
    InMemoryLastUsedStampThrottle throttle = throttle(10);
    throttle.claimStamp(1L, NOW);

    // When / Then: 59 Sekunden später ist die Minute noch nicht um.
    assertThat(throttle.claimStamp(1L, NOW.plusSeconds(59))).isFalse();
  }

  @Test
  void claimStamp_refusesTheSecondStampAtTheSameInstant() {
    // Given: zwei Aufrufe derselben Millisekunde — der Fall der zehn gleichzeitigen Befehle.
    InMemoryLastUsedStampThrottle throttle = throttle(10);
    throttle.claimStamp(1L, NOW);

    // When / Then
    assertThat(throttle.claimStamp(1L, NOW)).isFalse();
  }

  @Test
  void claimStamp_allowsAgainExactlyAtTheMinuteBoundary() {
    // Given: die Grenze selbst ist der Wechselpunkt — „höchstens einmal je Minute" heißt, dass
    // die volle Minute wieder freigibt, nicht erst die Millisekunde danach.
    InMemoryLastUsedStampThrottle throttle = throttle(10);
    throttle.claimStamp(1L, NOW);

    // When / Then
    assertThat(throttle.claimStamp(1L, NOW.plus(MINUTE))).isTrue();
  }

  @Test
  void claimStamp_tracksTokensSeparately() {
    // Given: ein Stempel für Token 1 sagt nichts über Token 2.
    InMemoryLastUsedStampThrottle throttle = throttle(10);
    throttle.claimStamp(1L, NOW);

    // When / Then
    assertThat(throttle.claimStamp(2L, NOW)).isTrue();
  }

  @Test
  void claimStamp_afterTheMinute_startsTheNextMinuteFromTheNewStamp() {
    // Given: der freigegebene Stempel setzt die Grenze neu — sonst liefe die Drosselung nach
    // dem ersten Ablauf dauerhaft leer.
    InMemoryLastUsedStampThrottle throttle = throttle(10);
    throttle.claimStamp(1L, NOW);
    clock.advance(MINUTE);
    throttle.claimStamp(1L, clock.instant());

    // When / Then
    assertThat(throttle.claimStamp(1L, clock.instant().plusSeconds(30))).isFalse();
  }

  @Test
  void claimStamp_beyondTheLimit_doesNotGrowTheMap() {
    // Given: zehn Plätze, hundert Token (Akzeptanzkriterium aus #997).
    InMemoryLastUsedStampThrottle throttle = throttle(10);

    // When
    IntStream.range(0, 100).forEach(i -> throttle.claimStamp(i, NOW));

    // Then
    assertThat(throttle.tracked()).isEqualTo(10);
  }

  @Test
  void claimStamp_removesStaleEntriesBeforeEvictingFreshOnes() {
    // Given: zwei Plätze; der erste Eintrag ist nach der Minute nur noch Ballast.
    InMemoryLastUsedStampThrottle throttle = throttle(2);
    throttle.claimStamp(1L, NOW);
    clock.advance(MINUTE);
    throttle.claimStamp(2L, clock.instant());

    // When: der dritte Token trifft auf die volle Tabelle.
    throttle.claimStamp(3L, clock.instant());

    // Then: Token 2 ist noch frisch und bleibt gedrosselt, Token 3 steht drin.
    assertThat(throttle.tracked()).isEqualTo(2);
    assertThat(throttle.claimStamp(2L, clock.instant())).isFalse();
    assertThat(throttle.claimStamp(3L, clock.instant())).isFalse();
  }

  @Test
  void claimStamp_onFullTable_removesAllStaleEntries_andEvictsNothingFresh() {
    // Given: drei Plätze, zwei abgelaufene Einträge und ein frischer. Unabhängig von der
    // Iterationsreihenfolge: Würde nur beliebig verdrängt statt zuerst das Abgelaufene
    // entfernt, blieben nach dem Einfügen drei Einträge stehen statt zwei.
    InMemoryLastUsedStampThrottle throttle = throttle(3);
    throttle.claimStamp(1L, NOW);
    throttle.claimStamp(2L, NOW);
    clock.advance(MINUTE);
    throttle.claimStamp(3L, clock.instant());

    // When
    throttle.claimStamp(4L, clock.instant());

    // Then: beide abgelaufenen sind weg, der frische und der neue bleiben.
    assertThat(throttle.tracked()).isEqualTo(2);
    assertThat(throttle.claimStamp(3L, clock.instant())).isFalse();
  }

  @Test
  void claimStamp_belowTheLimit_leavesStaleEntriesAlone() {
    // Given: Platz ist genug. Der Ablauf-Scan läuft bewusst nur bei voller Tabelle — bei jedem
    // Aufruf kostete er genau den Aufwand, den die Drosselung einspart.
    InMemoryLastUsedStampThrottle throttle = throttle(10);
    throttle.claimStamp(1L, NOW);
    clock.advance(MINUTE);

    // When
    throttle.claimStamp(2L, clock.instant());

    // Then: der abgelaufene Eintrag steht noch — er wird erst entfernt, wenn der Platz fehlt.
    assertThat(throttle.tracked()).isEqualTo(2);
  }

  @Test
  void claimStamp_onTheKnownToken_evictsNobody() {
    // Given: die Tabelle ist voll, der Token aber bereits bekannt.
    InMemoryLastUsedStampThrottle throttle = throttle(2);
    throttle.claimStamp(1L, NOW);
    throttle.claimStamp(2L, NOW);

    // When: ein weiterer Versuch desselben Tokens — er schreibt nichts Neues in die Tabelle.
    assertThat(throttle.claimStamp(1L, NOW)).isFalse();

    // Then
    assertThat(throttle.tracked()).isEqualTo(2);
    assertThat(throttle.claimStamp(2L, NOW)).isFalse();
  }

  @Test
  void concurrentClaims_onTheSameToken_letExactlyOneThrough() {
    // Given: der Kern der Drosselung — zehn gleichzeitige Befehle einer Person teilen sich eine
    // Token-Zeile. Ließe die Entscheidung mehrere durch, nähmen mehrere denselben Zeilen-Lock.
    InMemoryLastUsedStampThrottle throttle = throttle(100);
    AtomicInteger granted = new AtomicInteger();

    // When
    List<Integer> calls = IntStream.range(0, 100).boxed().toList();
    calls.parallelStream()
        .forEach(
            i -> {
              if (throttle.claimStamp(1L, NOW)) {
                granted.incrementAndGet();
              }
            });

    // Then
    assertThat(granted.get()).isEqualTo(1);
  }

  @Test
  void concurrentClaims_onDifferentTokens_eachGetTheirFirstStamp() {
    // Given: verschiedene Token drosseln einander nicht.
    InMemoryLastUsedStampThrottle throttle = throttle(1000);
    List<Integer> tokens = IntStream.range(0, 500).boxed().toList();
    AtomicInteger granted = new AtomicInteger();

    // When
    tokens.parallelStream()
        .forEach(
            i -> {
              if (throttle.claimStamp(i, NOW)) {
                granted.incrementAndGet();
              }
            });

    // Then
    assertThat(granted.get()).isEqualTo(500);
  }
}
