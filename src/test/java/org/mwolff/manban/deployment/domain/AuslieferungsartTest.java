package org.mwolff.manban.deployment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Wert und Rückweg der Auslieferungsart (Issue #2215; Mutanten aus Issue #2239). */
class AuslieferungsartTest {

  @Test
  void wert_nenntDenTextAusMeldungUndDatenbank() {
    assertThat(Auslieferungsart.VORSCHAU.wert()).isEqualTo("vorschau");
    assertThat(Auslieferungsart.VEROEFFENTLICHUNG.wert()).isEqualTo("veroeffentlichung");
  }

  @Test
  void aus_findetDieArtZumWert() {
    assertThat(Auslieferungsart.aus("vorschau")).isEqualTo(Auslieferungsart.VORSCHAU);
    assertThat(Auslieferungsart.aus("veroeffentlichung"))
        .isEqualTo(Auslieferungsart.VEROEFFENTLICHUNG);
  }

  @Test
  void aus_weistEinenUnbekanntenWertMitIllegalArgumentExceptionAb() {
    assertThatThrownBy(() -> Auslieferungsart.aus("probe"))
        .isExactlyInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unbekannte Auslieferungsart: probe");
  }
}
