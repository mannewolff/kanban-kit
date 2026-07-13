package org.mwolff.manban.project.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zuordnung abgelehnt, weil der bereits registrierte Nutzer noch nicht von einem Plattform-Admin
 * freigegeben wurde (Issue #0097/#0101). Ergibt HTTP 422.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class MemberNotApprovedException extends RuntimeException {

  public MemberNotApprovedException(String email) {
    super("Nutzer ist noch nicht freigegeben: " + email);
  }
}
