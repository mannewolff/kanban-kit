package org.mwolff.manban.project.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Der angesprochene Benutzer ist kein Mitglied des Projekts. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class MemberNotFoundException extends RuntimeException implements UebersetzbareMeldung {

  public MemberNotFoundException() {
    super("Mitglied nicht gefunden");
  }

  @Override
  public String schluessel() {
    return "project.mitglied-nicht-gefunden";
  }
}
