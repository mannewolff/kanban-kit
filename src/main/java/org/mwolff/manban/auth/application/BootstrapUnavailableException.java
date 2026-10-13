package org.mwolff.manban.auth.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Bootstrap nicht mehr möglich, weil bereits ein Plattform-Admin existiert. */
@ResponseStatus(HttpStatus.CONFLICT)
public class BootstrapUnavailableException extends RuntimeException
    implements UebersetzbareMeldung {

  public BootstrapUnavailableException() {
    super("Es existiert bereits ein Plattform-Admin");
  }

  @Override
  public String schluessel() {
    return "auth.bootstrap-nicht-verfuegbar";
  }
}
