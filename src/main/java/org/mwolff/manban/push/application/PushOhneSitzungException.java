package org.mwolff.manban.push.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Anfrage trägt kein Sitzungsende (Issue #1997, Plan #1989 E14): Ein Abo lebt so lange wie die
 * Sitzung auf dem Gerät, ohne Sitzung gibt es keins.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class PushOhneSitzungException extends RuntimeException implements UebersetzbareMeldung {

  private static final long serialVersionUID = 1L;

  public PushOhneSitzungException() {
    super("Ein Abo für Benachrichtigungen braucht eine Anmeldung auf diesem Gerät.");
  }

  @Override
  public String schluessel() {
    return "push.abo.ohne-sitzung";
  }
}
