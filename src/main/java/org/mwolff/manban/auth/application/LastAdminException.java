package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der letzte nicht gesperrte Plattform-Admin darf der Plattform nicht genommen werden — weder durch
 * Herabstufen noch durch Sperren. Beide Vorgänge werfen dieselbe Exception und geben damit dieselbe
 * Meldung nach außen (Issue #881): Für den Aufrufer ist es ein Sachverhalt, nicht zwei.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class LastAdminException extends RuntimeException {

  public LastAdminException() {
    super("Mindestens ein aktiver Plattform-Administrator muss bestehen bleiben.");
  }
}
