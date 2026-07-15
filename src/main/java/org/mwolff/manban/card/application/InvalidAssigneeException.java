package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Ein zugewiesener Benutzer ist kein Mitglied des Projekts (400). */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAssigneeException extends RuntimeException {

  public InvalidAssigneeException(String message) {
    super(message);
  }
}
