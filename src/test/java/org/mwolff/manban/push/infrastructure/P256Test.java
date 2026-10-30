package org.mwolff.manban.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Prüft die Formprüfung der P-256-Schlüssel (Issue #1996): Länge und Kennbyte. */
class P256Test {

  @Test
  void einPunktMitFalscherLaengeWirdAbgewiesen() {
    byte[] kurz = new byte[P256.PUNKT_LAENGE - 1];
    kurz[0] = 0x04;

    assertThatThrownBy(() -> P256.oeffentlicherSchluessel(kurz))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void einPunktOhneKennbyte04WirdAbgewiesen() {
    byte[] komprimiert = new byte[P256.PUNKT_LAENGE];
    komprimiert[0] = 0x02;

    assertThatThrownBy(() -> P256.oeffentlicherSchluessel(komprimiert))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void einSkalarMitFalscherLaengeWirdAbgewiesen() {
    byte[] punkt = new byte[P256.PUNKT_LAENGE];
    punkt[0] = 0x04;

    assertThatThrownBy(() -> P256.schluesselpaar(punkt, new byte[31]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Skalar");
  }
}
