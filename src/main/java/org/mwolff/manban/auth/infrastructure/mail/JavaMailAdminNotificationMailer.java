package org.mwolff.manban.auth.infrastructure.mail;

import org.mwolff.manban.auth.application.AdminNotificationMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Versendet die Admin-Benachrichtigung „neue Registrierung wartet auf Freigabe" per SMTP, schaltbar
 * über {@code manban.mail.enabled}. In Dev/Test (Default: aus) wird nur geloggt.
 */
@Component
class JavaMailAdminNotificationMailer implements AdminNotificationMailer {

  private static final Logger log = LoggerFactory.getLogger(JavaMailAdminNotificationMailer.class);

  private final JavaMailSender mailSender;
  private final boolean mailEnabled;
  private final String from;

  JavaMailAdminNotificationMailer(
      JavaMailSender mailSender,
      @Value("${manban.mail.enabled:false}") boolean mailEnabled,
      @Value("${manban.mail.from:no-reply@manban.local}") String from) {
    this.mailSender = mailSender;
    this.mailEnabled = mailEnabled;
    this.from = from;
  }

  @Override
  public void sendNewUserPendingApproval(
      String adminEmail, String newUserEmail, String newUserDisplayName) {
    if (!mailEnabled) {
      log.info(
          "[DEV] Neue Registrierung wartet auf Freigabe: {} ({}) — Admin: {}",
          newUserEmail,
          newUserDisplayName,
          adminEmail);
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(adminEmail);
    message.setSubject("manban: Neue Registrierung wartet auf Freigabe");
    message.setText(
        "Neu registriert und verifiziert: "
            + newUserDisplayName
            + " ("
            + newUserEmail
            + ").\n\nBitte in der Nutzerverwaltung freigeben.\n");
    mailSender.send(message);
    log.info("Freigabe-Benachrichtigung an {} versandt", adminEmail);
  }
}
