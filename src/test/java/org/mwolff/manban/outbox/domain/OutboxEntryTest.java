package org.mwolff.manban.outbox.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Zustandsübergänge eines Outbox-Eintrags (Issue #501). */
class OutboxEntryTest {

  private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");
  private static final Duration BASE = Duration.ofSeconds(10);
  private static final Duration MAX = Duration.ofHours(1);

  private static OutboxEntry pending() {
    return OutboxEntry.pending("mail.verification", "verify:42", "userId=42", NOW);
  }

  @Test
  void pending_startsUnattemptedAndImmediatelyDue() {
    // Given / When
    OutboxEntry entry = pending();

    // Then
    assertThat(entry)
        .extracting(
            OutboxEntry::id,
            OutboxEntry::status,
            OutboxEntry::attempts,
            OutboxEntry::createdAt,
            OutboxEntry::nextAttemptAt,
            OutboxEntry::completedAt,
            OutboxEntry::lastError)
        .containsExactly(null, OutboxStatus.PENDING, 0, NOW, NOW, null, null);
  }

  @Test
  void pending_keepsEventTypeKeyAndPayload() {
    // Given / When
    OutboxEntry entry = pending();

    // Then
    assertThat(entry)
        .extracting(OutboxEntry::eventType, OutboxEntry::idempotencyKey, OutboxEntry::payload)
        .containsExactly("mail.verification", "verify:42", "userId=42");
  }

  @Test
  void completed_marksDoneCountsTheAttemptAndClearsThePayload() {
    // Given
    OutboxEntry entry = pending();

    // When
    OutboxEntry done = entry.completed(NOW.plusSeconds(5));

    // Then
    assertThat(done)
        .extracting(
            OutboxEntry::status,
            OutboxEntry::attempts,
            OutboxEntry::completedAt,
            OutboxEntry::payload)
        .containsExactly(OutboxStatus.DONE, 1, NOW.plusSeconds(5), "");
  }

  @Test
  void completed_keepsIdentityAndDropsAnyEarlierError() {
    // Given
    OutboxEntry retried = pending().afterFailedAttempt(NOW, 5, BASE, MAX, "SMTP weg");

    // When
    OutboxEntry done = retried.completed(NOW.plusSeconds(30));

    // Then
    assertThat(done)
        .extracting(OutboxEntry::eventType, OutboxEntry::idempotencyKey, OutboxEntry::lastError)
        .containsExactly("mail.verification", "verify:42", null);
  }

  @Test
  void afterFailedAttempt_belowTheLimitSchedulesRetryAndKeepsThePayload() {
    // Given
    OutboxEntry entry = pending();

    // When
    OutboxEntry retried = entry.afterFailedAttempt(NOW, 5, BASE, MAX, "SMTP weg");

    // Then
    assertThat(retried)
        .extracting(
            OutboxEntry::status,
            OutboxEntry::attempts,
            OutboxEntry::nextAttemptAt,
            OutboxEntry::payload,
            OutboxEntry::lastError,
            OutboxEntry::completedAt)
        .containsExactly(OutboxStatus.PENDING, 1, NOW.plus(BASE), "userId=42", "SMTP weg", null);
  }

  @ParameterizedTest(name = "Versuch {0} wartet {1} Sekunden")
  @CsvSource({"0, 10", "1, 20", "2, 40", "3, 80"})
  void afterFailedAttempt_backsOffExponentially(int previousAttempts, long expectedSeconds) {
    // Given
    OutboxEntry entry = withAttempts(previousAttempts);

    // When
    OutboxEntry retried = entry.afterFailedAttempt(NOW, 99, BASE, MAX, "weg");

    // Then
    assertThat(retried.nextAttemptAt()).isEqualTo(NOW.plusSeconds(expectedSeconds));
  }

  @Test
  void afterFailedAttempt_capsTheBackoffAtTheConfiguredMaximum() {
    // Given
    OutboxEntry entry = withAttempts(20);

    // When
    OutboxEntry retried = entry.afterFailedAttempt(NOW, 99, BASE, MAX, "weg");

    // Then
    assertThat(retried.nextAttemptAt()).isEqualTo(NOW.plus(MAX));
  }

  @Test
  void afterFailedAttempt_capsTheShiftSoAnExtremeAttemptCountCannotOverflow() {
    // Given
    OutboxEntry entry = withAttempts(70);

    // When
    OutboxEntry retried = entry.afterFailedAttempt(NOW, Integer.MAX_VALUE, BASE, MAX, "weg");

    // Then
    assertThat(retried.nextAttemptAt()).isEqualTo(NOW.plus(MAX));
  }

  @Test
  void afterFailedAttempt_atTheLimitGivesUpAndClearsThePayload() {
    // Given
    OutboxEntry entry = withAttempts(2);

    // When
    OutboxEntry failed = entry.afterFailedAttempt(NOW, 3, BASE, MAX, "endgültig weg");

    // Then
    assertThat(failed)
        .extracting(
            OutboxEntry::status,
            OutboxEntry::attempts,
            OutboxEntry::completedAt,
            OutboxEntry::payload,
            OutboxEntry::lastError)
        .containsExactly(OutboxStatus.FAILED, 3, NOW, "", "endgültig weg");
    assertThat(failed.nextAttemptAt()).isEqualTo(NOW);
  }

  @Test
  void afterFailedAttempt_beyondTheLimitAlsoGivesUp() {
    // Given
    OutboxEntry entry = withAttempts(4);

    // When
    OutboxEntry failed = entry.afterFailedAttempt(NOW, 3, BASE, MAX, "weg");

    // Then
    assertThat(failed.status()).isEqualTo(OutboxStatus.FAILED);
  }

  @Test
  void afterFailedAttempt_truncatesAnOverlongErrorToTheColumnWidth() {
    // Given
    String tooLong = "x".repeat(OutboxEntry.MAX_ERROR_LENGTH + 50);

    // When
    OutboxEntry retried = pending().afterFailedAttempt(NOW, 5, BASE, MAX, tooLong);

    // Then
    assertThat(retried.lastError()).hasSize(OutboxEntry.MAX_ERROR_LENGTH);
  }

  @Test
  void afterFailedAttempt_keepsAnErrorThatFitsUnchanged() {
    // Given
    String exactFit = "x".repeat(OutboxEntry.MAX_ERROR_LENGTH);

    // When
    OutboxEntry retried = pending().afterFailedAttempt(NOW, 5, BASE, MAX, exactFit);

    // Then
    assertThat(retried.lastError()).isEqualTo(exactFit);
  }

  private static OutboxEntry withAttempts(int attempts) {
    return new OutboxEntry(
        7L,
        "mail.verification",
        "verify:42",
        "userId=42",
        OutboxStatus.PENDING,
        attempts,
        NOW,
        NOW,
        null,
        null);
  }
}
