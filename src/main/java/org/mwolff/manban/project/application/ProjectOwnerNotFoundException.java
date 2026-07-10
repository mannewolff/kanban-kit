package org.mwolff.manban.project.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die beim Anlegen eines Projekts angegebene Owner-E-Mail gehört zu keinem Nutzer.
 * Ergibt HTTP 400.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ProjectOwnerNotFoundException extends RuntimeException {

    public ProjectOwnerNotFoundException(String email) {
        super("Kein Nutzer mit E-Mail: " + email);
    }
}
