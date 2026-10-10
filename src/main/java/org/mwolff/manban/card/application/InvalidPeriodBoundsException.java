package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Grenzen eines Zeitraums passen nicht zusammen; welche Kombination zulässig ist, legt der
 * jeweilige Aufrufer fest (Plan #1539, E12; Plan #1574, E7).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidPeriodBoundsException extends RuntimeException {

  public InvalidPeriodBoundsException(String message) {
    super(message);
  }
}
