package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Die Abweichung der tatsächlichen Kosten von einer festgehaltenen Voraussage (Issue #2004, Plan
 * #1990 E13; fachlich #1954 AK 8): auf ganze Prozent mit {@code HALF_UP}, ohne Abweichung bei
 * „unvollständig“, bei einer Voraussage von 0 und bei „keine Voraussage“.
 */
class KostenVergleichTest {

  private static BigDecimal usd(String betrag) {
    return new BigDecimal(betrag);
  }

  @Test
  @DisplayName("Teurer als vorausgesagt: 18 $ zu 16 $ ist +12,5 % und rundet auf +13")
  void plusZwoelfEinhalbRundetAufDreizehn() {
    assertThat(KostenVergleich.abweichung(usd("18"), false, usd("16"))).hasValue(13);
  }

  @Test
  @DisplayName("Billiger als vorausgesagt: 14 $ zu 16 $ ist −12,5 % und rundet auf −13")
  void minusZwoelfEinhalbRundetAufMinusDreizehn() {
    assertThat(KostenVergleich.abweichung(usd("14"), false, usd("16"))).hasValue(-13);
  }

  @Test
  @DisplayName("18 $ zu 20 $ ist −10 %")
  void minusZehn() {
    assertThat(KostenVergleich.abweichung(usd("18"), false, usd("20"))).hasValue(-10);
  }

  @Test
  @DisplayName("Genau getroffen ist 0 %")
  void getroffen() {
    assertThat(KostenVergleich.abweichung(usd("20.00"), false, usd("20"))).hasValue(0);
  }

  @Test
  @DisplayName("Unter einem halben Prozent rundet ab")
  void unterHalbRundetAb() {
    assertThat(KostenVergleich.abweichung(usd("100.49"), false, usd("100"))).hasValue(0);
  }

  @Test
  @DisplayName("Unvollständige tatsächliche Kosten haben keine Abweichung")
  void unvollstaendigOhneAbweichung() {
    assertThat(KostenVergleich.abweichung(usd("18"), true, usd("16"))).isEmpty();
  }

  @Test
  @DisplayName("Eine Voraussage von 0 hat keine Abweichung")
  void voraussageNullOhneAbweichung() {
    assertThat(KostenVergleich.abweichung(usd("18"), false, usd("0.00"))).isEmpty();
  }

  @Test
  @DisplayName("Ohne bezifferte Voraussage gibt es keine Abweichung")
  void keineVoraussageOhneAbweichung() {
    assertThat(KostenVergleich.abweichung(usd("18"), false, null)).isEmpty();
  }

  @Test
  @DisplayName("Ohne tatsächliche Kosten gibt es keine Abweichung")
  void ohneTatsaechlicheKosten() {
    assertThat(KostenVergleich.abweichung(null, false, usd("16"))).isEmpty();
  }
}
