package org.mwolff.manban.board.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Eine Spalte mit Karten kann nicht gelöscht werden (erst Karten verschieben). */
@ResponseStatus(HttpStatus.CONFLICT)
public class ColumnNotEmptyException extends RuntimeException implements UebersetzbareMeldung {

  public ColumnNotEmptyException() {
    super("Spalte enthält noch Karten und kann nicht gelöscht werden");
  }

  @Override
  public String schluessel() {
    return "board.spalte.nicht-leer";
  }
}
