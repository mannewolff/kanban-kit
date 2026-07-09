package org.mwolff.manban.accesstoken.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Token existiert nicht oder gehört nicht dem anfragenden Benutzer. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AccessTokenNotFoundException extends RuntimeException {

    public AccessTokenNotFoundException() {
        super("Zugriffstoken nicht gefunden");
    }
}
