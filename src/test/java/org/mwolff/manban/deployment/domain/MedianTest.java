package org.mwolff.manban.deployment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/** Der Median über Dauern (Issue #1832, Plan #1831 E8). */
class MedianTest {

  @Test
  void ungeradeAnzahl_liefertDenMittlerenWert_unabhaengigVonDerReihenfolge() {
    assertThat(Median.von(List.of(30L, 10L, 20L))).isEqualTo(OptionalLong.of(20));
  }

  @Test
  void geradeAnzahl_liefertDasMittelDerBeidenMittlerenWerte() {
    assertThat(Median.von(List.of(40L, 10L, 30L, 20L))).isEqualTo(OptionalLong.of(25));
  }

  @Test
  void geradeAnzahl_rundetEinHalbesAb() {
    assertThat(Median.von(List.of(1L, 2L))).isEqualTo(OptionalLong.of(1));
  }

  @Test
  void geradeAnzahl_ohneUeberlaufBeiGrossenWerten() {
    assertThat(Median.von(List.of(Long.MAX_VALUE, Long.MAX_VALUE - 2)))
        .isEqualTo(OptionalLong.of(Long.MAX_VALUE - 1));
  }

  @Test
  void einWert_liefertDiesenWert() {
    assertThat(Median.von(List.of(7L))).isEqualTo(OptionalLong.of(7));
  }

  @Test
  void leereEingabe_liefertLeer() {
    assertThat(Median.von(List.of())).isEmpty();
  }
}
