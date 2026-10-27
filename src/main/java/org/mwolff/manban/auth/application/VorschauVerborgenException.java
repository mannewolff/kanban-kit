package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.MitProblemTyp;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * XY sähe diesen Bereich, der handelnde Admin selbst aber nicht; die Vorschau zeigt ihn deshalb
 * nicht (Issue #1918, Plan #1914 E6, AK 4 der fachlichen Quelle #1910).
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class VorschauVerborgenException extends RuntimeException
    implements UebersetzbareMeldung, MitProblemTyp {

  public VorschauVerborgenException() {
    super("Die Person sieht diesen Bereich, die Vorschau zeigt ihn aber nicht.");
  }

  @Override
  public String schluessel() {
    return "auth.vorschau-verborgen";
  }

  @Override
  public String problemTyp() {
    return "vorschau-verborgen";
  }
}
