package org.mwolff.manban.comment.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Kommentar existiert nicht. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CommentNotFoundException extends RuntimeException implements UebersetzbareMeldung {

  public CommentNotFoundException() {
    super("Kommentar nicht gefunden");
  }

  @Override
  public String schluessel() {
    return "comment.nicht-gefunden";
  }
}
