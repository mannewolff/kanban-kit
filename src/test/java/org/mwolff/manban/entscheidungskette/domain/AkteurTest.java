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
    assertThat(Akteur.nachHerkunft("SESSION", 7L, null, null, null).gemeldet()).isFalse();
    assertThat(Akteur.nachHerkunft("TOKEN", null, "Kit", null, null).gemeldet()).isFalse();
  }

  /** Ein Benutzer im fremden System steht mit seinem Namen da (Issue #2165, Plan #2152 A11). */
  @Test
  void einBenutzerImFremdenSystemErscheintMitSeinemNamen() {
    assertThat(Akteur.nachHerkunft("SESSION", null, null, null, "erika"))
        .isEqualTo(Akteur.menschGenannt("erika"));
    assertThat(Akteur.nachHerkunft(null, null, null, null, "erika"))
        .isEqualTo(Akteur.menschGenannt("erika"));
    assertThat(Akteur.nachHerkunft("TOKEN", null, null, null, "kit-bot"))
        .isEqualTo(Akteur.werkzeug("kit-bot", null));
    assertThat(Akteur.nachHerkunft("TOKEN", null, "Kit", "claude", "kit-bot"))
        .isEqualTo(Akteur.werkzeug("Kit", "claude"));
  }

  @Test
  void ohneHerkunftUndOhneFremdenBenutzerGibtEsKeinenAkteur() {
    assertThat(Akteur.nachHerkunft(null, 7L, "Kit", "claude", null)).isNull();
  }
}
