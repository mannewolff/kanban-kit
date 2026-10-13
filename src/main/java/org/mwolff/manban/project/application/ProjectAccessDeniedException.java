package org.mwolff.manban.project.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Mitglied, aber ohne ausreichende Rolle für die Aktion. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ProjectAccessDeniedException extends RuntimeException implements UebersetzbareMeldung {

  public ProjectAccessDeniedException() {
    super("Keine Berechtigung für diese Projekt-Aktion");
  }

  @Override
  public String schluessel() {
    return "project.keine-berechtigung";
  }
}
