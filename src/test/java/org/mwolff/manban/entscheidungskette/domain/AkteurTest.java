package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Nur ein Mensch, den eine Meldung des Kits nennt, gilt als gemeldet (Issue #2146). */
class AkteurTest {

  @Test
  void nurDerGenannteMenschIstGemeldet() {
    assertThat(Akteur.menschGenannt("Erika Muster").gemeldet()).isTrue();
    assertThat(Akteur.mensch(7L).gemeldet()).isFalse();
    assertThat(Akteur.modell("claude-opus-5-5").gemeldet()).isFalse();
    assertThat(Akteur.werkzeug("Kit", "claude-opus-5-5").gemeldet()).isFalse();
    assertThat(Akteur.nachHerkunft("SESSION", 7L, null, null).gemeldet()).isFalse();
    assertThat(Akteur.nachHerkunft("TOKEN", null, "Kit", null).gemeldet()).isFalse();
  }
}
