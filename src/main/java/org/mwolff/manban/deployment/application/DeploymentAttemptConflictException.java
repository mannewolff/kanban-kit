package org.mwolff.manban.deployment.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Kennung eines Versuchs kam schon einmal, aber mit anderem Inhalt (Issue #1833, Plan #1831
 * E3).
 *
 * <p>Ersetzt wird nicht: Eine verspätete Wiederholung dürfte sonst einen Ausgang umschreiben, und
 * dieselbe Kennung zählt nur einmal (fachlich #1784 AK 12). Der eigene Schlüssel trennt diesen Fall
 * vom ungebundenen Token, das ebenfalls {@code 409} bekommt.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DeploymentAttemptConflictException extends RuntimeException
    implements UebersetzbareMeldung {

  public DeploymentAttemptConflictException() {
    super("Ein Versuch mit dieser Kennung wurde schon mit anderem Inhalt gemeldet.");
  }

  @Override
  public String schluessel() {
    return "deployment.attempt.conflict";
  }
}
