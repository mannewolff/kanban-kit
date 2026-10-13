package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Login abgelehnt, weil die E-Mail-Adresse noch nicht bestätigt wurde. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class EmailNotVerifiedException extends RuntimeException implements UebersetzbareMeldung {

  public EmailNotVerifiedException() {
    super("E-Mail-Adresse ist noch nicht bestätigt");
  }

  @Override
  public String schluessel() {
    return "auth.email-nicht-bestaetigt";
  }
}
