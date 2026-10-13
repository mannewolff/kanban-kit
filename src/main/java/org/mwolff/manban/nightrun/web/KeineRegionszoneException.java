package org.mwolff.manban.nightrun.web;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die gemeldete Zone ist keine Regionszone, sondern ein fester Offset (Issue #939). Ergibt HTTP
 * 400; der Text erscheint in der Sprache der Anfrage (Issue #1642).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
final class KeineRegionszoneException extends RuntimeException implements UebersetzbareMeldung {

  KeineRegionszoneException() {
    super("zone muss eine Regionszone sein, etwa Europe/Berlin");
  }

  @Override
  public String schluessel() {
    return "nightrun.zone.keine-regionszone";
  }
}
