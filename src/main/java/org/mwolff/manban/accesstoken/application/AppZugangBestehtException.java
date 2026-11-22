package org.mwolff.manban.accesstoken.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Aufrufer hält am Board schon einen wirksamen Zugang der App; die Meldung nennt seinen Inhaber
 * (Plan #2100, E6). Die Grenze gilt je Person und Board (Plan #2206, E4). Ergibt HTTP 409.
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
