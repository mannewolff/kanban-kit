package org.mwolff.manban.auth.infrastructure.mail;

import org.mwolff.manban.auth.application.VerificationMailer;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.springframework.stereotype.Component;

/**
 * Merkt die Verifikations-E-Mail in der Outbox vor, statt sie synchron zu versenden (Issue #502).
 *
 * <p>Der Aufruf läuft innerhalb der fachlichen Transaktion (die Outbox verlangt das per {@code
 * MANDATORY}): Rollt die Registrierung zurück, verschwindet die Vormerkung mit — niemand bekommt
 * eine Mail zu einem Benutzer, den es nicht gibt. Versandt wird nach dem Commit vom Outbox-Worker
 * über den {@link VerificationMailHandler}.
 *
 * <p>Der Idempotenzschlüssel ist der Hash der Verifikations-URL: eindeutig je Token (jede
 * Registrierung erzeugt ein frisches), ohne das geheime Token selbst in den Schlüssel zu legen.
 */
@Component
class OutboxVerificationMailer implements VerificationMailer {

  private final OutboxWriter outbox;

  OutboxVerificationMailer(OutboxWriter outbox) {
    this.outbox = outbox;
  }

  @Override
  public void sendVerificationEmail(String toEmail, String verificationUrl) {
    outbox.schedule(
        new OutboxMessage(
            VerificationMailHandler.TYPE,
            VerificationMailHandler.TYPE + ":" + SecureTokens.sha256Hex(verificationUrl),
            PayloadFields.join(toEmail, verificationUrl)));
  }
}
