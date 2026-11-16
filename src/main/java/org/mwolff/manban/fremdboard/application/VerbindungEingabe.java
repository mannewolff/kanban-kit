package org.mwolff.manban.fremdboard.application;

import org.jspecify.annotations.Nullable;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;

/**
 * Was der Owner zur Verbindung angibt (Issue #2166). Bereinigt beim Anlegen: Leerzeichen am Rand
 * fallen weg, die Adresse verliert ihren Schlussstrich, der Pfad seine Schrägstriche am Rand; ein
 * leerer Zugang oder ein leeres Kit-Konto gilt als nicht angegeben, fehlende Statuslabels als
 * {@link StatusLabels#STANDARD}.
 *
 * @param zugang der Lesezugang im Klartext; {@code null} beim Ändern heißt: der alte bleibt
 */
public record VerbindungEingabe(
    FremdBoardArt art,
    String basisUrl,
    String projektPfad,
    @Nullable String zugang,
    @Nullable String kitKonto,
    StatusLabels statuslabels) {

  public VerbindungEingabe(
      FremdBoardArt art,
      String basisUrl,
      String projektPfad,
      @Nullable String zugang,
      @Nullable String kitKonto,
      @Nullable StatusLabels statuslabels) {
    this.art = art;
    this.basisUrl = ohneRand(basisUrl.strip(), false);
    this.projektPfad = ohneRand(projektPfad.strip(), true);
    this.zugang = leerAlsNull(zugang);
    this.kitKonto = leerAlsNull(kitKonto);
    this.statuslabels = statuslabels == null ? StatusLabels.STANDARD : statuslabels;
  }

  private static String ohneRand(String wert, boolean auchVorne) {
    int anfang = 0;
    int ende = wert.length();
    while (auchVorne && anfang < ende && wert.charAt(anfang) == '/') {
      anfang++;
    }
    while (ende > anfang && wert.charAt(ende - 1) == '/') {
      ende--;
    }
    return wert.substring(anfang, ende);
  }

  private static @Nullable String leerAlsNull(@Nullable String wert) {
    return wert == null || wert.isBlank() ? null : wert.strip();
  }

  /** Textform ohne den Zugang. */
  @Override
  public String toString() {
    return "VerbindungEingabe[art="
        + art
        + ", basisUrl="
        + basisUrl
        + ", projektPfad="
        + projektPfad
        + ", zugang="
        + (zugang == null ? "null" : "***")
        + ", kitKonto="
        + kitKonto
        + ", statuslabels="
        + statuslabels
        + "]";
  }
}
