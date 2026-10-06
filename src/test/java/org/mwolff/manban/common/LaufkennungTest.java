package org.mwolff.manban.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Auslesen der Laufkennung aus dem Header {@code X-Night-Run} (Issue #1426, Plan #1423). */
class LaufkennungTest {

  @Test
  void ausHeader_liefertDenInstant_fuerGueltigenWert() {
    // When / Then: getrimmt und geparst, wie der Runner ihn mit toISOString() schickt.
    assertThat(Laufkennung.ausHeader("  2026-10-05T08:58:22.123Z  "))
        .isEqualTo(Instant.parse("2026-10-05T08:58:22.123Z"));
  }

  @Test
  void ausHeader_liefertNull_ohneHeader() {
    assertThat(Laufkennung.ausHeader(null)).isNull();
  }

  @Test
  void ausHeader_liefertNull_fuerLeerenWert() {
    assertThat(Laufkennung.ausHeader("")).isNull();
  }

  @Test
  void ausHeader_liefertNull_fuerNurLeerraum() {
    assertThat(Laufkennung.ausHeader("   ")).isNull();
  }

  @Test
  void ausHeader_liefertNull_fuerMehrAls40Zeichen() {
    // Given: ein an sich gültiger Instant mit 41 Zeichen — verworfen allein wegen der Länge.
    String wert = "+999999999-12-31T23:59:59.999999999+01:00";

    // When / Then
    assertThat(wert).hasSize(41);
    assertThat(Laufkennung.ausHeader(wert)).isNull();
  }

  @Test
  void ausHeader_nimmtGenau40ZeichenAn() {
    // Given: die Grenze selbst ist noch erlaubt.
    String wert = "+999999999-12-31T23:59:59.99999999+01:00";

    // When / Then
    assertThat(wert).hasSize(40);
    assertThat(Laufkennung.ausHeader(wert))
        .isEqualTo(Instant.parse("+999999999-12-31T22:59:59.999Z"));
  }

  @Test
  void ausHeader_liefertNull_fuerUngueltigenWert() {
    assertThat(Laufkennung.ausHeader("gestern abend")).isNull();
  }

  @Test
  void ausHeader_kuerztMikrosekundenAufMillisekunden() {
    assertThat(Laufkennung.ausHeader("2026-10-05T08:58:22.123456Z"))
        .isEqualTo(Instant.parse("2026-10-05T08:58:22.123Z"));
  }
}
