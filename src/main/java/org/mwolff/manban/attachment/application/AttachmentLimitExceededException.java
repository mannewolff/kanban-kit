package org.mwolff.manban.attachment.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Die Karte hat bereits die maximal erlaubte Anzahl Anhänge. */
@ResponseStatus(HttpStatus.CONFLICT)
public class AttachmentLimitExceededException extends RuntimeException {

  public AttachmentLimitExceededException(int max) {
    super("Maximal " + max + " Anhänge pro Karte erlaubt");
  }
}
