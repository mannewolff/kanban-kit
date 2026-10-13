package org.mwolff.manban.attachment.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Anhang existiert nicht. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AttachmentNotFoundException extends RuntimeException implements UebersetzbareMeldung {

  public AttachmentNotFoundException() {
    super("Anhang nicht gefunden");
  }

  @Override
  public String schluessel() {
    return "attachment.nicht-gefunden";
  }
}
