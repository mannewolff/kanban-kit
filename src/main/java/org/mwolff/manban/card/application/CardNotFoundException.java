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

  /**
   * Dieselbe Meldung, aus einer anderen Nicht-gefunden-Ursache — etwa einer fehlenden
   * Mitgliedschaft, die nach außen nicht von einer unbekannten Karte zu unterscheiden sein soll.
   */
  public CardNotFoundException(Throwable cause) {
    super("Karte nicht gefunden", cause);
  }

  @Override
  public String schluessel() {
    return "card.nicht-gefunden";
  }
}
