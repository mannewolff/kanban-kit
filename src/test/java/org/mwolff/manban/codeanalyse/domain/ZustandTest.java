package org.mwolff.manban.codeanalyse.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Ein Zustand einer Messung (Issue #2273, Plan #2272 E7). */
class ZustandTest {

  @Test
  void funde_sindDieSummeDerDreiArten() {
    assertThat(new Zustand(1, 2, 3, 100).funde()).isEqualTo(6);
  }

  @Test
  void funde_laufenNichtUeber() {
    Zustand gross = new Zustand(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 0);

    assertThat(gross.funde()).isEqualTo(3L * Integer.MAX_VALUE);
  }

  @Test
  void nullen_sindErlaubt() {
    assertThat(new Zustand(0, 0, 0, 0).funde()).isZero();
  }

  @Test
  void negativeZahlen_werdenAbgewiesen() {
    assertThatThrownBy(() -> new Zustand(-1, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fehler");
    assertThatThrownBy(() -> new Zustand(0, -1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sicherheitsluecken");
    assertThatThrownBy(() -> new Zustand(0, 0, -1, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("wartbarkeitsmaengel");
    assertThatThrownBy(() -> new Zustand(0, 0, 0, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("zeilen");
  }
}
