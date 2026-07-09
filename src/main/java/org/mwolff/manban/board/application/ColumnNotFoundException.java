package org.mwolff.manban.board.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Spalte existiert nicht (oder gehört nicht zum angegebenen Board). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ColumnNotFoundException extends RuntimeException {

    public ColumnNotFoundException() {
        super("Spalte nicht gefunden");
    }
}
