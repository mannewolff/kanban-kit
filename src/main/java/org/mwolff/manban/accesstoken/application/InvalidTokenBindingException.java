package org.mwolff.manban.accesstoken.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die beim Anlegen eines Tokens angegebene Projekt-/Board-Bindung ist unschlüssig:
 * Board unbekannt, Board gehört nicht zum angegebenen Projekt, oder nur eines von
 * beiden ({@code projectId}/{@code boardId}) gesetzt. Ergibt HTTP 400.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTokenBindingException extends RuntimeException {

    public InvalidTokenBindingException(String message) {
        super(message);
    }
}
