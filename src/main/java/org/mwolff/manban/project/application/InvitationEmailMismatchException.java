package org.mwolff.manban.project.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Die annehmende Person ist nicht die eingeladene E-Mail-Adresse. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class InvitationEmailMismatchException extends RuntimeException {

    public InvitationEmailMismatchException() {
        super("Einladung gehört zu einer anderen E-Mail-Adresse");
    }
}
