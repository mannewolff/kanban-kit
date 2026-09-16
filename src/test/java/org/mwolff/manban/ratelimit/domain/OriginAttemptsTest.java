package org.mwolff.manban.ratelimit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.ratelimit.domain.OriginAttempts.Limits;

/**
 * Zeitregeln der Zählbremse je Herkunft und Vorgang (Issue #896, Plan #892 E10).
 *
 * <p>Die Zeit steht fest: Alle Zeitpunkte leiten sich von {@link #NOW} ab, die Domäne zieht sie
 * nicht selbst (keine {@code Clock}-Abhängigkeit, kein {@code Instant.now()}). Die Grenzwerte sind
 * bewusst so gewählt, dass die Sperrdauer <em>kürzer</em> ist als das Zählfenster — nur dann lässt
 * sich prüfen, dass das Ende der Sperre den Zustand zurücksetzt und nicht bloß das Fenster abläuft.
 */
class OriginAttemptsTest {

  private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");
  private static final Duration WINDOW = Duration.ofMinutes(10);
  private static final Duration BLOCK = Duration.ofMinutes(5);
  private static final Limits LIMITS = new Limits(3, WINDOW, BLOCK);

  /** Zeitpunkt des ersten abgewiesenen Versuchs — nach dem dritten, noch gezählten Versuch. */
  private static final Instant REJECTED = NOW.plusSeconds(30);

  /** Ende der Sperre, die {@link #REJECTED} auslöst. */
  private static final Instant BLOCK_OVER = REJECTED.plus(BLOCK);

  /** Zustand nach {@code count} Versuchen, alle zum Zeitpunkt {@link #NOW}. */
  private static OriginAttempts afterAttempts(int count) {
    OriginAttempts state = OriginAttempts.none(NOW);
    for (int i = 0; i < count; i++) {
      state = state.recordAttempt(NOW, LIMITS);
    }
    return state;
  }

  /** Zustand, den der erste abgewiesene Versuch zu {@link #REJECTED} hinterlässt. */
  private static OriginAttempts blocked() {
    return afterAttempts(3).block(REJECTED, LIMITS);
  }

  @Test
  void none_startsWithoutAttemptsAndWithoutBlock() {
    // Given / When
    OriginAttempts state = OriginAttempts.none(NOW);

    // Then
    assertThat(state)
        .extracting(
            OriginAttempts::attempts, OriginAttempts::windowStartedAt, OriginAttempts::blockedUntil)
        .containsExactly(0, NOW, null);
    assertThat(state.isBlocked(NOW)).isFalse();
  }

  @Test
  void recordAttempt_belowTheLimit_doesNotBlock() {
    // Given / When
    OriginAttempts state = afterAttempts(2);

    // Then
    assertThat(state)
        .extracting(
            OriginAttempts::attempts, OriginAttempts::windowStartedAt, OriginAttempts::blockedUntil)
        .containsExactly(2, NOW, null);
    assertThat(state.isBlocked(NOW)).isFalse();
  }

  @Test
  void recordAttempt_reachingTheLimit_doesNotSetBlockedUntilYet() {
    // Given / When — AK 2: die Sperruhr startet nicht beim Erreichen der Grenze.
    OriginAttempts state = afterAttempts(3);

    // Then
    assertThat(state.attempts()).isEqualTo(3);
    assertThat(state.blockedUntil()).isNull();
    assertThat(state.isBlocked(NOW)).isFalse();
  }

  @Test
  void recordAttempt_withinTheWindow_keepsCounting() {
    // Given
    Instant lastInWindow = NOW.plus(WINDOW).minusNanos(1);

    // When
    OriginAttempts state = afterAttempts(2).recordAttempt(lastInWindow, LIMITS);

    // Then
    assertThat(state)
        .extracting(OriginAttempts::attempts, OriginAttempts::windowStartedAt)
        .containsExactly(3, NOW);
  }

