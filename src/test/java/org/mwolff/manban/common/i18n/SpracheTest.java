package org.mwolff.manban.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Die beiden Sprachen der Installation mit Code und Locale (Issue #1639, Plan #1638). */
class SpracheTest {

  @Test
  void deutschTraegtCodeUndLocale() {
    assertThat(Sprache.DE.code()).isEqualTo("de");
    assertThat(Sprache.DE.locale()).isEqualTo(Locale.forLanguageTag("de-DE"));
  }

  @Test
  void englischTraegtCodeUndUsLocale() {
    assertThat(Sprache.EN.code()).isEqualTo("en");
    assertThat(Sprache.EN.locale()).isEqualTo(Locale.forLanguageTag("en-US"));
  }

  @Test
  void ausCodeFindetJedeSprache() {
    assertThat(Sprache.ausCode("de")).isEqualTo(Sprache.DE);
    assertThat(Sprache.ausCode("en")).isEqualTo(Sprache.EN);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "fr", "DE", "EN", " de", "de-DE"})
  void ausCodeWeistUnbekannteCodesAb(String code) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Sprache.ausCode(code))
        .withMessageContaining("Unbekannte Sprache");
  }
}
