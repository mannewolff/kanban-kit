package org.mwolff.manban.project.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Der angesprochene Benutzer ist kein Mitglied des Projekts. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class MemberNotFoundException extends RuntimeException {

  public MemberNotFoundException() {
    super("Mitglied nicht gefunden");
  }
}
