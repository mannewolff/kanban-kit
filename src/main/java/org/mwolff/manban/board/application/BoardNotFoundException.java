package org.mwolff.manban.board.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Board existiert nicht. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BoardNotFoundException extends RuntimeException implements UebersetzbareMeldung {

  public BoardNotFoundException() {
    super("Board nicht gefunden");
  }

  @Override
  public String schluessel() {
    return "board.nicht-gefunden";
  }
}
