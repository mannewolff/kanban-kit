package org.mwolff.manban.board.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Ein Board kann erst endgültig gelöscht werden, wenn es zuvor archiviert wurde. */
@ResponseStatus(HttpStatus.CONFLICT)
public class BoardNotArchivedException extends RuntimeException implements UebersetzbareMeldung {

  public BoardNotArchivedException() {
    super("Board ist nicht archiviert");
  }

  @Override
  public String schluessel() {
    return "board.nicht-archiviert";
  }
}
