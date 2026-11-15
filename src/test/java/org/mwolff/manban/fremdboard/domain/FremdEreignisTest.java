package org.mwolff.manban.fremdboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Ein Wechsel an einer Karte im fremden System (Issue #2158, Plan #2152 A4, A15). */
class FremdEreignisTest {

  private static final Instant ZEIT = Instant.parse("2026-11-15T10:00:00Z");

  @Test
  void zustandswechsel_ohneLabelUndOhneErfasstenAkteur() {
    FremdEreignis ereignis =
        new FremdEreignis("state-9", 7, FremdEreignis.Art.GESCHLOSSEN, null, ZEIT, null);

    assertThat(ereignis.label()).isNull();
    assertThat(ereignis.akteur()).isNull();
    assertThat(ereignis.zeit()).isEqualTo(ZEIT);
  }

  @Test
  void gleicheKennungUndAngaben_sindDasselbeEreignis() {
    FremdEreignis a =
        new FremdEreignis("label-3", 7, FremdEreignis.Art.LABEL_GESETZT, "Ready", ZEIT, "kit");
    FremdEreignis b =
        new FremdEreignis("label-3", 7, FremdEreignis.Art.LABEL_GESETZT, "Ready", ZEIT, "kit");

    assertThat(a).isEqualTo(b);
  }
}
