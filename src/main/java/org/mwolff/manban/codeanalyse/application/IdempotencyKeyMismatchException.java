package org.mwolff.manban.codeanalyse.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Der Kopf {@code Idempotency-Key} weicht von {@code publishedCommit} im Rumpf ab (Issue #2275,
 * Plan #2272 E4). Der Kopf ist freiwillig; nennt er einen anderen Stand als der Rumpf, ist unklar,
 * welche Veröffentlichung gemeint ist, und nichts wird gespeichert.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class IdempotencyKeyMismatchException extends RuntimeException
    implements UebersetzbareMeldung {

  public IdempotencyKeyMismatchException() {
    super("Der Kopf Idempotency-Key nennt einen anderen Stand als publishedCommit im Rumpf.");
  }

  @Override
  public String schluessel() {
    return "codeanalyse.kopf-weicht-ab";
  }
}
