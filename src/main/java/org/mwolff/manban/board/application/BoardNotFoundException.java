package org.mwolff.manban.board.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Board existiert nicht. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BoardNotFoundException extends RuntimeException {

    public BoardNotFoundException() {
        super("Board nicht gefunden");
    }
}
