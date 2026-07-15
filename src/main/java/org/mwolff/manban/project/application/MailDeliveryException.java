package org.mwolff.manban.project.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Einladung konnte nicht zugestellt werden, weil der E-Mail-Versand fehlschlug. Führt zu einem
 * aussagekräftigen 502 statt eines generischen 500 (der Einladungs-Link ist ohne Mail wertlos,
 * daher bleibt keine verwaiste Invitation zurück — die Transaktion rollt zurück).
 */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class MailDeliveryException extends RuntimeException {

  public MailDeliveryException(Throwable cause) {
    super("E-Mail konnte nicht versandt werden. Bitte später erneut versuchen.", cause);
  }
}
