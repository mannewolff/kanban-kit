package org.mwolff.manban.accesstoken.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Am Board besteht schon ein wirksamer Zugang der App; die Meldung nennt seinen Inhaber (Plan
 * #2100, E6). Ergibt HTTP 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public final class AppZugangBestehtException extends RuntimeException
    implements UebersetzbareMeldung {

  private final String inhaberName;

  public AppZugangBestehtException(String inhaberName) {
    super("Für dieses Board besteht schon ein Zugang der App. Inhaber: " + inhaberName);
    this.inhaberName = inhaberName;
  }

  @Override
  public String schluessel() {
    return "accesstoken.app-zugang.besteht";
  }

  @Override
  public Object[] argumente() {
    return new Object[] {inhaberName};
  }
}
