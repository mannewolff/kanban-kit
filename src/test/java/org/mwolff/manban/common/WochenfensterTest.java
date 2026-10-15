package org.mwolff.manban.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Zwölf rollende 7-Tage-Fenster bis „jetzt“ (Issue #1753, Plan #1752 E6). */
class WochenfensterTest {

  private static final Instant JETZT = Instant.parse("2026-10-15T12:00:00Z");
  private static final Duration WOCHE = Duration.ofDays(7);

  @Test
  void anzahl_istZwoelf() {
    assertThat(Wochenfenster.ANZAHL).isEqualTo(12);
  }

  @Test
  void index_liefertJuengstesFenster_fuerJetzt() {
    assertThat(Wochenfenster.index(JETZT, JETZT)).isEqualTo(OptionalInt.of(11));
  }

  @Test
  void index_liefertJuengstesFenster_kurzVorDerGrenze() {
    Instant zeitpunkt = JETZT.minus(WOCHE).plusSeconds(1);
    assertThat(Wochenfenster.index(zeitpunkt, JETZT)).isEqualTo(OptionalInt.of(11));
  }

  @Test
  void index_liefertVorletztesFenster_genauAnDerGrenze() {
    assertThat(Wochenfenster.index(JETZT.minus(WOCHE), JETZT)).isEqualTo(OptionalInt.of(10));
  }

  @Test
  void index_liefertAeltestesFenster_kurzVorDessenAnfang() {
    Instant zeitpunkt = JETZT.minus(WOCHE.multipliedBy(12)).plusSeconds(1);
    assertThat(Wochenfenster.index(zeitpunkt, JETZT)).isEqualTo(OptionalInt.of(0));
  }

  @Test
  void index_liefertLeer_vorDemAeltestenFenster() {
    Instant zeitpunkt = JETZT.minus(WOCHE.multipliedBy(12));
    assertThat(Wochenfenster.index(zeitpunkt, JETZT)).isEmpty();
  }

  @Test
  void index_liefertLeer_nachJetzt() {
    assertThat(Wochenfenster.index(JETZT.plusSeconds(1), JETZT)).isEmpty();
  }

  @Test
  void beginn_liefertAnfangDesJuengstenFensters() {
    assertThat(Wochenfenster.beginn(11, JETZT)).isEqualTo(JETZT.minus(WOCHE));
  }

  @Test
  void beginn_liefertAnfangDesAeltestenFensters() {
    assertThat(Wochenfenster.beginn(0, JETZT)).isEqualTo(JETZT.minus(WOCHE.multipliedBy(12)));
  }

  @Test
  void beginn_passtZuIndex() {
    // Der Beginn eines Fensters liegt in genau diesem Fenster.
    for (int i = 0; i < Wochenfenster.ANZAHL; i++) {
      Instant beginn = Wochenfenster.beginn(i, JETZT);
      assertThat(Wochenfenster.index(beginn.plusSeconds(1), JETZT)).isEqualTo(OptionalInt.of(i));
    }
  }
}
