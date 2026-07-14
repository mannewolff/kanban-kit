package org.mwolff.manban.project.infrastructure.mail;

import org.mwolff.manban.project.application.InvitationMailer;
import org.mwolff.manban.project.domain.ProjectRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Versendet die Einladungs-E-Mail per SMTP, schaltbar über {@code manban.mail.enabled}. In Dev/Test
 * (Default: aus) wird der Einladungs-Link nur geloggt.
 */
@Component
class JavaMailInvitationMailer implements InvitationMailer {

  private static final Logger log = LoggerFactory.getLogger(JavaMailInvitationMailer.class);

  private final JavaMailSender mailSender;
  private final boolean mailEnabled;
  private final String from;
  private final String productName;

  JavaMailInvitationMailer(
      JavaMailSender mailSender,
      @Value("${manban.mail.enabled:false}") boolean mailEnabled,
      @Value("${manban.mail.from:no-reply@kanban-kit.local}") String from,
      @Value("${manban.mail.product-name:kanban-kit}") String productName) {
    this.mailSender = mailSender;
    this.mailEnabled = mailEnabled;
    this.from = from;
    this.productName = productName;
  }

  @Override
  public void sendInvitationEmail(String toEmail, String projectName, String invitationUrl) {
    if (!mailEnabled) {
      log.info("[DEV] Einladung ins Projekt '{}' für {}: {}", projectName, toEmail, invitationUrl);
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(toEmail);
    message.setSubject(productName + ": Einladung ins Projekt " + projectName);
    message.setText(
        "Du wurdest ins Projekt '"
            + projectName
            + "' eingeladen.\n\n"
            + "So nimmst du die Einladung an:\n"
            + "1. Registriere dich mit genau dieser E-Mail-Adresse (der, an die diese Nachricht"
            + " ging).\n"
            + "2. Bestätige deine E-Mail über den Link aus der Bestätigungs-Mail.\n"
            + "3. Öffne anschließend diesen Link, um die Einladung anzunehmen:\n\n"
            + invitationUrl
            + "\n\n"
            + "Hast du bereits ein Konto mit dieser E-Mail-Adresse, genügt Schritt 3.\n");
    mailSender.send(message);
    log.info("Einladungs-E-Mail an {} versandt", toEmail);
  }

  @Override
  public void sendProjectAssignedEmail(
      String toEmail, String projectName, ProjectRole role, String projectUrl) {
    if (!mailEnabled) {
      log.info(
          "[DEV] Zuordnung zum Projekt '{}' als {} für {}: {}",
          projectName,
          role,
          toEmail,
          projectUrl);
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(toEmail);
    message.setSubject(productName + ": Du wurdest dem Projekt " + projectName + " hinzugefügt");
    message.setText(
        "Du wurdest dem Projekt '"
            + projectName
            + "' als "
            + role
            + " hinzugefügt. Zum Projekt:\n\n"
            + projectUrl
            + "\n");
    mailSender.send(message);
    log.info("Zuordnungs-E-Mail an {} versandt", toEmail);
  }
}
