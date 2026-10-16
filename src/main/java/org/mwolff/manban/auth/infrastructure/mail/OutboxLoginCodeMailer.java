package org.mwolff.manban.auth.infrastructure.mail;

import org.mwolff.manban.auth.application.LoginCodeMailer;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.SecureTokens;
import org.mwolff.manban.outbox.application.OutboxMessage;
import org.mwolff.manban.outbox.application.OutboxWriter;
import org.springframework.stereotype.Component;

/**
 * Merkt die Anmeldecode-Mail in der Outbox vor (Plan #1801, A12). Semantik wie beim {@link
 * OutboxPasswordResetMailer}: Vormerkung in der fachlichen Transaktion, Zustellung nach Commit über
 * den {@link LoginCodeMailHandler}, Schlüssel = Hash der Link-URL (eindeutig je Anforderung, das
 * Geheimnis bleibt draußen). Code und Link stehen bis zur Outbox-Aufbewahrung im Klartext in der
 * Nutzlast (Plan #1801, E7).
 */
@Component
class OutboxLoginCodeMailer implements LoginCodeMailer {

  private final OutboxWriter outbox;

  OutboxLoginCodeMailer(OutboxWriter outbox) {
    this.outbox = outbox;
  }

  @Override
  public void sendLoginCode(String toEmail, String code, String linkUrl) {
    outbox.schedule(
        new OutboxMessage(
            LoginCodeMailHandler.TYPE,
            LoginCodeMailHandler.TYPE + ":" + SecureTokens.sha256Hex(linkUrl),
            PayloadFields.join(toEmail, code, linkUrl)));
  }
}
