package org.mwolff.manban.auth.infrastructure.mail;

import org.mwolff.manban.auth.application.PasswordResetMailer;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.springframework.stereotype.Component;

/**
 * Merkt die Passwort-Reset-E-Mail in der Outbox vor, statt sie synchron zu versenden (Issue #502).
 * Semantik und Schlüsselbildung wie beim {@link OutboxVerificationMailer}: Vormerkung in der
 * fachlichen Transaktion, Zustellung nach Commit über den {@link PasswordResetMailHandler},
 * Schlüssel = Hash der Reset-URL (eindeutig je Token, Geheimnis bleibt draußen).
 */
@Component
class OutboxPasswordResetMailer implements PasswordResetMailer {

  private final OutboxWriter outbox;

  OutboxPasswordResetMailer(OutboxWriter outbox) {
    this.outbox = outbox;
  }

  @Override
  public void sendPasswordResetEmail(String toEmail, String resetUrl) {
    outbox.schedule(
        new OutboxMessage(
            PasswordResetMailHandler.TYPE,
            PasswordResetMailHandler.TYPE + ":" + SecureTokens.sha256Hex(resetUrl),
            PayloadFields.join(toEmail, resetUrl)));
  }
}
