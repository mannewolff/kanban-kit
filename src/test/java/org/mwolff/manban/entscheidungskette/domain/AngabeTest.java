package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Das Zerlegen einer Angabe {@code <wert>, <zeitpunkt>} an ihren Grenzen (Issue #2133). */
class AngabeTest {

  private static final Instant T1 = Instant.parse("2026-11-10T10:00:00Z");

  @Test
  void aus_trennerAmAnfang_liestZeitpunktOhneText() {
    // Given: der Trenner steht an Position 0, vor ihm kein Text
    String roh = ", 2026-11-10T10:00:00Z";

    // When
    Angabe angabe = Angabe.aus(roh);

    // Then
    assertThat(angabe).isEqualTo(new Angabe(null, T1));
  }

  @Test
  void aus_ohneTrenner_suchtKeinenZeitpunkt() {
    // Given: ohne Trenner; ab dem zweiten Zeichen stünde ein gültiger Zeitpunkt
    String roh = "x2026-11-10T10:00:00Z";

    // When
    Angabe angabe = Angabe.aus(roh);

    // Then
    assertThat(angabe).isEqualTo(new Angabe(roh, null));
  }
}
