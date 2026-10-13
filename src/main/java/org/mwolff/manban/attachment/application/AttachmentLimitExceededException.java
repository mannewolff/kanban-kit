package org.mwolff.manban.attachment.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Die Karte hat bereits die maximal erlaubte Anzahl Anhänge. */
@ResponseStatus(HttpStatus.CONFLICT)
public class AttachmentLimitExceededException extends RuntimeException
    implements UebersetzbareMeldung {

  private final int max;

  public AttachmentLimitExceededException(int max) {
    super("Maximal " + max + " Anhänge pro Karte erlaubt");
    this.max = max;
  }

  @Override
  public String schluessel() {
    return "attachment.limit";
  }

  @Override
  public Object[] argumente() {
    return new Object[] {max};
  }
}
