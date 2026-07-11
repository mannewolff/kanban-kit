package org.mwolff.manban.auth.application;

/** Ausgehender Port für den Versand der Passwort-Reset-E-Mail. */
@FunctionalInterface
public interface PasswordResetMailer {

  void sendPasswordResetEmail(String toEmail, String resetUrl);
}
