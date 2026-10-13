package org.mwolff.manban.card.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Karte existiert nicht. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CardNotFoundException extends RuntimeException implements UebersetzbareMeldung {

  public CardNotFoundException() {
    super("Karte nicht gefunden");
  }

  @Override
  public String schluessel() {
    return "card.nicht-gefunden";
  }
}
