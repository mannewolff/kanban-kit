package org.mwolff.manban.kanbancompat.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das präsentierte Token ist gültig, aber nicht an ein Projekt + Board gebunden — die
 * Kanban-Compat-API kann daher kein Board bestimmen. Ergibt HTTP 409.
 *
 * <p>Der Text steht als Message statt als {@code reason} der Annotation, damit er übersetzbar ist
 * (Issue #1641).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class TokenNotBoundException extends RuntimeException implements UebersetzbareMeldung {

  public TokenNotBoundException() {
    super("Token ist an kein Board gebunden. Bitte ein board-gebundenes Kanban-Token verwenden.");
  }

  @Override
  public String schluessel() {
    return "kanbancompat.token-ohne-board";
  }
}
