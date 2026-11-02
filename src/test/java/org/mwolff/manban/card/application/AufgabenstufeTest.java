package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit-Tests der Aufgabenstufe aus der Beschreibung eines Pakets (Issue #2001, Plan #1990 E5): die
 * erste Zeile mit {@code Aufgabenstufe:} zählt, ihr Rest muss genau eine der drei Stufen sein.
 */
class AufgabenstufeTest {

  @ParameterizedTest
  @CsvSource({"leicht, LEICHT", "mittel, MITTEL", "schwer, SCHWER"})
  void dieDreiWerteWerdenGelesen(String wert, Aufgabenstufe erwartet) {
    assertThat(Aufgabenstufe.ausBeschreibung("## Kontext\nAufgabenstufe: " + wert + "\nRest"))
        .contains(erwartet);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Mittel", "MITTEL", "mItTeL"})
  void grossUndKleinschreibungZaehltNicht(String wert) {
    assertThat(Aufgabenstufe.ausBeschreibung("Aufgabenstufe: " + wert))
        .contains(Aufgabenstufe.MITTEL);
  }

  @Test
  void leerraumUmDenWertUndWindowsZeilenendenZaehlenNicht() {
    assertThat(Aufgabenstufe.ausBeschreibung("Kontext\r\nAufgabenstufe:   schwer  \r\nRest"))
        .contains(Aufgabenstufe.SCHWER);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Aufgabenstufe: leicht (Vorschlag)",
        "Aufgabenstufe: sehr schwer",
        "Aufgabenstufe:",
        "Aufgabenstufe: unbekannt"
      })
  void einAndererRestIstOhneStufe(String zeile) {
    assertThat(Aufgabenstufe.ausBeschreibung(zeile)).isEmpty();
  }

  @Test
  void ohneZeileIstOhneStufe() {
    assertThat(Aufgabenstufe.ausBeschreibung("## Kontext\nStufengrund: leicht")).isEmpty();
  }

  @Test
  void ohneBeschreibungIstOhneStufe() {
    assertThat(Aufgabenstufe.ausBeschreibung(null)).isEmpty();
  }

  /** Nur eine Zeile, die selbst mit dem Marker beginnt — nicht eingerückt, nicht mitten im Satz. */
  @Test
  void eineEingerueckteZeileZaehltNicht() {
    assertThat(Aufgabenstufe.ausBeschreibung("  Aufgabenstufe: leicht")).isEmpty();
  }

  @Test
  void eineZweiteZeileWirdIgnoriert() {
    assertThat(Aufgabenstufe.ausBeschreibung("Aufgabenstufe: mittel\nAufgabenstufe: schwer"))
        .contains(Aufgabenstufe.MITTEL);
  }

  /** Die erste Zeile entscheidet, auch wenn sie keine gültige Stufe trägt. */
  @Test
  void eineZweiteZeileRettetEineUngueltigeErsteNicht() {
    assertThat(Aufgabenstufe.ausBeschreibung("Aufgabenstufe: ?\nAufgabenstufe: schwer")).isEmpty();
  }
}
