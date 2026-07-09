package org.mwolff.manban.comment.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Kommentar existiert nicht. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CommentNotFoundException extends RuntimeException {

    public CommentNotFoundException() {
        super("Kommentar nicht gefunden");
    }
}
