package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Ungültige Label-Operation (leerer/duplizierter Name oder Label eines fremden Boards) (400). */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidLabelException extends RuntimeException {

  public InvalidLabelException(String message) {
    super(message);
  }
}
