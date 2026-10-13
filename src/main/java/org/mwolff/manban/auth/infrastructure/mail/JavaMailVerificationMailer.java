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
 * Versendet die Verifikations-E-Mail per SMTP ({@link JavaMailSender}).
 *
 * <p>Seit Issue #502 nicht mehr die Port-Implementierung, sondern die Zustell-Komponente hinter dem
 * {@link VerificationMailHandler}: Der Aufruf kommt nach dem Commit aus dem Outbox-Worker.
 *
 * <p>Der Versand ist über {@code manban.mail.enabled} schaltbar. In Dev/Test (Default: aus) wird
 * der Verifikations-Link nur geloggt, sodass kein SMTP-Server nötig ist — genau der Punkt, der in
 * der Toolbox (#262) blockierte.
 *
 * <p>Betreff und Text in der eingestellten Sprache, bestimmt beim Versand (Issue #1643, Plan #1638
 * A6) — die Outbox-Payload trägt keine Sprache.
 */
@Component
class JavaMailVerificationMailer {

  private static final Logger log = LoggerFactory.getLogger(JavaMailVerificationMailer.class);

  private final JavaMailSender mailSender;
  private final boolean mailEnabled;
  private final String from;
  private final String productName;
  private final Texte texte;

  JavaMailVerificationMailer(
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

  void sendVerificationEmail(String toEmail, String verificationUrl) {
    if (!mailEnabled) {
      log.info("[DEV] Verifikations-Link für {}: {}", toEmail, verificationUrl);
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(toEmail);
    Sprache sprache = texte.eingestellte();
    message.setSubject(texte.text(sprache, "mail.verifikation.betreff", productName));
    message.setText(texte.text(sprache, "mail.verifikation.text", verificationUrl));
    mailSender.send(message);
    log.info("Verifikations-E-Mail an {} versandt", toEmail);
  }
}
