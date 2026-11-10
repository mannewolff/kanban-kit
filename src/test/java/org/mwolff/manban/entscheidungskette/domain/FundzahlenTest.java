package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Zählung der Funde nach der Grammatik aus {@code kit/findings.mjs} (Issue #2113, E9). */
class FundzahlenTest {

  private static Fundzahlen zaehle(String text) {
    return Fundzahlen.aus(text.lines().toList());
  }

  @Test
  void aus_berichtOhneFunde_keineFunde() {
    // Given
    String text = "Alles geprüft, nichts zu beanstanden.\n\nKeine Funde.";

    // When
    Fundzahlen funde = zaehle(text);

    // Then
    assertThat(funde).isEqualTo(new Fundzahlen(0, 0, 0, 0));
    assertThat(funde.keine()).isTrue();
  }

  @Test
  void aus_leererText_keineFunde() {
    assertThat(Fundzahlen.aus(List.of()).keine()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "**KRITISCH — Titel**",
        "- **KRITISCH — Titel**",
        "1. **KRITISCH — Titel**",
        "2) **KRITISCH — Titel**",
        "+ KRITISCH: Titel",
        "* KRITISCH: Titel",
        "> - #### KRITISCH Titel",
        "#### KRITISCH",
        "KRITISCH am Zeilenanfang"
      })
  void aus_markeInJederForm_einFund(String zeile) {
    assertThat(zaehle(zeile)).isEqualTo(new Fundzahlen(1, 0, 0, 0));
  }

  @Test
  void aus_fetteKopfzeile_zaehltJeSchwere() {
    // Given
    String text =
        """
        **KRITISCH — eins**
        **BLOCKER — zwei**
        **WICHTIG — drei**
        **HINWEIS — vier**
        """;

    // When
    Fundzahlen funde = zaehle(text);

    // Then
    assertThat(funde).isEqualTo(new Fundzahlen(1, 1, 1, 1));
    assertThat(funde.gesamt()).isEqualTo(4);
    assertThat(funde.keine()).isFalse();
  }

  @Test
  void aus_blockerAlsListenpunkte_zaehltJeden() {
    // Given
    String text =
        """
        1. **BLOCKER — eins**
           Text.
        2. **BLOCKER — zwei**
           Text.
        """;

    // When / Then
    assertThat(zaehle(text)).isEqualTo(new Fundzahlen(0, 2, 0, 0));
  }

  @Test
  void aus_gruppenueberschrift_jedeFetteKopfzeileDarunterEinFund() {
    // Given
    String text =
        """
        #### WICHTIG

        **Erster Fund**
        Beschreibung.

        **Zweiter Fund**
        Beschreibung.

        - **Dritter Fund**
        """;

    // When / Then
    assertThat(zaehle(text)).isEqualTo(new Fundzahlen(0, 0, 3, 0));
  }

  @Test
  void aus_gruppenueberschriftMitInhaltVorDerKopfzeile_bleibtEigenerFund() {
    // Given
    String text =
        """
        #### HINWEIS
        Ein Satz direkt unter der Marke.
        **Zweiter Fund**
        """;

    // When / Then
    assertThat(zaehle(text)).isEqualTo(new Fundzahlen(0, 0, 0, 2));
  }

  @Test
  void aus_gruppenueberschriftOhneKopfzeile_einFund() {
    assertThat(zaehle("#### WICHTIG\n\n")).isEqualTo(new Fundzahlen(0, 0, 1, 0));
  }

  @Test
  void aus_beschriftungInnerhalbEinesFunds_keinWeitererFund() {
    // Given
    String text =
        """
        **WICHTIG — Titel**
        **Datei:** kit/board.mjs:12
        **Behebung:** so
        """;

    // When / Then
    assertThat(zaehle(text)).isEqualTo(new Fundzahlen(0, 0, 1, 0));
  }

  @Test
  void aus_ueberschriftOhneMarke_schliesstDenAbschnitt() {
    // Given
    String text =
        """
        **WICHTIG — Titel**

        ### Geprüft ohne Befund
        **Fette Zeile ohne Abschnitt**
        """;

    // When / Then
    assertThat(zaehle(text)).isEqualTo(new Fundzahlen(0, 0, 1, 0));
  }

  @Test
  void aus_ueberschriftOhneMarke_schliesstAuchGruppenueberschrift() {
    // Given
    String text =
        """
        #### WICHTIG
        ### Weiter
        Ein Satz.
        **Fette Zeile**
        """;

    // When / Then
    assertThat(zaehle(text)).isEqualTo(new Fundzahlen(0, 0, 1, 0));
  }

  @Test
  void aus_markeImFliesstext_eroeffnetNichts() {
    assertThat(zaehle("Die Marke WICHTIG steht mitten im Satz.").keine()).isTrue();
  }

  @Test
  void aus_markeAlsWortanfang_zaehltNicht() {
    assertThat(zaehle("WICHTIGER Hinweis ohne Marke").keine()).isTrue();
  }

  @Test
  void aus_fetteKopfzeileOhneAbschnitt_zaehltNicht() {
    assertThat(zaehle("**Nur fett**").keine()).isTrue();
  }

  @Test
  void aus_dekorationAmZeilenende_faelltWeg() {
    assertThat(zaehle("## HINWEIS ##   ")).isEqualTo(new Fundzahlen(0, 0, 0, 1));
  }
}
