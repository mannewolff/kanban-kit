package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Login abgelehnt, weil der Benutzer noch nicht von einem Plattform-Admin freigegeben wurde. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class UserNotApprovedException extends RuntimeException {

  public UserNotApprovedException() {
    super("Benutzer ist noch nicht vom Administrator freigegeben");
  }
}
