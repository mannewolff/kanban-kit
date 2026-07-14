package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Ein Label mit der angefragten ID existiert nicht (404). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class LabelNotFoundException extends RuntimeException {

  public LabelNotFoundException() {
    super("Label nicht gefunden");
  }
}
