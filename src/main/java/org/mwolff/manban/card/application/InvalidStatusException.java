package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Status lässt sich so nicht setzen (Plan #1294, E9): Der Wert ist keiner der fünf
 * Prozesszustände, oder die Karte trägt keinen eigenen Status (Vorhaben, Dokumentart).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidStatusException extends RuntimeException {

  public InvalidStatusException(String message) {
    super(message);
  }

  public InvalidStatusException(String message, Throwable cause) {
    super(message, cause);
  }
}
