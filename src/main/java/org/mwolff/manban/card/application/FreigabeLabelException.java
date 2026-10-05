package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Access-Token wollte ein Freigabe-Label des Kits gegen seine Richtung ändern (403, Issue
 * #1421). Die Meldung nennt Label und Richtung; Einzelheiten in {@link FreigabeLabels}.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class FreigabeLabelException extends RuntimeException {

  public FreigabeLabelException(String message) {
    super(message);
  }
}
