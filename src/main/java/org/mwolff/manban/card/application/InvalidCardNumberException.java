package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die gesetzte Projekt-Startnummer ist ungültig — sie liegt nicht über der höchsten bereits
 * vergebenen Kartennummer des Projekts.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCardNumberException extends RuntimeException {

  public InvalidCardNumberException(String message) {
    super(message);
  }
}
