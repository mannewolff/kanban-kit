package org.mwolff.manban.board.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Ein Board kann erst endgültig gelöscht werden, wenn es zuvor archiviert wurde. */
@ResponseStatus(HttpStatus.CONFLICT)
public class BoardNotArchivedException extends RuntimeException {

  public BoardNotArchivedException() {
    super("Board ist nicht archiviert");
  }
}
