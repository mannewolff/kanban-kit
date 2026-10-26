package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Eine Vorschau „Ansicht als XY“ ist weder als das eigene Konto noch als ein anderer
 * Plattform-Admin möglich (Issue #1917, Plan #1914 E11).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class VorschauZielUnzulaessigException extends RuntimeException
    implements UebersetzbareMeldung {

  public VorschauZielUnzulaessigException() {
    super("Eine Vorschau ist weder als das eigene Konto noch als ein Plattform-Admin möglich.");
  }

  @Override
  public String schluessel() {
    return "auth.vorschau-ziel-unzulaessig";
  }
}
