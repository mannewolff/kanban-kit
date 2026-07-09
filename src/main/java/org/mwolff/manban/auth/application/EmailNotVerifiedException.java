package org.mwolff.manban.auth.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Login abgelehnt, weil die E-Mail-Adresse noch nicht bestätigt wurde. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("E-Mail-Adresse ist noch nicht bestätigt");
    }
}
