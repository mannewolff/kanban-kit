package org.mwolff.manban.auth.infrastructure.mail;

import org.mwolff.manban.auth.application.PasswordResetMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Versendet die Passwort-Reset-E-Mail per SMTP, schaltbar über {@code manban.mail.enabled}.
 * In Dev/Test (Default: aus) wird der Reset-Link nur geloggt.
 */
@Component
class JavaMailPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(JavaMailPasswordResetMailer.class);

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;
    private final String from;

    JavaMailPasswordResetMailer(JavaMailSender mailSender,
                                @Value("${manban.mail.enabled:false}") boolean mailEnabled,
                                @Value("${manban.mail.from:no-reply@manban.local}") String from) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
        this.from = from;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
        if (!mailEnabled) {
            log.info("[DEV] Passwort-Reset-Link für {}: {}", toEmail, resetUrl);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("manban: Passwort zurücksetzen");
        message.setText("Setze dein Passwort über diesen Link zurück:\n\n" + resetUrl + "\n");
        mailSender.send(message);
        log.info("Passwort-Reset-E-Mail an {} versandt", toEmail);
    }
}
