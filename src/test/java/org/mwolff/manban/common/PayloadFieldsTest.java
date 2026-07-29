package org.mwolff.manban.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Kodierung der Outbox-Payload-Felder (Issue #502). */
class PayloadFieldsTest {

  @Test
  void join_thenSplit_roundtripsPlainFields() {
    // Given / When
    String payload = PayloadFields.join("alice@example.org", "https://app/verify?token=abc");

    // Then
    assertThat(PayloadFields.split(payload, 2))
        .containsExactly("alice@example.org", "https://app/verify?token=abc");
  }

  @Test
  void join_thenSplit_survivesSeparatorAndSpecialCharacters() {
    // Given — Zeilenumbruch, Umlaute und das Trennzeichen selbst dürfen Felder nicht zerreißen.
    String tricky = "Zeile1\nZeile2 & Söhne %20+";

    // When
    String payload = PayloadFields.join(tricky, "zweites Feld");

    // Then
    assertThat(PayloadFields.split(payload, 2)).containsExactly(tricky, "zweites Feld");
  }

  @Test
  void join_thenSplit_keepsEmptyFields() {
    // Given / When
    String payload = PayloadFields.join("", "mitte", "");

    // Then
    assertThat(PayloadFields.split(payload, 3)).containsExactly("", "mitte", "");
  }

  @Test
  void split_rejectsWrongFieldCount() {
    // Given
    String payload = PayloadFields.join("nur", "zwei");

    // When / Then — kaputte Payload endet als Fehlversuch, nicht als stilles Falsch-Parsen.
    assertThatThrownBy(() -> PayloadFields.split(payload, 3))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2")
        .hasMessageContaining("3");
  }
}
