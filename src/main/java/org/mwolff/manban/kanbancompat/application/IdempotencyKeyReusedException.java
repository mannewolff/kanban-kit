package org.mwolff.manban.kanbancompat.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Idempotenz-Schlüssel wurde für einen anderen Befehl vorgelegt als beim ersten Mal (Issue
 * #1001). Die abgelegte Antwort gehört zu jenem Befehl; sie hier auszuliefern, meldete eine
 * Wirkung, die dieser Aufruf nie hatte. Ergibt HTTP 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyKeyReusedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public IdempotencyKeyReusedException(String key, String firstEndpoint) {
    super(
        ("Idempotency-Key '%s' wurde bereits für %s verwendet — für einen neuen Befehl einen neuen"
                + " Schlüssel wählen.")
            .formatted(key, firstEndpoint));
  }
}
