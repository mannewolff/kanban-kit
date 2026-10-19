package org.mwolff.manban.deployment.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Token ohne Board-Bindung kann keine Auslieferung melden (Issue #1833, Plan #1831 E4, fachlich
 * #1784 AK 13).
 *
 * <p>Das Projekt kommt aus der Bindung des Tokens, nie aus dem Aufruf; ohne Bindung gibt es kein
 * Zielprojekt. {@code 409} wie die übrige Kanban-Schnittstelle unter {@code /api/kanban/**}, wo
 * dieser Status schon „Token nicht gebunden“ heißt.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class TokenNotBoundForDeploymentException extends RuntimeException
    implements UebersetzbareMeldung {

  public TokenNotBoundForDeploymentException() {
    super("Token ist an kein Board gebunden. Bitte ein board-gebundenes Kanban-Token verwenden.");
  }

  @Override
  public String schluessel() {
    return "deployment.token-ohne-board";
  }
}
