package org.mwolff.manban.deployment.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Ausgang einer Meldung liegt mehr als fünf Minuten nach der Serverzeit (Issue #1833, Plan
 * #1831 E7). Eine Meldung aus der Zukunft verfälschte das Fenster dauerhaft; ein vorgehendes {@code
 * committedAt} wird dagegen angenommen.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DeploymentInFutureException extends RuntimeException implements UebersetzbareMeldung {

  public DeploymentInFutureException() {
    super("finishedAt liegt mehr als 5 Minuten nach der Serverzeit.");
  }

  @Override
  public String schluessel() {
    return "deployment.finished-at.zukunft";
  }
}
