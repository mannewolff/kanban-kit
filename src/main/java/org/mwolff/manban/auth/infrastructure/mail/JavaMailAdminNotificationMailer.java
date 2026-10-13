package org.mwolff.manban.auth.infrastructure.mail;

import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.Texte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Versendet die Admin-Benachrichtigung „neue Registrierung wartet auf Freigabe" per SMTP, schaltbar
 * über {@code manban.mail.enabled}. In Dev/Test (Default: aus) wird nur geloggt.
 *
 * <p>Seit Issue #502 die Zustell-Komponente hinter dem {@link AdminNotificationMailHandler}: Der
 * Aufruf kommt nach dem Commit aus dem Outbox-Worker.
 *
 * <p>Betreff und Text in der eingestellten Sprache, bestimmt beim Versand (Issue #1643, Plan #1638
 * A6) — die Outbox-Payload trägt keine Sprache.
 */
@Component
class JavaMailAdminNotificationMailer {

  private static final Logger log = LoggerFactory.getLogger(JavaMailAdminNotificationMailer.class);

  private final JavaMailSender mailSender;
  private final boolean mailEnabled;
  private final String from;
  private final String productName;
  private final Texte texte;

  JavaMailAdminNotificationMailer(
      JavaMailSender mailSender,
      @Value("${manban.mail.enabled:false}") boolean mailEnabled,
      @Value("${manban.mail.from:no-reply@kanban-kit.local}") String from,
      @Value("${manban.mail.product-name:kanban-kit}") String productName,
      Texte texte) {
    this.mailSender = mailSender;
    this.mailEnabled = mailEnabled;
    this.from = from;
    this.productName = productName;
    this.texte = texte;
  }

  void sendNewUserPendingApproval(
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
    Sprache sprache = texte.eingestellte();
    message.setSubject(texte.text(sprache, "mail.freigabe.betreff", productName));
    message.setText(texte.text(sprache, "mail.freigabe.text", newUserDisplayName, newUserEmail));
    mailSender.send(message);
    log.info("Freigabe-Benachrichtigung an {} versandt", adminEmail);
  }
}
