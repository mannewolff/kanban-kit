package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Grenzen eines Zeitraums passen nicht (Plan #1539, E12): Nur eine der beiden Grenzen ist
 * gesetzt, oder der Beginn liegt nicht vor dem Ende.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidPeriodBoundsException extends RuntimeException {

  public InvalidPeriodBoundsException(String message) {
    super(message);
  }
}
