package org.mwolff.manban.project.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Zuordnung abgelehnt, weil der bereits registrierte Nutzer noch nicht von einem Plattform-Admin
 * freigegeben wurde (Issue #0097/#0101). Ergibt HTTP 422.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class MemberNotApprovedException extends RuntimeException implements UebersetzbareMeldung {

  private final String email;

  public MemberNotApprovedException(String email) {
    super("Nutzer ist noch nicht freigegeben: " + email);
    this.email = email;
  }

  @Override
  public String schluessel() {
    return "project.mitglied-nicht-freigegeben";
  }

  @Override
  public Object[] argumente() {
    return new Object[] {email};
  }
}
