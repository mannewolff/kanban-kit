package org.mwolff.manban.auth.infrastructure.mail;

import org.mwolff.manban.auth.application.VerificationMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Versendet die Verifikations-E-Mail per SMTP ({@link JavaMailSender}).
 *
 * <p>Der Versand ist über {@code manban.mail.enabled} schaltbar. In Dev/Test
 * (Default: aus) wird der Verifikations-Link nur geloggt, sodass kein SMTP-Server
 * nötig ist — genau der Punkt, der in der Toolbox (#262) blockierte.
 */
@Component
class JavaMailVerificationMailer implements VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(JavaMailVerificationMailer.class);

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String from;

    JavaMailVerificationMailer(JavaMailSender mailSender,
                               @Value("${manban.mail.enabled:false}") boolean mailEnabled,
                               @Value("${manban.mail.from:no-reply@manban.local}") String from) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.from = from;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String verificationUrl) {
        if (!mailEnabled) {
            log.info("[DEV] Verifikations-Link für {}: {}", toEmail, verificationUrl);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("manban: E-Mail bestätigen");
        message.setText("Bitte bestätige deine E-Mail-Adresse über diesen Link:\n\n" + verificationUrl + "\n");
        mailSender.send(message);
        log.info("Verifikations-E-Mail an {} versandt", toEmail);
    }
}
