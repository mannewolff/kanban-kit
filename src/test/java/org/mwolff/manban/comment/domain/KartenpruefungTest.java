package org.mwolff.manban.comment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Welche Kartenprüfung ein Kunde nicht sieht (Issue #2214, Plan #2206 E2): jede, deren Zeile {@code
 * Ergebnis:}/{@code Result:} nicht grün meldet, und jede ohne diese Zeile.
 */
class KartenpruefungTest {

  private static String pruefung(String kopf, String ergebnisZeile) {
    return kopf
        + "\n\nDie Prüfung ist durchgelaufen.\n\n### Einzelheiten für den Support\n\n"
        + ergebnisZeile
        + "\nCommit: abc123\n";
  }

  @ParameterizedTest
  @ValueSource(strings = {"Ergebnis: gruen", "Result: green", "Ergebnis:   gruen  "})
  void eineGrueneKartenpruefungBleibtSichtbar(String zeile) {
    assertThat(Kartenpruefung.istVerborgen(pruefung("## Kartenprüfung", zeile))).isFalse();
    assertThat(Kartenpruefung.istVerborgen(pruefung("## Card check", zeile))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Ergebnis: rot",
        "Result: red",
        "Ergebnis: Konflikt",
        "Result: conflict",
        "Ergebnis: nicht geprüft",
        "Result: not checked",
        "Ergebnis: läuft",
        "Result: running",
        "Ergebnis:",
        "Ergebnis: gruenlich"
      })
  void eineNichtGrueneKartenpruefungIstVerborgen(String zeile) {
    assertThat(Kartenpruefung.istVerborgen(pruefung("## Kartenprüfung", zeile))).isTrue();
    assertThat(Kartenpruefung.istVerborgen(pruefung("## Card check", zeile))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"## Kartenprüfung\n\nNur ein Satz.", "## Card check", "## Kartenprüfung"})
  void eineKartenpruefungOhneErgebniszeileIstVerborgen(String text) {
    assertThat(Kartenpruefung.istVerborgen(text)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\n\n  ## Kartenprüfung\n\n  Ergebnis: rot\n",
        "  ## card CHECK  \nResult: red",
        "## Kartenprüfung\r\n\r\n   Ergebnis: rot\r\n"
      })
  void fuehrenderLeerraumUndSchreibungZaehlenNicht_verborgen(String text) {
    assertThat(Kartenpruefung.istVerborgen(text)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\n\n  ## Kartenprüfung\n\n  Ergebnis: gruen\n",
        "## Kartenprüfung\r\n\r\n\tResult: green\r\n"
      })
  void fuehrenderLeerraumUndSchreibungZaehlenNicht_sichtbar(String text) {
    assertThat(Kartenpruefung.istVerborgen(text)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Ergebnis: rot",
        "Ergebnis: grün",
        "Result: red",
        "Ergebnis: rot\nErgebnis: gruen"
      })
  void nurDieErsteErgebniszeileZaehlt(String zeilen) {
    assertThat(Kartenpruefung.istVerborgen("## Kartenprüfung\n" + zeilen)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "Sieht gut aus.\nErgebnis: rot",
        "## Kartenprüfung läuft noch\nErgebnis: rot",
        "## Laufstand\n\n## Kartenprüfung\nErgebnis: rot",
        "### Kartenprüfung\nErgebnis: rot",
        "## Rückfrage\nErgebnis: rot"
      })
  void einAndererKommentarIstNieVerborgen(String text) {
    assertThat(Kartenpruefung.istVerborgen(text)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"## Kartenprüfung", "  ## Card check ", "## kartenprüfung"})
  void erkenntDenKopf(String kopf) {
    assertThat(Kartenpruefung.istKartenpruefung(kopf + "\nErgebnis: gruen")).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Hallo", "## Kartenprüfung 2", "## Antwort"})
  void erkenntAndereKoepfeNicht(String kopf) {
    assertThat(Kartenpruefung.istKartenpruefung(kopf + "\nErgebnis: rot")).isFalse();
  }
}
