package org.mwolff.manban.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Quantil linear interpoliert, Typ 7 (Issue #2002, Plan #1990 E4). */
class QuantilTest {

  private static final BigDecimal P15 = new BigDecimal("0.15");
  private static final BigDecimal P50 = new BigDecimal("0.5");
  private static final BigDecimal P85 = new BigDecimal("0.85");

  private static List<BigDecimal> werte(String... betraege) {
    return Arrays.stream(betraege).map(BigDecimal::new).toList();
  }

  @Test
  void leereListe_ergibtKeinQuantil() {
    assertThat(Quantil.von(List.of(), P50)).isEmpty();
  }

  @Test
  void einWert_istJedesQuantil() {
    List<BigDecimal> einer = werte("7.25");

    assertThat(Quantil.von(einer, P15))
        .hasValueSatisfying(q -> assertThat(q).isEqualByComparingTo("7.25"));
    assertThat(Quantil.von(einer, P50))
        .hasValueSatisfying(q -> assertThat(q).isEqualByComparingTo("7.25"));
    assertThat(Quantil.von(einer, P85))
        .hasValueSatisfying(q -> assertThat(q).isEqualByComparingTo("7.25"));
  }

  @Test
  void zweiWerte_interpolierenLinear() {
    List<BigDecimal> zwei = werte("20", "10");

    assertThat(Quantil.von(zwei, P15))
        .hasValueSatisfying(q -> assertThat(q).isEqualByComparingTo("11.5"));
    assertThat(Quantil.von(zwei, P50))
        .hasValueSatisfying(q -> assertThat(q).isEqualByComparingTo("15"));
    assertThat(Quantil.von(zwei, P85))
        .hasValueSatisfying(q -> assertThat(q).isEqualByComparingTo("18.5"));
  }

  /** h = (n − 1) · p; unsortierte Eingabe wird sortiert. */
  @Test
  void vieleWerte_interpolierenZwischenDenNachbarn() {
    List<BigDecimal> viele = werte("4", "1", "3", "2", "10");

    // h = 0,6 → 1 + 0,6 · (2 − 1)
    assertThat(Quantil.von(viele, P15))
        .hasValueSatisfying(q -> assertThat(q).isEqualByComparingTo("1.6"));
    // h = 2 → genau der mittlere Wert
    assertThat(Quantil.von(viele, P50))
        .hasValueSatisfying(q -> assertThat(q).isEqualByComparingTo("3"));
    // h = 3,4 → 4 + 0,4 · (10 − 4)
    assertThat(Quantil.von(viele, P85))
        .hasValueSatisfying(q -> assertThat(q).isEqualByComparingTo("6.4"));
  }

  @Test
  void geradeAnzahl_medianIstDasMittelDerBeidenMittleren() {
    assertThat(Quantil.von(werte("1", "2", "3", "4"), P50))
        .hasValueSatisfying(q -> assertThat(q).isEqualByComparingTo("2.5"));
  }

  @Test
  void dieRaender_sindKleinsterUndGroessterWert() {
    List<BigDecimal> viele = werte("5", "1", "9");

    assertThat(Quantil.von(viele, BigDecimal.ZERO))
        .hasValueSatisfying(q -> assertThat(q).isEqualByComparingTo("1"));
    assertThat(Quantil.von(viele, BigDecimal.ONE))
        .hasValueSatisfying(q -> assertThat(q).isEqualByComparingTo("9"));
  }

  @Test
  void anteilAusserhalbVonNullBisEins_wirdAbgewiesen() {
    List<BigDecimal> einer = werte("1");

    assertThatThrownBy(() -> Quantil.von(einer, new BigDecimal("-0.01")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Quantil.von(einer, new BigDecimal("1.01")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
