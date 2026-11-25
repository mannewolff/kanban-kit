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
 * Versendet die Mail mit Anmeldecode und Link per SMTP, schaltbar über {@code manban.mail.enabled}
 * (Plan #1801, A12). Die Zustell-Komponente hinter dem {@link LoginCodeMailHandler}.
 *
 * <p><strong>Protokollregel (fachliches AK 9):</strong> Ist der Mailversand aus, stehen Code und
 * Link im Protokoll — sonst käme niemand an seinen Code. Ist er an, steht dort nur, dass die Mail
 * hinausging, nie der Code.
 *
 * <p><strong>Nur im Entwicklungsbetrieb (Issue #2267):</strong> Code und Link landen nur dann im
 * Protokoll, wenn zusätzlich {@code manban.dev-mode=true} gesetzt ist. Sonst könnte jeder mit
 * Lesezugriff auf die Protokolle sich ohne Zugriff auf das Postfach an einem fremden Konto
 * anmelden. Ohne Entwicklungsbetrieb ist ein abgeschalteter Mailversand ein Konfigurationsfehler:
 * Das Protokoll nennt nur die Adresse, und der Versuch scheitert, damit die Outbox ihn als
 * Fehlversuch verbucht.
 *
 * <p>Betreff und Text in der eingestellten Sprache, bestimmt beim Versand wie bei den übrigen Mails
 * (Issue #1643) — die Outbox-Payload trägt keine Sprache.
 */
@Component
class JavaMailLoginCodeMailer {

  private static final Logger log = LoggerFactory.getLogger(JavaMailLoginCodeMailer.class);

  private final JavaMailSender mailSender;
  private final boolean mailEnabled;
  private final boolean devMode;
  private final String from;
  private final String productName;
  private final Texte texte;

  JavaMailLoginCodeMailer(
      JavaMailSender mailSender,
      @Value("${manban.mail.enabled:false}") boolean mailEnabled,
      @Value("${manban.dev-mode:false}") boolean devMode,
      @Value("${manban.mail.from:no-reply@kanban-kit.local}") String from,
      @Value("${manban.mail.product-name:kanban-kit}") String productName,
      Texte texte) {
    this.mailSender = mailSender;
    this.mailEnabled = mailEnabled;
    this.devMode = devMode;
    this.from = from;
    this.productName = productName;
    this.texte = texte;
  }

  void sendLoginCode(String toEmail, String code, String linkUrl) {
    if (!mailEnabled) {
      if (!devMode) {
        log.error(
            "Anmeldecode für {} nicht zugestellt: Mailversand ist aus und kein Entwicklungsbetrieb."
                + " manban.mail.enabled=true setzen.",
            toEmail);
        throw new IllegalStateException(
            "Mailversand ist aus (manban.mail.enabled=false) außerhalb des Entwicklungsbetriebs"
                + " (manban.dev-mode=false): Anmeldecode nicht zugestellt.");
      }
      log.info("[DEV] Anmeldecode für {}: {} — Link: {}", toEmail, code, linkUrl);
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(toEmail);
    Sprache sprache = texte.eingestellte();
    message.setSubject(texte.text(sprache, "mail.anmeldecode.betreff", productName, code));
    message.setText(texte.text(sprache, "mail.anmeldecode.text", code, linkUrl));
    mailSender.send(message);
    log.info("Anmeldecode-Mail an {} versandt", toEmail);
  }
}
