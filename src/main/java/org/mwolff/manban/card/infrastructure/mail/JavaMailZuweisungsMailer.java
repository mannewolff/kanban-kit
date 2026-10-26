package org.mwolff.manban.card.infrastructure.mail;

import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.Texte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Versendet die Nachricht an neu Zuständige per SMTP (Issue #1916), schaltbar über {@code
 * manban.mail.enabled}. In Dev/Test (Default: aus) werden nur Empfänger und Link geloggt.
 *
 * <p>Betreff und Text in der eingestellten Sprache der Installation, bestimmt beim Versand — wie
 * {@code JavaMailInvitationMailer}. Der Kartentext steht nie darin.
 */
@Component
class JavaMailZuweisungsMailer {

  private static final Logger log = LoggerFactory.getLogger(JavaMailZuweisungsMailer.class);

  private final JavaMailSender mailSender;
  private final boolean mailEnabled;
  private final String from;
  private final String productName;
  private final Texte texte;

  JavaMailZuweisungsMailer(
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

  void sendZuweisungsEmail(Zuweisung z) {
    if (!mailEnabled) {
      log.info("[DEV] Karte #{} ist {} zugewiesen: {}", z.nummer(), z.toEmail(), z.link());
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(z.toEmail());
    Sprache sprache = texte.eingestellte();
    message.setSubject(texte.text(sprache, "mail.zuweisung.betreff", productName, z.nummer()));
    message.setText(
        texte.text(
            sprache,
            "mail.zuweisung.text",
            z.nummer(),
            z.titel(),
            z.projekt(),
            z.board(),
            z.von(),
            z.link()));
    mailSender.send(message);
    log.info("Zuweisungs-E-Mail an {} versandt", z.toEmail());
  }

  /** Inhalt einer Nachricht — bewusst ohne Kartentext. */
  record Zuweisung(
      String toEmail,
      String nummer,
      String titel,
      String projekt,
      String board,
      String von,
      String link) {}
}
