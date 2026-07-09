package org.mwolff.manban.board.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Eine Spalte mit Karten kann nicht gelöscht werden (erst Karten verschieben). */
@ResponseStatus(HttpStatus.CONFLICT)
public class ColumnNotEmptyException extends RuntimeException {

    public ColumnNotEmptyException() {
        super("Spalte enthält noch Karten und kann nicht gelöscht werden");
    }
}
