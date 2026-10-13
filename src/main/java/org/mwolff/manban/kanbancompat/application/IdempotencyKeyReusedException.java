package org.mwolff.manban.kanbancompat.application;

import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Ein Idempotenz-Schlüssel wurde für einen anderen Befehl vorgelegt als beim ersten Mal (Issue
 * #1001). Die abgelegte Antwort gehört zu jenem Befehl; sie hier auszuliefern, meldete eine
 * Wirkung, die dieser Aufruf nie hatte. Ergibt HTTP 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyKeyReusedException extends RuntimeException
    implements UebersetzbareMeldung {

  private static final long serialVersionUID = 1L;

  private final String key;
  private final String firstEndpoint;

  public IdempotencyKeyReusedException(String key, String firstEndpoint) {
    super(
        ("Idempotency-Key '%s' wurde bereits für %s verwendet — für einen neuen Befehl einen neuen"
                + " Schlüssel wählen.")
            .formatted(key, firstEndpoint));
    this.key = key;
    this.firstEndpoint = firstEndpoint;
  }

  @Override
  public String schluessel() {
    return "kanbancompat.idempotency-key-wiederverwendet";
  }

  @Override
  public Object[] argumente() {
    return new Object[] {key, firstEndpoint};
  }
}