  @Test
  void recordAttempt_afterTheWindowExpired_startsTheNextWindow() {
    // Given
    Instant windowOver = NOW.plus(WINDOW);

    // When
    OriginAttempts state = afterAttempts(2).recordAttempt(windowOver, LIMITS);

    // Then
    assertThat(state)
        .extracting(
            OriginAttempts::attempts, OriginAttempts::windowStartedAt, OriginAttempts::blockedUntil)
        .containsExactly(1, windowOver, null);
  }

  @Test
  void block_belowTheLimit_leavesTheStateUnblocked() {
    // Given / When
    OriginAttempts state = afterAttempts(2).block(NOW, LIMITS);

    // Then
    assertThat(state.blockedUntil()).isNull();
    assertThat(state.attempts()).isEqualTo(2);
  }

  @Test
  void block_firstRejectedAttempt_startsTheBlockFromThatMoment() {
    // Given / When — AK 2: erst der abgewiesene Versuch setzt die Sperre.
    OriginAttempts state = blocked();

    // Then
    assertThat(state)
        .extracting(
            OriginAttempts::attempts, OriginAttempts::windowStartedAt, OriginAttempts::blockedUntil)
        .containsExactly(3, NOW, BLOCK_OVER);
    assertThat(state.isBlocked(REJECTED)).isTrue();
  }

  @Test
  void block_furtherRejectedAttempt_doesNotExtendTheBlock() {
    // Given
    OriginAttempts state = blocked();

    // When
    OriginAttempts again = state.block(REJECTED.plusSeconds(60), LIMITS);

    // Then
    assertThat(again).isEqualTo(state);
  }

  @Test
  void block_afterTheWindowExpired_doesNotBlock() {
    // Given
    Instant windowOver = NOW.plus(WINDOW);

    // When
    OriginAttempts state = afterAttempts(3).block(windowOver, LIMITS);

    // Then
    assertThat(state)
        .extracting(
            OriginAttempts::attempts, OriginAttempts::windowStartedAt, OriginAttempts::blockedUntil)
        .containsExactly(0, windowOver, null);
  }

  @Test
  void recordAttempt_duringTheBlock_doesNotCount() {
    // Given
    OriginAttempts state = blocked();

    // When
    OriginAttempts again = state.recordAttempt(REJECTED.plusSeconds(60), LIMITS);

    // Then
    assertThat(again).isEqualTo(state);
  }

  @Test
  void isBlocked_atTheExpiryInstant_isFalse() {
    // Given
    OriginAttempts state = blocked();

    // Then — die letzte Nanosekunde gehört noch zur Sperre, der Ablaufzeitpunkt nicht mehr.
    assertThat(state.isBlocked(BLOCK_OVER.minusNanos(1))).isTrue();
    assertThat(state.isBlocked(BLOCK_OVER)).isFalse();
  }

  @Test
  void recordAttempt_afterTheBlockExpired_resetsTheStateCompletely() {
    // Given — das Zählfenster läuft hier noch, allein das Ende der Sperre setzt zurück.
    OriginAttempts state = blocked();

    // When
    OriginAttempts afterExpiry = state.recordAttempt(BLOCK_OVER, LIMITS);

    // Then
    assertThat(afterExpiry)
        .extracting(
            OriginAttempts::attempts, OriginAttempts::windowStartedAt, OriginAttempts::blockedUntil)
        .containsExactly(1, BLOCK_OVER, null);
    assertThat(afterExpiry.block(BLOCK_OVER, LIMITS).isBlocked(BLOCK_OVER)).isFalse();
  }

  @Test
  void retryAfter_whileBlocked_isTheRemainingTime() {
    // Given / When / Then
    assertThat(blocked().retryAfter(REJECTED.plusSeconds(60))).isEqualTo(Duration.ofMinutes(4));
  }

  @Test
  void retryAfter_withoutBlock_isZero() {
    // Given / When / Then
    assertThat(afterAttempts(3).retryAfter(NOW)).isEqualTo(Duration.ZERO);
  }

  @Test
  void retryAfter_afterTheBlockExpired_isZero() {
    // Given / When / Then
    assertThat(blocked().retryAfter(BLOCK_OVER)).isEqualTo(Duration.ZERO);
  }
}
