package org.mwolff.manban.project.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Einladung ist unbekannt, bereits angenommen oder abgelaufen. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidInvitationException extends RuntimeException {

    public InvalidInvitationException() {
        super("Einladung ist ungültig oder abgelaufen");
    }
}
