package org.mwolff.manban.project.infrastructure.mail;

import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.Texte;
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
 *
 * <p>Seit Issue #502 die Zustell-Komponente hinter {@link InvitationMailHandler} und {@link
 * ProjectAssignedMailHandler}: Der Aufruf kommt nach dem Commit aus dem Outbox-Worker.
 *
 * <p>Betreff und Text in der eingestellten Sprache, bestimmt beim Versand (Issue #1643, Plan #1638
 * A6) — die Outbox-Payload trägt keine Sprache.
 */
@Component
class JavaMailInvitationMailer {

  private static final Logger log = LoggerFactory.getLogger(JavaMailInvitationMailer.class);

  private final JavaMailSender mailSender;
  private final boolean mailEnabled;
  private final String from;
  private final String productName;
  private final Texte texte;

  JavaMailInvitationMailer(
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

  void sendInvitationEmail(String toEmail, String projectName, String invitationUrl) {
    if (!mailEnabled) {
      log.info("[DEV] Einladung ins Projekt '{}' für {}: {}", projectName, toEmail, invitationUrl);
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(toEmail);
    Sprache sprache = texte.eingestellte();
    message.setSubject(texte.text(sprache, "mail.einladung.betreff", productName, projectName));
    message.setText(texte.text(sprache, "mail.einladung.text", projectName, invitationUrl));
    mailSender.send(message);
    log.info("Einladungs-E-Mail an {} versandt", toEmail);
  }

  void sendProjectAssignedEmail(
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
    Sprache sprache = texte.eingestellte();
    message.setSubject(texte.text(sprache, "mail.zuordnung.betreff", productName, projectName));
    message.setText(
        texte.text(sprache, "mail.zuordnung.text", projectName, role.name(), projectUrl));
    mailSender.send(message);
    log.info("Zuordnungs-E-Mail an {} versandt", toEmail);
  }
}
