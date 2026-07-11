package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Der letzte verbleibende Plattform-Admin darf nicht degradiert werden. */
@ResponseStatus(HttpStatus.CONFLICT)
public class LastAdminException extends RuntimeException {

  public LastAdminException() {
    super("Der letzte Plattform-Admin kann nicht degradiert werden");
  }
}
