package org.mwolff.manban.push.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Web Push ist abgeschaltet, weil die VAPID-Schlüssel fehlen (Issue #1997, Plan #1989 E16). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PushNichtEingerichtetException extends RuntimeException
    implements UebersetzbareMeldung {

  private static final long serialVersionUID = 1L;

  public PushNichtEingerichtetException() {
    super("Benachrichtigungen sind auf diesem Server nicht eingerichtet.");
  }

  @Override
  public String schluessel() {
    return "push.nicht-eingerichtet";
  }
}
