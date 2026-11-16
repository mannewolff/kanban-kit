package org.mwolff.manban.fremdboard.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;

/**
 * Die Verbindung, wie die Einstellung sie zeigt (Issue #2166, Plan #2152 A9, A13) — ohne Zugang,
 * auch nicht verschlüsselt. Ohne Verbindung sind alle Angaben außer {@code schluesselGesetzt} leer.
 *
 * @param angebunden ob das Projekt ein fremdes Board hat
 * @param schluesselGesetzt ob {@code MANBAN_SECRET_KEY} gesetzt ist; sonst ist das Anbinden
 *     gesperrt
 * @param zugangLesbar ob sich der gespeicherte Zugang entschlüsseln lässt; sonst muss er neu
 *     hinterlegt werden
 */
public record VerbindungAnsicht(
    boolean angebunden,
    boolean schluesselGesetzt,
    @Nullable FremdBoardArt art,
    @Nullable String basisUrl,
    @Nullable String projektPfad,
    @Nullable String kitKonto,
    @Nullable StatusLabels statuslabels,
    @Nullable Instant angebundenAm,
    @Nullable Instant letzterAbgleichAm,
    @Nullable String letzterFehler,
    boolean zugangLesbar) {

  /** Die Ansicht eines Projekts ohne fremdes Board. */
  static VerbindungAnsicht ohne(boolean schluesselGesetzt) {
    return new VerbindungAnsicht(
        false, schluesselGesetzt, null, null, null, null, null, null, null, null, false);
  }
}
