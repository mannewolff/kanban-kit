package org.mwolff.manban.codeanalyse.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Eine Messung einer Veröffentlichung (Issue #2273, Plan #2272 E7, E9; Fachplan #2271 AK 16). */
class MessungTest {

  private static final Instant ZEIT = Instant.parse("2026-11-25T12:00:00Z");
  private static final Zustand ZUSTAND = new Zustand(1, 0, 2, 50);

  private static Messung messung(
      boolean zaehlt, String grund, int karten, Zustand vorher, Zustand endzustand) {
    return new Messung(
        "abc123", ZEIT, "def456", "1.2.3", zaehlt, grund, karten, vorher, endzustand);
  }

  @Test
  void zaehlendeMessung_mitBeidenZustaenden_wirdAngenommen() {
    Messung m = messung(true, null, 3, ZUSTAND, ZUSTAND);

    assertThat(m.zaehlt()).isTrue();
    assertThat(m.grund()).isNull();
    assertThat(m.karten()).isEqualTo(3);
    assertThat(m.publishedAt()).isEqualTo(ZEIT);
  }

  @Test
  void zaehlendeMessung_ohneZustandVorher_wirdAbgewiesen() {
    assertThatThrownBy(() -> messung(true, null, 1, null, ZUSTAND))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("vorher");
  }

  @Test
  void zaehlendeMessung_ohneEndzustand_wirdAbgewiesen() {
    assertThatThrownBy(() -> messung(true, null, 1, ZUSTAND, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Endzustand");
  }

  @Test
  void zaehlendeMessung_mitGrund_wirdAbgewiesen() {
    assertThatThrownBy(() -> messung(true, "incomplete", 1, ZUSTAND, ZUSTAND))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Grund");
  }

  @Test
  void nichtZaehlendeMessung_ohneZustaende_mitGrund_wirdAngenommen() {
    Messung m = messung(false, "missing-before", 0, null, null);

    assertThat(m.zaehlt()).isFalse();
    assertThat(m.grund()).isEqualTo("missing-before");
    assertThat(m.vorher()).isNull();
    assertThat(m.endzustand()).isNull();
  }

  @Test
  void nichtZaehlendeMessung_ohneGrund_wirdAbgewiesen() {
    assertThatThrownBy(() -> messung(false, null, 0, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Grund");
    assertThatThrownBy(() -> messung(false, "  ", 0, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Grund");
  }

  @Test
  void negativeKartenzahl_wirdAbgewiesen() {
    assertThatThrownBy(() -> messung(true, null, -1, ZUSTAND, ZUSTAND))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("karten");
  }
}
