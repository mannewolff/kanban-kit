package org.mwolff.manban.project.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Einladung ist unbekannt, bereits angenommen oder abgelaufen. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidInvitationException extends RuntimeException implements UebersetzbareMeldung {

  public InvalidInvitationException() {
    super("Einladung ist ungültig oder abgelaufen");
  }

  @Override
  public String schluessel() {
    return "project.einladung-ungueltig";
  }
}
