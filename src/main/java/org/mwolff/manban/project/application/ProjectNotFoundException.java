package org.mwolff.manban.project.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Projekt existiert nicht oder der Benutzer ist kein Mitglied. Bewusst 404 (kein
 * Existenz-Leak): ein Nichtmitglied darf nicht erfahren, ob ein Projekt existiert.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException() {
        super("Projekt nicht gefunden");
    }
}
