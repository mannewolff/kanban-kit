package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Aufrufer ist kein Plattform-Admin. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AdminAccessDeniedException extends RuntimeException {

    public AdminAccessDeniedException() {
        super("Plattform-Admin-Recht erforderlich");
    }
}
