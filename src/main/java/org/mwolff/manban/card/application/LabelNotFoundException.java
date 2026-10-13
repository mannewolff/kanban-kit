package org.mwolff.manban.card.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Ein Label mit der angefragten ID existiert nicht (404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class LabelNotFoundException extends RuntimeException implements UebersetzbareMeldung {

  public LabelNotFoundException() {
    super("Label nicht gefunden");
  }

  @Override
  public String schluessel() {
    return "card.label.nicht-gefunden";
  }
}
