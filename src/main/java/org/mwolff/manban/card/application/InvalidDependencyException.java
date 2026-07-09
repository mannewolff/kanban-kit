package org.mwolff.manban.card.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Abhängigkeit verweist auf eine unbekannte Kartennummer oder auf die Karte selbst. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidDependencyException extends RuntimeException {

    public InvalidDependencyException(String message) {
        super(message);
    }
}
