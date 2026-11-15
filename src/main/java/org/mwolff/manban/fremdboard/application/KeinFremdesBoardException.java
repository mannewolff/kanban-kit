package org.mwolff.manban.fremdboard.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Anstoß für ein Projekt, dessen Board in kanban-kit liegt (Issue #2167, Plan #2152 A6):
 * Zustandskonflikt (409), weil derselbe Aufruf für ein Projekt mit fremdem Board gültig wäre.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public final class KeinFremdesBoardException extends RuntimeException
    implements UebersetzbareMeldung {

  private static final long serialVersionUID = 1L;

  public KeinFremdesBoardException() {
    super("Das Board dieses Projekts liegt in kanban-kit; es gibt nichts anzustoßen.");
  }

  @Override
  public String schluessel() {
    return "fremdboard.kein-fremdes-board";
  }
}
