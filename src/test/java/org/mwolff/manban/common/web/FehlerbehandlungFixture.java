package org.mwolff.manban.common.web;

import org.mwolff.manban.common.i18n.Texte;

/**
 * Der globale Fehler-Handler für Controller-Tests anderer Module (Issue #2277). {@link
 * GlobalExceptionHandler} bleibt paketintern; wer in einem Modul-Test Statuscode, {@code
 * fieldErrors} und übersetzte Meldung über MockMvc prüft, holt ihn hier für {@code
 * setControllerAdvice}.
 */
public final class FehlerbehandlungFixture {

  private FehlerbehandlungFixture() {}

  /** Der Handler mit den gegebenen Texten. */
  public static Object handler(Texte texte) {
    return new GlobalExceptionHandler(texte);
  }
}
