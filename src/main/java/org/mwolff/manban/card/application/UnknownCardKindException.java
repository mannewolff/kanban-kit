package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Eine verlangte Kartenart gibt es nicht (Plan #1574, E2) (400). */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UnknownCardKindException extends RuntimeException {

  public UnknownCardKindException(String message) {
    super(message);
  }
}
