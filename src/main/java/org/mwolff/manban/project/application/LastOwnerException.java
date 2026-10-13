package org.mwolff.manban.project.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Der letzte OWNER eines Projekts kann nicht entfernt oder herabgestuft werden. */
@ResponseStatus(HttpStatus.CONFLICT)
public class LastOwnerException extends RuntimeException implements UebersetzbareMeldung {

  public LastOwnerException() {
    super("Der letzte OWNER kann nicht entfernt oder herabgestuft werden");
  }

  @Override
  public String schluessel() {
    return "project.letzter-owner";
  }
}
