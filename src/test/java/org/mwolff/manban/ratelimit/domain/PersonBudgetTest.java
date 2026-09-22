package org.mwolff.manban.ratelimit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Zeitregeln des Personenkontingents der Durchsatzbremse (Issue #999, Plan #995 E3/E17). */
class PersonBudgetTest {

  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final Duration SECOND = Duration.ofSeconds(1);

  /** 60 je Minute, davon 10 gleichzeitig — die zugesagte Grenze aus #970. */
  private static final PersonBudget.Limits LIMITS = PersonBudget.Limits.of(60, 10);

  @Test
  void limits_derivePerCallCostFromTheMinuteRate() {
    assertThat(LIMITS.costPerCall()).isEqualTo(SECOND);
    assertThat(LIMITS.capacity()).isEqualTo(60);
    assertThat(LIMITS.maxConcurrent()).isEqualTo(10);
  }

  @Test
  void fresh_startsWithFullBucketAndNothingInFlight() {
    PersonBudget budget = PersonBudget.fresh(NOW, LIMITS);

    assertThat(budget.credit()).isEqualTo(Duration.ofSeconds(60));
    assertThat(budget.refilledAt()).isEqualTo(NOW);
    assertThat(budget.inFlight()).isZero();
    assertThat(budget.warnedAt()).isNull();
  }

  @Test
  void admit_takesOneCallFromTheBucket_andCountsItAsInFlight() {
    PersonBudget admitted = PersonBudget.fresh(NOW, LIMITS).admit(NOW, LIMITS);

    assertThat(admitted.credit()).isEqualTo(Duration.ofSeconds(59));
    assertThat(admitted.inFlight()).isEqualTo(1);
  }

  @Test
  void bucketSize_allowsSixtyCallsAtOnce_andNotTheSixtyFirst() {
    // Given: 60 Aufrufe im selben Augenblick, jeder sofort wieder beendet.
    PersonBudget budget = PersonBudget.fresh(NOW, LIMITS);
    for (int i = 0; i < 60; i++) {
      assertThat(budget.waitTime(NOW, LIMITS)).isZero();
      budget = budget.admit(NOW, LIMITS).release();
    }

    // When / Then: der 61. wartet genau ein Nachfüllintervall.
    assertThat(budget.waitTime(NOW, LIMITS)).isEqualTo(SECOND);
  }

  @Test
  void refill_addsOneCallPerSecond() {
    // Given: ein leerer Eimer.
    PersonBudget empty = drain(PersonBudget.fresh(NOW, LIMITS));

    // When / Then: nach einer halben Sekunde fehlt noch die halbe, nach einer ganzen ist einer da.
    assertThat(empty.waitTime(NOW.plusMillis(500), LIMITS)).isEqualTo(Duration.ofMillis(500));
    assertThat(empty.waitTime(NOW.plus(SECOND), LIMITS)).isZero();
    assertThat(empty.refilled(NOW.plusSeconds(3), LIMITS).credit())
        .isEqualTo(Duration.ofSeconds(3));
  }

  @Test
  void refill_neverExceedsTheBucketSize() {
    PersonBudget later = PersonBudget.fresh(NOW, LIMITS).refilled(NOW.plusSeconds(600), LIMITS);

    assertThat(later.credit()).isEqualTo(Duration.ofSeconds(60));
    assertThat(later.refilledAt()).isEqualTo(NOW.plusSeconds(600));
  }

  @Test
  void refill_ignoresClockRunningBackwards() {
    // Given: Die Uhr springt zurück (NTP-Korrektur). Das darf weder Guthaben abziehen noch den
    // Nachfüllzeitpunkt zurückdrehen — sonst zählte dieselbe Zeit zweimal.
    PersonBudget empty = drain(PersonBudget.fresh(NOW, LIMITS));

    PersonBudget back = empty.refilled(NOW.minusSeconds(5), LIMITS);

    assertThat(back.credit()).isZero();
    assertThat(back.refilledAt()).isEqualTo(NOW);
  }

  @Test
  void concurrencyCap_admitsTenRunningCalls_andNotTheEleventh() {
    PersonBudget budget = PersonBudget.fresh(NOW, LIMITS);
    for (int i = 0; i < 10; i++) {
      budget = budget.admit(NOW, LIMITS);
    }

    assertThat(budget.inFlight()).isEqualTo(10);
    assertThat(budget.waitTime(NOW, LIMITS)).isEqualTo(SECOND);
    assertThat(budget.release().waitTime(NOW, LIMITS)).isZero();
  }

  @Test
  void waitTime_whenBothLimitsBite_isTheLongerOne() {
    // Given: zehn laufende Aufrufe und ein leerer Eimer.
    PersonBudget budget = PersonBudget.fresh(NOW, LIMITS);
    for (int i = 0; i < 10; i++) {
      budget = budget.admit(NOW, LIMITS);
    }
    for (int i = 0; i < 50; i++) {
      budget = budget.release().admit(NOW, LIMITS);
    }
    assertThat(budget.credit()).isZero();
    assertThat(budget.inFlight()).isEqualTo(10);

    // When / Then: Nach 400 ms fehlen dem Eimer noch 600 ms, der Deckel verlangt aber eine volle
    // Sekunde — es gilt die längere. Ist der Deckel frei, bleibt allein der Eimer.
    Instant later = NOW.plusMillis(400);
    assertThat(budget.waitTime(later, LIMITS)).isEqualTo(SECOND);
    assertThat(budget.release().waitTime(later, LIMITS)).isEqualTo(Duration.ofMillis(600));
  }

  @Test
  void release_neverGoesBelowZero() {
    assertThat(PersonBudget.fresh(NOW, LIMITS).release().inFlight()).isZero();
  }

  @Test
  void release_keepsTheCredit() {
    PersonBudget admitted = PersonBudget.fresh(NOW, LIMITS).admit(NOW, LIMITS);

    assertThat(admitted.release().credit()).isEqualTo(admitted.credit());
  }

  @Test
  void shouldWarn_oncePerWindow_andAgainAfterIt() {
    PersonBudget budget = PersonBudget.fresh(NOW, LIMITS);
    assertThat(budget.shouldWarn(NOW)).isTrue();

    PersonBudget warned = budget.warned(NOW);
    assertThat(warned.warnedAt()).isEqualTo(NOW);
    assertThat(warned.shouldWarn(NOW.plusSeconds(59))).isFalse();
    assertThat(warned.shouldWarn(NOW.plusSeconds(60))).isTrue();
  }

  @Test
  void isIdle_onlyWithNothingInFlightAndFullBucket() {
    PersonBudget fresh = PersonBudget.fresh(NOW, LIMITS);
    assertThat(fresh.isIdle(NOW, LIMITS)).isTrue();

    PersonBudget running = fresh.admit(NOW, LIMITS);
    assertThat(running.isIdle(NOW.plusSeconds(600), LIMITS)).isFalse();

    PersonBudget done = running.release();
    assertThat(done.isIdle(NOW, LIMITS)).isFalse();
    assertThat(done.isIdle(NOW.plus(SECOND), LIMITS)).isTrue();
  }

  @Test
  void sustainedLoad_ofSixtyPerMinute_isNeverRejected() {
    // Given: die zugesagte Dauerlast — ein Aufruf je Sekunde, zehn Minuten lang.
    PersonBudget budget = PersonBudget.fresh(NOW, LIMITS);
    Instant t = NOW;

    // When / Then
    for (int i = 0; i < 600; i++) {
      assertThat(budget.waitTime(t, LIMITS)).isZero();
      budget = budget.admit(t, LIMITS).release();
      t = t.plus(SECOND);
    }
  }

  private static PersonBudget drain(PersonBudget budget) {
    PersonBudget current = budget;
    for (int i = 0; i < 60; i++) {
      current = current.admit(NOW, LIMITS).release();
    }
    return current;
  }
}
