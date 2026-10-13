package org.mwolff.manban.project.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die beim Anlegen eines Projekts angegebene Owner-E-Mail gehört zu keinem Nutzer. Ergibt HTTP 400.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ProjectOwnerNotFoundException extends RuntimeException
    implements UebersetzbareMeldung {

  private final String email;

  public ProjectOwnerNotFoundException(String email) {
    super("Kein Nutzer mit E-Mail: " + email);
    this.email = email;
  }

  @Override
  public String schluessel() {
    return "project.owner-nicht-gefunden";
  }

  @Override
  public Object[] argumente() {
    return new Object[] {email};
  }
}
