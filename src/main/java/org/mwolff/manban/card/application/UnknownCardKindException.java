package org.mwolff.manban.card.application;

import java.io.Serializable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Eine verlangte Kartenart gibt es nicht (Plan #1574, E2) (400).
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public final class UnknownCardKindException extends RuntimeException
    implements UebersetzbareMeldung {

  private final String meldungsschluessel;
  private final Serializable[] meldungsargumente;

  private UnknownCardKindException(String schluessel, String text, Serializable... argumente) {
    super(text);
    this.meldungsschluessel = schluessel;
    this.meldungsargumente = argumente;
  }

  /** Die Kartenart gibt es nicht. */
  public static UnknownCardKindException unbekannt(String name) {
    return new UnknownCardKindException(
        "card.kartenart.unbekannt", "Unbekannte Kartenart: " + name, name);
  }

  @Override
  public String schluessel() {
    return meldungsschluessel;
  }

  @Override
  public Object[] argumente() {
    return meldungsargumente.clone();
  }
}
