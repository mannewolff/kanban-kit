package org.mwolff.manban.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Invarianten einer einzuplanenden Outbox-Nachricht (Issue #501). */
class OutboxMessageTest {

  @Test
  void valuesAreKept() {
    // Given / When
    OutboxMessage message = new OutboxMessage("mail.verification", "verify:42", "userId=42");

    // Then
    assertThat(message)
        .extracting(OutboxMessage::eventType, OutboxMessage::idempotencyKey, OutboxMessage::payload)
        .containsExactly("mail.verification", "verify:42", "userId=42");
  }

  @Test
  void emptyPayloadIsAllowed() {
    // Given / When
    OutboxMessage message = new OutboxMessage("mail.verification", "verify:42", "");

    // Then
    assertThat(message.payload()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void blankEventTypeIsRejected(String blank) {
    // Given / When / Then
    assertThatThrownBy(() -> new OutboxMessage(blank, "verify:42", "userId=42"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Ereignistyp");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void blankIdempotencyKeyIsRejected(String blank) {
    // Given / When / Then
    assertThatThrownBy(() -> new OutboxMessage("mail.verification", blank, "userId=42"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Idempotenzschlüssel");
  }
}
