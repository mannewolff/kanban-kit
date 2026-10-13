package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Aufrufer ist kein Plattform-Admin. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AdminAccessDeniedException extends RuntimeException implements UebersetzbareMeldung {

  public AdminAccessDeniedException() {
    super("Plattform-Admin-Recht erforderlich");
  }

  @Override
  public String schluessel() {
    return "auth.admin-recht-erforderlich";
  }
}
