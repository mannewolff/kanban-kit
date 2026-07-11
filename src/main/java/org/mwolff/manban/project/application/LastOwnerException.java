package org.mwolff.manban.project.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Der letzte OWNER eines Projekts kann nicht entfernt oder herabgestuft werden. */
@ResponseStatus(HttpStatus.CONFLICT)
public class LastOwnerException extends RuntimeException {

  public LastOwnerException() {
    super("Der letzte OWNER kann nicht entfernt oder herabgestuft werden");
  }
}
