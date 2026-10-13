package org.mwolff.manban.board.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Spalte existiert nicht (oder gehört nicht zum angegebenen Board). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ColumnNotFoundException extends RuntimeException implements UebersetzbareMeldung {

  public ColumnNotFoundException() {
    super("Spalte nicht gefunden");
  }

  @Override
  public String schluessel() {
    return "board.spalte.nicht-gefunden";
  }
}
