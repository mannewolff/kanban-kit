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
 * Versendet die Passwort-Reset-E-Mail per SMTP, schaltbar über {@code manban.mail.enabled}. In
 * Dev/Test (Default: aus) wird der Reset-Link nur geloggt.
 *
 * <p>Seit Issue #502 die Zustell-Komponente hinter dem {@link PasswordResetMailHandler}: Der Aufruf
 * kommt nach dem Commit aus dem Outbox-Worker.
 *
 * <p>Betreff und Text in der eingestellten Sprache, bestimmt beim Versand (Issue #1643, Plan #1638
 * A6) — die Outbox-Payload trägt keine Sprache.
 */
@Component
class JavaMailPasswordResetMailer {

  private static final Logger log = LoggerFactory.getLogger(JavaMailPasswordResetMailer.class);

  private final JavaMailSender mailSender;
  private final boolean mailEnabled;
  private final String from;
  private final String productName;
  private final Texte texte;

  JavaMailPasswordResetMailer(
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

  void sendPasswordResetEmail(String toEmail, String resetUrl) {
    if (!mailEnabled) {
      log.info("[DEV] Passwort-Reset-Link für {}: {}", toEmail, resetUrl);
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(toEmail);
    Sprache sprache = texte.eingestellte();
    message.setSubject(texte.text(sprache, "mail.passwort-reset.betreff", productName));
    message.setText(texte.text(sprache, "mail.passwort-reset.text", resetUrl));
    mailSender.send(message);
    log.info("Passwort-Reset-E-Mail an {} versandt", toEmail);
  }
}
