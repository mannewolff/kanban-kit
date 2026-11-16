package org.mwolff.manban.fremdboard.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;

/** Bereinigung und Textform der Eingabe zur Verbindung (Issue #2166, Mutanten aus Issue #2182). */
class VerbindungEingabeTest {

  private static VerbindungEingabe eingabe(String basisUrl, String projektPfad, String zugang) {
    return new VerbindungEingabe(
        FremdBoardArt.GITLAB, basisUrl, projektPfad, zugang, "kit-bot", StatusLabels.STANDARD);
  }

  @Test
  @DisplayName("Ein Pfad nur aus Schrägstrichen wird leer, ohne über das Ende hinaus zu lesen")
  void pfadNurAusSchraegstrichenWirdLeer() {
    assertThat(eingabe("https://gitlab.example", "///", null).projektPfad()).isEmpty();
  }

  @Test
  @DisplayName("Ein leerer Pfad bleibt leer")
  void leererPfadBleibtLeer() {
    assertThat(eingabe("https://gitlab.example", "", null).projektPfad()).isEmpty();
  }

  @Test
  @DisplayName("Vorne fallen nur Schrägstriche weg, kein anderes Zeichen")
  void vorneFallenNurSchraegstricheWeg() {
    assertThat(eingabe("https://gitlab.example", "gruppe/projekt", null).projektPfad())
        .isEqualTo("gruppe/projekt");
    assertThat(eingabe("https://gitlab.example", "//gruppe/projekt//", null).projektPfad())
        .isEqualTo("gruppe/projekt");
  }

  @Test
  @DisplayName("Eine Adresse nur aus Schrägstrichen wird leer, ohne vor den Anfang zu lesen")
  void adresseNurAusSchraegstrichenWirdLeer() {
    assertThat(eingabe("/", "gruppe/projekt", null).basisUrl()).isEmpty();
    assertThat(eingabe("", "gruppe/projekt", null).basisUrl()).isEmpty();
  }

  @Test
  @DisplayName("Die Adresse behält ihre führenden Schrägstriche und verliert nur den Schluss")
  void adresseBehaeltAnfang() {
    assertThat(eingabe("//gitlab.example//", "gruppe/projekt", null).basisUrl())
        .isEqualTo("//gitlab.example");
  }

  @Test
  @DisplayName("Die Textform nennt alle Felder und verbirgt einen gesetzten Zugang")
  void textformVerbirgtZugang() {
    String text = eingabe("https://gitlab.example", "gruppe/projekt", "geheim").toString();

    assertThat(text)
        .isEqualTo(
            "VerbindungEingabe[art=GITLAB, basisUrl=https://gitlab.example,"
                + " projektPfad=gruppe/projekt, zugang=***, kitKonto=kit-bot, statuslabels="
                + StatusLabels.STANDARD
                + "]")
        .doesNotContain("geheim");
  }

  @Test
  @DisplayName("Die Textform zeigt einen fehlenden Zugang als null")
  void textformOhneZugang() {
    assertThat(eingabe("https://gitlab.example", "gruppe/projekt", null).toString())
        .contains("zugang=null,");
  }
}
