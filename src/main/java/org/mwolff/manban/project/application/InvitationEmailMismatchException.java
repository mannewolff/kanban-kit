package org.mwolff.manban.project.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Die annehmende Person ist nicht die eingeladene E-Mail-Adresse. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class InvitationEmailMismatchException extends RuntimeException
    implements UebersetzbareMeldung {

  public InvitationEmailMismatchException() {
    super("Einladung gehört zu einer anderen E-Mail-Adresse");
  }

  @Override
  public String schluessel() {
    return "project.einladung-andere-email";
  }
}
